import crypto from "node:crypto";
import { now } from "./auth.js";

const VERIFY_TTL_MS = 1000 * 60 * 60 * 24;       // 24h
const RESET_TTL_MS = 1000 * 60 * 60;             // 1h

export function generateUrlToken() {
  return crypto.randomBytes(32).toString("base64url");
}

export function createEmailVerification(db, userId, email) {
  const token = generateUrlToken();
  const ts = now();
  // Invalidate previous unconsumed tokens for this user
  db.prepare(
    `UPDATE email_verifications SET consumed_at = ? WHERE user_id = ? AND consumed_at IS NULL`
  ).run(ts, userId);
  db.prepare(
    `INSERT INTO email_verifications (token, user_id, email, created_at, expires_at)
     VALUES (?, ?, ?, ?, ?)`
  ).run(token, userId, email, ts, ts + VERIFY_TTL_MS);
  return token;
}

export function consumeEmailVerification(db, token) {
  const ts = now();
  const row = db.prepare(`SELECT * FROM email_verifications WHERE token = ?`).get(token);
  if (!row) return { ok: false, reason: "NOT_FOUND" };
  if (row.consumed_at) return { ok: false, reason: "ALREADY_USED" };
  if (row.expires_at <= ts) return { ok: false, reason: "EXPIRED" };

  db.prepare(`UPDATE email_verifications SET consumed_at = ? WHERE token = ?`).run(ts, token);
  db.prepare(`UPDATE users SET email_verified = 1 WHERE id = ? AND email = ?`).run(row.user_id, row.email);
  return { ok: true, userId: row.user_id, email: row.email };
}

export function createPasswordReset(db, userId, email) {
  const token = generateUrlToken();
  const ts = now();
  db.prepare(
    `UPDATE password_resets SET consumed_at = ? WHERE user_id = ? AND consumed_at IS NULL`
  ).run(ts, userId);
  db.prepare(
    `INSERT INTO password_resets (token, user_id, email, created_at, expires_at)
     VALUES (?, ?, ?, ?, ?)`
  ).run(token, userId, email, ts, ts + RESET_TTL_MS);
  return token;
}

export function lookupPasswordReset(db, token) {
  const ts = now();
  const row = db.prepare(`SELECT * FROM password_resets WHERE token = ?`).get(token);
  if (!row) return { ok: false, reason: "NOT_FOUND" };
  if (row.consumed_at) return { ok: false, reason: "ALREADY_USED" };
  if (row.expires_at <= ts) return { ok: false, reason: "EXPIRED" };
  return { ok: true, userId: row.user_id, email: row.email, token };
}

export function consumePasswordReset(db, token) {
  const ts = now();
  db.prepare(`UPDATE password_resets SET consumed_at = ? WHERE token = ?`).run(ts, token);
}
