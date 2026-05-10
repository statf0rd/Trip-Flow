import { now } from "./auth.js";
import { rowToSnapshot } from "./db.js";

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

export function parseRelayPayload(payloadJson) {
  const parsed = JSON.parse(payloadJson);
  const tripId = parsed?.trip?.id;
  if (!tripId) {
    throw new Error("Payload does not contain trip.id");
  }

  const participants = safeArray(parsed.participants).map((participant) => ({
    userId: participant.userId,
    displayName: participant.displayName ?? "",
    email: participant.email ?? null,
    role: participant.role ?? "MEMBER",
    updatedAt: Number(participant.updatedAt || 0)
  }));

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

function sortById(items, idKey = "id") {
  return safeArray(items)
    .map((item) => JSON.parse(JSON.stringify(item)))
    .sort((left, right) =>
      String(left[idKey] || "").localeCompare(String(right[idKey] || ""))
    );
}

function normalizeParticipants(items) {
  return safeArray(items)
    .map((item) => ({
      userId: item.userId,
      role: item.role,
      email: item.email ?? null,
      displayName: item.displayName ?? ""
    }))
    .sort((left, right) => String(left.userId).localeCompare(String(right.userId)));
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
    .sort((left, right) => String(left.id).localeCompare(String(right.id)));
}

export function memberCanPush(existingJson, incomingJson) {
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

function replaceMembers(db, tripId, participants) {
  db.prepare(`DELETE FROM trip_members WHERE trip_id = ?`).run(tripId);
  const insert = db.prepare(
    `INSERT INTO trip_members (trip_id, user_id, role, email, display_name, updated_at)
     VALUES (?, ?, ?, ?, ?, ?)`
  );
  for (const p of participants) {
    if (!p.userId) continue;
    insert.run(
      tripId,
      p.userId,
      p.role || "MEMBER",
      p.email ?? null,
      p.displayName ?? "",
      Number(p.updatedAt || 0)
    );
  }
}

export function upsertTripSnapshot(db, payloadJson, updatedByUserId) {
  const meta = parseRelayPayload(payloadJson);
  const ts = now();
  db.prepare(
    `INSERT INTO trip_snapshots
       (trip_id, owner_id, invite_code, payload_json, source_updated_at, server_updated_at, updated_by_user_id)
     VALUES (?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(trip_id) DO UPDATE SET
       owner_id = excluded.owner_id,
       invite_code = excluded.invite_code,
       payload_json = excluded.payload_json,
       source_updated_at = excluded.source_updated_at,
       server_updated_at = excluded.server_updated_at,
       updated_by_user_id = excluded.updated_by_user_id`
  ).run(
    meta.tripId,
    meta.ownerId,
    meta.inviteCode,
    payloadJson,
    meta.sourceUpdatedAt,
    ts,
    updatedByUserId
  );
  replaceMembers(db, meta.tripId, meta.participants);
  return {
    tripId: meta.tripId,
    ownerId: meta.ownerId,
    inviteCode: meta.inviteCode,
    payloadJson,
    sourceUpdatedAt: meta.sourceUpdatedAt,
    serverUpdatedAt: ts,
    updatedByUserId,
    members: meta.participants
  };
}

export function persistParsedSnapshot(db, parsed, updatedByUserId) {
  return upsertTripSnapshot(db, JSON.stringify(parsed), updatedByUserId);
}

export function findSnapshotByInviteCode(db, inviteCode) {
  const row = db
    .prepare(`SELECT * FROM trip_snapshots WHERE invite_code = ? LIMIT 1`)
    .get(inviteCode);
  if (row) return rowToSnapshot(row);
  // Fallback: legacy snapshots may have invite_code stored only inside payload.
  const all = db.prepare(`SELECT * FROM trip_snapshots`).all();
  for (const r of all) {
    try {
      if (parseRelayPayload(r.payload_json).inviteCode === inviteCode) {
        return rowToSnapshot(r);
      }
    } catch (_error) {
      // ignore malformed snapshot
    }
  }
  return null;
}

export function findSnapshotByTripId(db, tripId) {
  return rowToSnapshot(
    db.prepare(`SELECT * FROM trip_snapshots WHERE trip_id = ?`).get(tripId)
  );
}

export function findSnapshotRole(db, tripId, userId) {
  const row = db
    .prepare(`SELECT role FROM trip_members WHERE trip_id = ? AND user_id = ?`)
    .get(tripId, userId);
  return row?.role ?? null;
}

export function listSnapshotsForUser(db, userId, since) {
  return db
    .prepare(
      `SELECT s.*
       FROM trip_snapshots s
       JOIN trip_members m ON m.trip_id = s.trip_id
       WHERE m.user_id = ? AND s.server_updated_at > ?`
    )
    .all(userId, since)
    .map(rowToSnapshot);
}

export function replaceParticipantProfile(db, snapshot, user) {
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
  if (!changed) return snapshot;
  return persistParsedSnapshot(db, parsed, user.id);
}

export function removeParticipant(db, snapshot, userId) {
  const parsed = JSON.parse(snapshot.payloadJson);
  const beforeCount = safeArray(parsed.participants).length;
  parsed.participants = safeArray(parsed.participants).filter(
    (participant) => participant.userId !== userId
  );
  if (safeArray(parsed.participants).length === beforeCount) {
    return snapshot;
  }
  return persistParsedSnapshot(db, parsed, snapshot.updatedByUserId);
}

export function addParticipant(db, snapshot, participant, updatedByUserId) {
  const parsed = JSON.parse(snapshot.payloadJson);
  const participants = safeArray(parsed.participants);
  const existingIndex = participants.findIndex(
    (item) => item.userId === participant.userId
  );
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
  return persistParsedSnapshot(db, parsed, updatedByUserId);
}
