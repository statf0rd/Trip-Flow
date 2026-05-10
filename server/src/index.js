import path from "node:path";
import cors from "cors";
import express from "express";
import { openDatabase } from "./db.js";
import { makeAuthMiddleware, now } from "./auth.js";
import { authRouter } from "./routes/auth.js";
import { tripsRouter } from "./routes/trips.js";
import { syncRouter } from "./routes/sync.js";
import { webRouter } from "./routes/web.js";

const PORT = Number(process.env.PORT || 8091);
const HOST = process.env.HOST || "0.0.0.0";
const DATA_DIR = process.env.DATA_DIR || path.join(process.cwd(), "data");
const ALLOWED_ORIGIN = process.env.ALLOWED_ORIGIN || "*";
const PUBLIC_BASE_URL = process.env.PUBLIC_BASE_URL || `http://localhost:${PORT}`;
const TRUST_PROXY = String(process.env.TRUST_PROXY || "1");

const db = openDatabase(DATA_DIR);
const requireAuth = makeAuthMiddleware(db);

const app = express();
app.disable("x-powered-by");
// Behind reverse proxy (Caddy) → trust X-Forwarded-* for rate-limit and IP logging.
app.set("trust proxy", TRUST_PROXY === "true" ? true : Number(TRUST_PROXY) || 1);
app.use(cors({ origin: ALLOWED_ORIGIN }));
app.use(express.json({ limit: "5mb" }));

app.get("/health", (_req, res) => {
  const userCount = db.prepare(`SELECT COUNT(*) AS n FROM users`).get().n;
  const verifiedCount = db.prepare(`SELECT COUNT(*) AS n FROM users WHERE email_verified = 1`).get().n;
  const snapshotCount = db.prepare(`SELECT COUNT(*) AS n FROM trip_snapshots`).get().n;
  res.json({
    ok: true,
    time: now(),
    users: userCount,
    verifiedUsers: verifiedCount,
    tripSnapshots: snapshotCount
  });
});

app.use("/api/v1/auth", authRouter(db, requireAuth, PUBLIC_BASE_URL));
app.use("/api/v1/trips", tripsRouter(db, requireAuth));
app.use("/api/v1/sync", syncRouter(db, requireAuth));
app.use(webRouter(db));

app.use((err, _req, res, _next) => {
  console.error("Unhandled error:", err);
  res.status(500).json({ message: "Internal server error" });
});

const server = app.listen(PORT, HOST, () => {
  console.log(`Triloo backend listening on ${HOST}:${PORT} (data: ${DATA_DIR}, base: ${PUBLIC_BASE_URL})`);
});

function shutdown(signal) {
  console.log(`Received ${signal}, shutting down`);
  server.close(() => {
    try { db.close(); } catch (_e) { /* ignore */ }
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 5000).unref();
}
process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
