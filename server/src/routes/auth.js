import crypto from "node:crypto";
import express from "express";
import {
  createPasswordHash,
  createSession,
  deleteSession,
  isValidEmail,
  normalizeEmail,
  now,
  sanitizeUser,
  verifyPassword
} from "../auth.js";
import { rowToUser } from "../db.js";
import { listSnapshotsForUser, removeParticipant, replaceParticipantProfile } from "../snapshot.js";
import {
  consumeEmailVerification,
  consumePasswordReset,
  createEmailVerification,
  createPasswordReset,
  lookupPasswordReset
} from "../tokens.js";
import { buildPasswordResetEmail, buildVerificationEmail, sendMail } from "../mailer.js";
import {
  passwordResetLimiter,
  signInLimiter,
  signUpLimiter,
  verifyEmailLimiter
} from "../rateLimit.js";

export function authRouter(db, requireAuth, baseUrl) {
  const router = express.Router();
  const requireEmailVerification =
    String(process.env.REQUIRE_EMAIL_VERIFICATION || "").toLowerCase() === "true";

  function buildVerifyLink(token) {
    const root = (baseUrl || "").replace(/\/+$/, "");
    return `${root}/verify-email?token=${encodeURIComponent(token)}`;
  }

  function buildResetLink(token) {
    const root = (baseUrl || "").replace(/\/+$/, "");
    return `${root}/reset-password?token=${encodeURIComponent(token)}`;
  }

  async function dispatchVerificationEmail(user) {
    const token = createEmailVerification(db, user.id, user.email);
    const link = buildVerifyLink(token);
    const mail = buildVerificationEmail({ displayName: user.displayName, link });
    try {
      await sendMail({ to: user.email, ...mail });
    } catch (error) {
      console.error("[auth] failed to send verification email:", error);
    }
    return token;
  }

  async function dispatchPasswordResetEmail(user) {
    const token = createPasswordReset(db, user.id, user.email);
    const link = buildResetLink(token);
    const mail = buildPasswordResetEmail({ displayName: user.displayName, link });
    try {
      await sendMail({ to: user.email, ...mail });
    } catch (error) {
      console.error("[auth] failed to send password-reset email:", error);
    }
    return token;
  }

  router.post("/sign-up", signUpLimiter, async (req, res) => {
    const email = normalizeEmail(req.body?.email);
    const password = String(req.body?.password || "");
    const displayName = String(req.body?.displayName || "").trim();

    if (!isValidEmail(email)) {
      return res.status(400).json({ code: "INVALID_EMAIL", message: "Неверный формат email" });
    }
    if (password.length < 6) {
      return res.status(400).json({ code: "WEAK_PASSWORD", message: "Пароль должен быть не короче 6 символов" });
    }
    if (!displayName) {
      return res.status(400).json({ code: "INVALID_NAME", message: "Укажите имя пользователя" });
    }

    const existing = db.prepare(`SELECT id FROM users WHERE email = ?`).get(email);
    if (existing) {
      return res.status(409).json({ code: "EMAIL_ALREADY_IN_USE", message: "Этот email уже используется" });
    }

    const credentials = createPasswordHash(password);
    const ts = now();
    const id = crypto.randomUUID();

    const initialEmailVerified = requireEmailVerification ? 0 : 1;
    db.prepare(
      `INSERT INTO users
        (id, email, display_name, avatar_url, phone_number, preferred_currency,
         password_hash, password_salt, email_verified, created_at, last_login_at)
       VALUES (?, ?, ?, NULL, NULL, 'RUB', ?, ?, ?, ?, ?)`
    ).run(
      id, email, displayName,
      credentials.passwordHash, credentials.passwordSalt,
      initialEmailVerified, ts, ts
    );

    const sessionToken = createSession(db, id);
    const userRow = rowToUser(db.prepare(`SELECT * FROM users WHERE id = ?`).get(id));
    if (requireEmailVerification) {
      await dispatchVerificationEmail(userRow);
    }
    res.status(201).json({ token: sessionToken, user: sanitizeUser(userRow) });
  });

  router.post("/sign-in", signInLimiter, (req, res) => {
    const email = normalizeEmail(req.body?.email);
    const password = String(req.body?.password || "");
    const userRow = rowToUser(db.prepare(`SELECT * FROM users WHERE email = ?`).get(email));

    if (!userRow) {
      return res.status(404).json({ code: "USER_NOT_FOUND", message: "Пользователь не найден" });
    }
    if (!verifyPassword(password, userRow)) {
      return res.status(401).json({ code: "WRONG_PASSWORD", message: "Неверный пароль" });
    }

    const ts = now();
    db.prepare(`UPDATE users SET last_login_at = ? WHERE id = ?`).run(ts, userRow.id);
    userRow.lastLoginAt = ts;
    const token = createSession(db, userRow.id);
    res.json({ token, user: sanitizeUser(userRow) });
  });

  router.post("/sign-out", requireAuth, (req, res) => {
    deleteSession(db, req.authToken);
    res.json({ ok: true });
  });

  router.post("/password-reset", passwordResetLimiter, async (req, res) => {
    const email = normalizeEmail(req.body?.email);
    if (!isValidEmail(email)) {
      return res.status(400).json({ code: "INVALID_EMAIL", message: "Неверный формат email" });
    }
    db.prepare(
      `INSERT INTO password_reset_requests (email, created_at) VALUES (?, ?)`
    ).run(email, now());

    // Always respond ok, even if user not found — to avoid email enumeration.
    const userRow = rowToUser(db.prepare(`SELECT * FROM users WHERE email = ?`).get(email));
    if (userRow) {
      await dispatchPasswordResetEmail(userRow);
    }
    res.json({ ok: true });
  });

  router.post("/password-reset/confirm", passwordResetLimiter, (req, res) => {
    const token = String(req.body?.token || "").trim();
    const password = String(req.body?.password || "");
    if (!token) {
      return res.status(400).json({ code: "INVALID_TOKEN", message: "Не указан токен" });
    }
    if (password.length < 6) {
      return res.status(400).json({ code: "WEAK_PASSWORD", message: "Пароль должен быть не короче 6 символов" });
    }

    const lookup = lookupPasswordReset(db, token);
    if (!lookup.ok) {
      const code = lookup.reason === "EXPIRED" ? "TOKEN_EXPIRED" : "TOKEN_INVALID";
      return res.status(400).json({ code, message: "Ссылка недействительна или просрочена" });
    }

    const credentials = createPasswordHash(password);
    db.prepare(
      `UPDATE users SET password_hash = ?, password_salt = ? WHERE id = ?`
    ).run(credentials.passwordHash, credentials.passwordSalt, lookup.userId);
    db.prepare(`DELETE FROM sessions WHERE user_id = ?`).run(lookup.userId);
    consumePasswordReset(db, token);
    res.json({ ok: true });
  });

  router.post("/verify-email/resend", verifyEmailLimiter, requireAuth, async (req, res) => {
    if (req.user.emailVerified) {
      return res.json({ ok: true, alreadyVerified: true });
    }
    await dispatchVerificationEmail(req.user);
    res.json({ ok: true });
  });

  router.post("/verify-email/confirm", verifyEmailLimiter, (req, res) => {
    const token = String(req.body?.token || "").trim();
    if (!token) {
      return res.status(400).json({ code: "INVALID_TOKEN", message: "Не указан токен" });
    }
    const result = consumeEmailVerification(db, token);
    if (!result.ok) {
      const code =
        result.reason === "EXPIRED" ? "TOKEN_EXPIRED" :
        result.reason === "ALREADY_USED" ? "TOKEN_ALREADY_USED" : "TOKEN_INVALID";
      return res.status(400).json({ code, message: "Ссылка недействительна или просрочена" });
    }
    res.json({ ok: true, userId: result.userId, email: result.email });
  });

  router.get("/me", requireAuth, (req, res) => {
    res.json({ user: sanitizeUser(req.user) });
  });

  router.patch("/profile", requireAuth, (req, res) => {
    const displayName = String(req.body?.displayName || "").trim();
    const avatarUrl = req.body?.avatarUrl ? String(req.body.avatarUrl).trim() : null;

    if (displayName) req.user.displayName = displayName;
    req.user.avatarUrl = avatarUrl || null;

    db.prepare(
      `UPDATE users SET display_name = ?, avatar_url = ? WHERE id = ?`
    ).run(req.user.displayName, req.user.avatarUrl, req.user.id);

    const memberSnapshotRows = db
      .prepare(
        `SELECT s.* FROM trip_snapshots s
         JOIN trip_members m ON m.trip_id = s.trip_id
         WHERE m.user_id = ?`
      )
      .all(req.user.id);
    for (const row of memberSnapshotRows) {
      replaceParticipantProfile(db, {
        tripId: row.trip_id,
        ownerId: row.owner_id,
        inviteCode: row.invite_code,
        payloadJson: row.payload_json,
        sourceUpdatedAt: row.source_updated_at,
        serverUpdatedAt: row.server_updated_at,
        updatedByUserId: row.updated_by_user_id
      }, req.user);
    }

    res.json({ user: sanitizeUser(req.user) });
  });

  router.delete("/account", requireAuth, (req, res) => {
    const ownsTrips = db
      .prepare(`SELECT 1 FROM trip_snapshots WHERE owner_id = ? LIMIT 1`)
      .get(req.user.id);
    if (ownsTrips) {
      return res.status(409).json({
        code: "ACCOUNT_OWNS_TRIPS",
        message: "Нельзя удалить аккаунт, пока за вами закреплены поездки как за владельцем"
      });
    }

    const memberSnapshots = listSnapshotsForUser(db, req.user.id, -1);
    for (const snapshot of memberSnapshots) {
      const updated = removeParticipant(db, snapshot, req.user.id);
      if (!updated.members || updated.members.length === 0) {
        db.prepare(`DELETE FROM trip_snapshots WHERE trip_id = ?`).run(updated.tripId);
      }
    }
    db.prepare(`DELETE FROM users WHERE id = ?`).run(req.user.id);

    res.json({ ok: true });
  });

  return router;
}
