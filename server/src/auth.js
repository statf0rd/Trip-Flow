import crypto from "node:crypto";
import { rowToUser } from "./db.js";

const SESSION_TTL_MS = 1000 * 60 * 60 * 24 * 30;

export function now() {
  return Date.now();
}

export function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

export function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email || "").trim());
}

export function createPasswordHash(password, salt = crypto.randomBytes(16).toString("hex")) {
  const passwordHash = crypto.scryptSync(password, salt, 64).toString("hex");
  return { passwordHash, passwordSalt: salt };
}

export function verifyPassword(password, user) {
  const hash = crypto.scryptSync(password, user.passwordSalt, 64).toString("hex");
  const a = Buffer.from(hash, "hex");
  const b = Buffer.from(user.passwordHash, "hex");
  if (a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}

export function sanitizeUser(user) {
  return {
    id: user.id,
    email: user.email,
    displayName: user.displayName,
    avatarUrl: user.avatarUrl ?? null,
    phoneNumber: user.phoneNumber ?? null,
    preferredCurrency: user.preferredCurrency ?? "RUB",
    emailVerified: !!user.emailVerified,
    createdAt: user.createdAt,
    lastLoginAt: user.lastLoginAt
  };
}

export function createSession(db, userId) {
  const token = crypto.randomUUID();
  const ts = now();
  db.prepare(`DELETE FROM sessions WHERE expires_at <= ?`).run(ts);
  db.prepare(
    `INSERT INTO sessions (token, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)`
  ).run(token, userId, ts, ts + SESSION_TTL_MS);
  return token;
}

export function deleteSession(db, token) {
  db.prepare(`DELETE FROM sessions WHERE token = ?`).run(token);
}

function readBearerToken(headerValue) {
  const raw = String(headerValue || "");
  if (!raw.startsWith("Bearer ")) return null;
  return raw.substring("Bearer ".length).trim();
}

export function makeAuthMiddleware(db) {
  const findSession = db.prepare(`SELECT * FROM sessions WHERE token = ?`);
  const findUser = db.prepare(`SELECT * FROM users WHERE id = ?`);
  const cleanupExpired = db.prepare(`DELETE FROM sessions WHERE expires_at <= ?`);

  return function requireAuth(req, res, next) {
    const token = readBearerToken(req.headers.authorization);
    if (!token) {
      res.status(401).json({ message: "Missing bearer token" });
      return;
    }

    const ts = now();
    cleanupExpired.run(ts);
    const session = findSession.get(token);
    if (!session || session.expires_at <= ts) {
      res.status(401).json({ message: "Session expired" });
      return;
    }

    const user = rowToUser(findUser.get(session.user_id));
    if (!user) {
      db.prepare(`DELETE FROM sessions WHERE token = ?`).run(token);
      res.status(401).json({ message: "User not found" });
      return;
    }

    req.authToken = token;
    req.user = user;
    next();
  };
}
