import cors from "cors";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import express from "express";

const PORT = Number(process.env.PORT || 8090);
const DATA_DIR = process.env.DATA_DIR || path.join(process.cwd(), "data");
const STORE_FILE = path.join(DATA_DIR, "store.json");
const SESSION_TTL_MS = 1000 * 60 * 60 * 24 * 30;
const ALLOWED_ORIGIN = process.env.ALLOWED_ORIGIN || "*";

const defaultStore = () => ({
  users: [],
  sessions: [],
  tripSnapshots: [],
  passwordResetRequests: []
});

function ensureDataDir() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

function loadStore() {
  ensureDataDir();
  if (!fs.existsSync(STORE_FILE)) {
    const initial = defaultStore();
    fs.writeFileSync(STORE_FILE, JSON.stringify(initial, null, 2));
    return initial;
  }
  try {
    const raw = fs.readFileSync(STORE_FILE, "utf8");
    return { ...defaultStore(), ...JSON.parse(raw) };
  } catch (error) {
    console.error("Failed to load store:", error);
    return defaultStore();
  }
}

let store = loadStore();
let saveChain = Promise.resolve();

function persistStore() {
  saveChain = saveChain.then(async () => {
    ensureDataDir();
    const tempFile = `${STORE_FILE}.tmp`;
    await fs.promises.writeFile(tempFile, JSON.stringify(store, null, 2));
    await fs.promises.rename(tempFile, STORE_FILE);
  });
  return saveChain;
}

function now() {
  return Date.now();
}

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

function createPasswordHash(password, salt = crypto.randomBytes(16).toString("hex")) {
  const passwordHash = crypto.scryptSync(password, salt, 64).toString("hex");
  return { passwordHash, passwordSalt: salt };
}

function verifyPassword(password, user) {
  const hash = crypto.scryptSync(password, user.passwordSalt, 64).toString("hex");
  return crypto.timingSafeEqual(
    Buffer.from(hash, "hex"),
    Buffer.from(user.passwordHash, "hex")
  );
}

function sanitizeUser(user) {
  return {
    id: user.id,
    email: user.email,
    displayName: user.displayName,
    avatarUrl: user.avatarUrl ?? null,
    phoneNumber: user.phoneNumber ?? null,
    preferredCurrency: user.preferredCurrency ?? "RUB",
    createdAt: user.createdAt,
    lastLoginAt: user.lastLoginAt
  };
}

function createSession(userId) {
  const token = crypto.randomUUID();
  const session = {
    token,
    userId,
    createdAt: now(),
    expiresAt: now() + SESSION_TTL_MS
  };
  store.sessions = store.sessions.filter((item) => item.expiresAt > now());
  store.sessions.push(session);
  return token;
}

function readBearerToken(headerValue) {
  const raw = String(headerValue || "");
  if (!raw.startsWith("Bearer ")) return null;
  return raw.substring("Bearer ".length).trim();
}

function parseRelayPayload(payloadJson) {
  const parsed = JSON.parse(payloadJson);
  const tripId = parsed?.trip?.id;
  if (!tripId) {
    throw new Error("Payload does not contain trip.id");
  }

  const participants = Array.isArray(parsed.participants)
    ? parsed.participants.map((participant) => ({
        userId: participant.userId,
        displayName: participant.displayName ?? "",
        email: participant.email ?? null,
        role: participant.role ?? "MEMBER",
        updatedAt: Number(participant.updatedAt || 0)
      }))
    : [];

  const sourceUpdatedAt = Math.max(
    Number(parsed.trip?.updatedAt || 0),
    ...participants.map((item) => Number(item.updatedAt || 0)),
    ...safeArray(parsed.tripDays).map((item) => Number(item.updatedAt || 0)),
    ...safeArray(parsed.places).map((item) => Number(item.updatedAt || 0)),
    ...safeArray(parsed.expenses).map((item) => Number(item.updatedAt || 0)),
    ...safeArray(parsed.deletions).map((item) => Number(item.deletedAt || 0))
  );

  return {
    parsed,
    tripId,
    ownerId: parsed.trip?.ownerId ?? null,
    inviteCode: parsed.trip?.inviteCode ?? null,
    participants,
    sourceUpdatedAt
  };
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function sortById(items, idKey = "id") {
  return safeArray(items)
    .map((item) => JSON.parse(JSON.stringify(item)))
    .sort((left, right) => String(left[idKey] || "").localeCompare(String(right[idKey] || "")));
}

function normalizeParticipants(items) {
  return safeArray(items)
    .map((item) => ({
      userId: item.userId,
      role: item.role,
      email: item.email ?? null,
      displayName: item.displayName ?? ""
    }))
    .sort((left, right) => left.userId.localeCompare(right.userId));
}

function normalizeNonExpenseDeletions(items) {
  return safeArray(items)
    .filter((item) => item.entityType !== "EXPENSE")
    .map((item) => ({
      id: item.id,
      entityType: item.entityType,
      entityId: item.entityId,
      deletedAt: item.deletedAt
    }))
    .sort((left, right) => left.id.localeCompare(right.id));
}

function memberCanPush(existingJson, incomingJson) {
  const existing = JSON.parse(existingJson);
  const incoming = JSON.parse(incomingJson);

  const existingComparable = {
    trip: existing.trip,
    participants: normalizeParticipants(existing.participants),
    tripDays: sortById(existing.tripDays),
    places: sortById(existing.places),
    deletions: normalizeNonExpenseDeletions(existing.deletions)
  };

  const incomingComparable = {
    trip: incoming.trip,
    participants: normalizeParticipants(incoming.participants),
    tripDays: sortById(incoming.tripDays),
    places: sortById(incoming.places),
    deletions: normalizeNonExpenseDeletions(incoming.deletions)
  };

  return JSON.stringify(existingComparable) === JSON.stringify(incomingComparable);
}

function upsertTripSnapshot(payloadJson, updatedByUserId) {
  const meta = parseRelayPayload(payloadJson);
  const snapshot = {
    tripId: meta.tripId,
    ownerId: meta.ownerId,
    inviteCode: meta.inviteCode,
    payloadJson,
    sourceUpdatedAt: meta.sourceUpdatedAt,
    serverUpdatedAt: now(),
    updatedByUserId,
    members: meta.participants
  };
  const existingIndex = store.tripSnapshots.findIndex((item) => item.tripId === meta.tripId);
  if (existingIndex >= 0) {
    store.tripSnapshots[existingIndex] = snapshot;
  } else {
    store.tripSnapshots.push(snapshot);
  }
  return snapshot;
}

function persistParsedSnapshot(snapshot, parsed, updatedByUserId = snapshot.updatedByUserId) {
  const serialized = JSON.stringify(parsed);
  const meta = parseRelayPayload(serialized);
  snapshot.ownerId = meta.ownerId;
  snapshot.inviteCode = meta.inviteCode;
  snapshot.payloadJson = serialized;
  snapshot.sourceUpdatedAt = meta.sourceUpdatedAt;
  snapshot.serverUpdatedAt = now();
  snapshot.updatedByUserId = updatedByUserId;
  snapshot.members = meta.participants;
  return snapshot;
}

function findSnapshotByInviteCode(inviteCode) {
  return store.tripSnapshots.find((snapshot) => {
    if (snapshot.inviteCode === inviteCode) return true;
    try {
      return parseRelayPayload(snapshot.payloadJson).inviteCode === inviteCode;
    } catch (_error) {
      return false;
    }
  });
}

function replaceParticipantProfileInSnapshot(snapshot, user) {
  const parsed = JSON.parse(snapshot.payloadJson);
  let changed = false;
  parsed.participants = safeArray(parsed.participants).map((participant) => {
    if (participant.userId !== user.id) return participant;
    changed = true;
    return {
      ...participant,
      displayName: user.displayName,
      email: user.email,
      updatedAt: now()
    };
  });
  return changed ? persistParsedSnapshot(snapshot, parsed, user.id) : snapshot;
}

function removeParticipantFromSnapshot(snapshot, userId) {
  const parsed = JSON.parse(snapshot.payloadJson);
  const beforeCount = safeArray(parsed.participants).length;
  parsed.participants = safeArray(parsed.participants).filter((participant) => participant.userId !== userId);
  if (safeArray(parsed.participants).length === beforeCount) {
    return snapshot;
  }
  return persistParsedSnapshot(snapshot, parsed, snapshot.updatedByUserId);
}

function addParticipantToSnapshot(snapshot, participant, updatedByUserId) {
  const parsed = JSON.parse(snapshot.payloadJson);
  const participants = safeArray(parsed.participants);
  const existingIndex = participants.findIndex((item) => item.userId === participant.userId);
  if (existingIndex >= 0) {
    participants[existingIndex] = {
      ...participants[existingIndex],
      displayName: participant.displayName,
      email: participant.email ?? participants[existingIndex].email ?? null,
      updatedAt: now()
    };
  } else {
    participants.push({
      ...participant,
      avatarUrl: null,
      shareLocation: true,
      lastLatitude: null,
      lastLongitude: null,
      lastLocationUpdate: null,
      joinedAt: now(),
      isOnline: false,
      createdAt: now(),
      updatedAt: now()
    });
  }
  parsed.participants = participants;
  parsed.trip = {
    ...parsed.trip,
    isGroupTrip: true,
    updatedAt: now()
  };
  return persistParsedSnapshot(snapshot, parsed, updatedByUserId);
}

function findSnapshotRole(snapshot, userId) {
  return snapshot.members.find((item) => item.userId === userId)?.role ?? null;
}

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email || "").trim());
}

const app = express();
app.use(cors({ origin: ALLOWED_ORIGIN }));
app.use(express.json({ limit: "5mb" }));

async function requireAuth(req, res, next) {
  const token = readBearerToken(req.headers.authorization);
  if (!token) {
    res.status(401).json({ message: "Missing bearer token" });
    return;
  }

  store.sessions = store.sessions.filter((session) => session.expiresAt > now());
  const session = store.sessions.find((item) => item.token === token);
  if (!session) {
    await persistStore();
    res.status(401).json({ message: "Session expired" });
    return;
  }

  const user = store.users.find((item) => item.id === session.userId);
  if (!user) {
    store.sessions = store.sessions.filter((item) => item.token !== token);
    await persistStore();
    res.status(401).json({ message: "User not found" });
    return;
  }

  req.authToken = token;
  req.user = user;
  next();
}

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    time: now(),
    users: store.users.length,
    tripSnapshots: store.tripSnapshots.length
  });
});

app.post("/api/v1/auth/sign-up", async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const password = String(req.body?.password || "");
  const displayName = String(req.body?.displayName || "").trim();

  if (!isValidEmail(email)) {
    res.status(400).json({ code: "INVALID_EMAIL", message: "Неверный формат email" });
    return;
  }
  if (password.length < 6) {
    res.status(400).json({ code: "WEAK_PASSWORD", message: "Пароль должен быть не короче 6 символов" });
    return;
  }
  if (!displayName) {
    res.status(400).json({ code: "INVALID_NAME", message: "Укажите имя пользователя" });
    return;
  }
  if (store.users.some((item) => item.email === email)) {
    res.status(409).json({ code: "EMAIL_ALREADY_IN_USE", message: "Этот email уже используется" });
    return;
  }

  const credentials = createPasswordHash(password);
  const user = {
    id: crypto.randomUUID(),
    email,
    displayName,
    avatarUrl: null,
    phoneNumber: null,
    preferredCurrency: "RUB",
    createdAt: now(),
    lastLoginAt: now(),
    ...credentials
  };

  store.users.push(user);
  const token = createSession(user.id);
  await persistStore();
  res.status(201).json({ token, user: sanitizeUser(user) });
});

app.post("/api/v1/auth/sign-in", async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const password = String(req.body?.password || "");
  const user = store.users.find((item) => item.email === email);

  if (!user) {
    res.status(404).json({ code: "USER_NOT_FOUND", message: "Пользователь не найден" });
    return;
  }
  if (!verifyPassword(password, user)) {
    res.status(401).json({ code: "WRONG_PASSWORD", message: "Неверный пароль" });
    return;
  }

  user.lastLoginAt = now();
  const token = createSession(user.id);
  await persistStore();
  res.json({ token, user: sanitizeUser(user) });
});

app.post("/api/v1/auth/sign-out", requireAuth, async (req, res) => {
  store.sessions = store.sessions.filter((item) => item.token !== req.authToken);
  await persistStore();
  res.json({ ok: true });
});

app.post("/api/v1/auth/password-reset", async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  if (!isValidEmail(email)) {
    res.status(400).json({ code: "INVALID_EMAIL", message: "Неверный формат email" });
    return;
  }

  store.passwordResetRequests.push({
    email,
    createdAt: now()
  });
  await persistStore();
  res.json({ ok: true });
});

app.get("/api/v1/auth/me", requireAuth, (req, res) => {
  res.json({ user: sanitizeUser(req.user) });
});

app.patch("/api/v1/auth/profile", requireAuth, async (req, res) => {
  const displayName = String(req.body?.displayName || "").trim();
  const avatarUrl = req.body?.avatarUrl ? String(req.body.avatarUrl).trim() : null;
  if (displayName) {
    req.user.displayName = displayName;
  }
  req.user.avatarUrl = avatarUrl || null;

  store.tripSnapshots.forEach((snapshot) => {
    replaceParticipantProfileInSnapshot(snapshot, req.user);
  });

  await persistStore();
  res.json({ user: sanitizeUser(req.user) });
});

app.delete("/api/v1/auth/account", requireAuth, async (req, res) => {
  const ownsTrips = store.tripSnapshots.some((snapshot) => snapshot.ownerId === req.user.id);
  if (ownsTrips) {
    res.status(409).json({
      code: "ACCOUNT_OWNS_TRIPS",
      message: "Нельзя удалить аккаунт, пока за вами закреплены поездки как за владельцем"
    });
    return;
  }

  store.users = store.users.filter((item) => item.id !== req.user.id);
  store.sessions = store.sessions.filter((item) => item.userId !== req.user.id);
  store.tripSnapshots = store.tripSnapshots
    .map((snapshot) => removeParticipantFromSnapshot(snapshot, req.user.id))
    .filter((snapshot) => snapshot.members.length > 0);

  await persistStore();
  res.json({ ok: true });
});

app.post("/api/v1/trips/join-by-invite", requireAuth, async (req, res) => {
  const inviteCode = String(req.body?.inviteCode || "").trim().toUpperCase();
  const displayName = String(req.body?.displayName || "").trim() || req.user.displayName;

  if (!inviteCode) {
    res.status(400).json({ code: "INVALID_INVITE_CODE", message: "Укажите код приглашения" });
    return;
  }

  const snapshot = findSnapshotByInviteCode(inviteCode);
  if (!snapshot) {
    res.status(404).json({ code: "TRIP_NOT_FOUND", message: "Поездка по коду не найдена" });
    return;
  }

  addParticipantToSnapshot(
    snapshot,
    {
      tripId: snapshot.tripId,
      userId: req.user.id,
      displayName,
      email: req.user.email,
      role: "MEMBER"
    },
    req.user.id
  );

  await persistStore();
  res.json({
    tripId: snapshot.tripId,
    serverUpdatedAt: snapshot.serverUpdatedAt
  });
});

app.post("/api/v1/sync/push", requireAuth, async (req, res) => {
  const items = Array.isArray(req.body?.items) ? req.body.items : [];
  const applied = [];
  const rejected = [];

  for (const item of items) {
    const tripId = String(item?.tripId || "");
    const payloadJson = String(item?.payloadJson || "");

    if (!tripId || !payloadJson) {
      rejected.push({ tripId: tripId || null, message: "Missing tripId or payloadJson" });
      continue;
    }

    try {
      const incoming = parseRelayPayload(payloadJson);
      const existing = store.tripSnapshots.find((snapshot) => snapshot.tripId === incoming.tripId);

      if (!existing) {
        const incomingRole =
          incoming.participants.find((participant) => participant.userId === req.user.id)?.role ?? null;
        if (incoming.ownerId && incoming.ownerId !== req.user.id) {
          throw new Error("Only trip owner can publish a new shared trip");
        }
        if (incomingRole !== "OWNER" && incomingRole !== "ADMIN") {
          throw new Error("Publishing a new shared trip requires OWNER or ADMIN role");
        }

        const snapshot = upsertTripSnapshot(payloadJson, req.user.id);
        applied.push({
          tripId: snapshot.tripId,
          serverUpdatedAt: snapshot.serverUpdatedAt,
          sourceUpdatedAt: snapshot.sourceUpdatedAt
        });
        continue;
      }

      const role = findSnapshotRole(existing, req.user.id);
      if (!role) {
        throw new Error("Current user is not a participant of this trip");
      }
      if (incoming.sourceUpdatedAt < existing.sourceUpdatedAt) {
        throw new Error("Incoming snapshot is older than the server version");
      }
      if (role === "MEMBER" && !memberCanPush(existing.payloadJson, payloadJson)) {
        throw new Error("MEMBER can sync only expense changes");
      }
      if (role === "ADMIN" && incoming.ownerId !== existing.ownerId) {
        throw new Error("ADMIN cannot transfer trip ownership");
      }

      const snapshot = upsertTripSnapshot(payloadJson, req.user.id);
      applied.push({
        tripId: snapshot.tripId,
        serverUpdatedAt: snapshot.serverUpdatedAt,
        sourceUpdatedAt: snapshot.sourceUpdatedAt
      });
    } catch (error) {
      rejected.push({
        tripId: tripId || null,
        message: error instanceof Error ? error.message : "Unknown sync error"
      });
    }
  }

  await persistStore();
  res.json({
    applied,
    rejected,
    serverTime: now()
  });
});

app.get("/api/v1/sync/pull", requireAuth, (req, res) => {
  const since = Number(req.query?.since || 0);
  const items = store.tripSnapshots
    .filter((snapshot) => snapshot.serverUpdatedAt > since)
    .filter((snapshot) => snapshot.members.some((member) => member.userId === req.user.id))
    .map((snapshot) => ({
      tripId: snapshot.tripId,
      payloadJson: snapshot.payloadJson,
      serverUpdatedAt: snapshot.serverUpdatedAt,
      sourceUpdatedAt: snapshot.sourceUpdatedAt
    }));

  res.json({
    items,
    serverTime: now()
  });
});

app.listen(PORT, () => {
  console.log(`Triloo sync backend listening on port ${PORT}`);
});
