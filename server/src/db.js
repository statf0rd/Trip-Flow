import fs from "node:fs";
import path from "node:path";
import Database from "better-sqlite3";

const SCHEMA = `
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE COLLATE NOCASE,
  display_name TEXT NOT NULL,
  avatar_url TEXT,
  phone_number TEXT,
  preferred_currency TEXT NOT NULL DEFAULT 'RUB',
  password_hash TEXT NOT NULL,
  password_salt TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  last_login_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS trip_snapshots (
  trip_id TEXT PRIMARY KEY,
  owner_id TEXT,
  invite_code TEXT,
  payload_json TEXT NOT NULL,
  source_updated_at INTEGER NOT NULL,
  server_updated_at INTEGER NOT NULL,
  updated_by_user_id TEXT
);
CREATE INDEX IF NOT EXISTS idx_snapshots_invite ON trip_snapshots(invite_code);
CREATE INDEX IF NOT EXISTS idx_snapshots_owner ON trip_snapshots(owner_id);
CREATE INDEX IF NOT EXISTS idx_snapshots_server_updated ON trip_snapshots(server_updated_at);

CREATE TABLE IF NOT EXISTS trip_members (
  trip_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  role TEXT NOT NULL,
  email TEXT,
  display_name TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (trip_id, user_id),
  FOREIGN KEY (trip_id) REFERENCES trip_snapshots(trip_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_trip_members_user ON trip_members(user_id);

CREATE TABLE IF NOT EXISTS password_reset_requests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_pwd_reset_email ON password_reset_requests(email);

CREATE TABLE IF NOT EXISTS email_verifications (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  email TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_email_verif_user ON email_verifications(user_id);

CREATE TABLE IF NOT EXISTS password_resets (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  email TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_pwd_resets_user ON password_resets(user_id);
`;

function columnExists(db, table, column) {
  return db
    .prepare(`PRAGMA table_info(${table})`)
    .all()
    .some((row) => row.name === column);
}

function applyMigrations(db) {
  if (!columnExists(db, "users", "email_verified")) {
    db.exec(`ALTER TABLE users ADD COLUMN email_verified INTEGER NOT NULL DEFAULT 0`);
  }
}

export function openDatabase(dataDir) {
  fs.mkdirSync(dataDir, { recursive: true });
  const dbPath = path.join(dataDir, "triloo.db");
  const db = new Database(dbPath);
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  db.pragma("synchronous = NORMAL");
  db.exec(SCHEMA);
  applyMigrations(db);
  return db;
}

export function rowToUser(row) {
  if (!row) return null;
  return {
    id: row.id,
    email: row.email,
    displayName: row.display_name,
    avatarUrl: row.avatar_url ?? null,
    phoneNumber: row.phone_number ?? null,
    preferredCurrency: row.preferred_currency ?? "RUB",
    passwordHash: row.password_hash,
    passwordSalt: row.password_salt,
    emailVerified: !!row.email_verified,
    createdAt: row.created_at,
    lastLoginAt: row.last_login_at
  };
}

export function rowToSnapshot(row) {
  if (!row) return null;
  return {
    tripId: row.trip_id,
    ownerId: row.owner_id ?? null,
    inviteCode: row.invite_code ?? null,
    payloadJson: row.payload_json,
    sourceUpdatedAt: row.source_updated_at,
    serverUpdatedAt: row.server_updated_at,
    updatedByUserId: row.updated_by_user_id ?? null
  };
}
