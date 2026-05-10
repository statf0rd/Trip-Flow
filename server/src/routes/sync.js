import express from "express";
import { now } from "../auth.js";
import {
  findSnapshotByTripId,
  findSnapshotRole,
  listSnapshotsForUser,
  memberCanPush,
  parseRelayPayload,
  upsertTripSnapshot
} from "../snapshot.js";

export function syncRouter(db, requireAuth) {
  const router = express.Router();

  router.post("/push", requireAuth, (req, res) => {
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
        const existing = findSnapshotByTripId(db, incoming.tripId);

        if (!existing) {
          const incomingRole =
            incoming.participants.find((participant) => participant.userId === req.user.id)?.role ?? null;
          if (incoming.ownerId && incoming.ownerId !== req.user.id) {
            throw new Error("Only trip owner can publish a new shared trip");
          }
          if (incomingRole !== "OWNER" && incomingRole !== "ADMIN") {
            throw new Error("Publishing a new shared trip requires OWNER or ADMIN role");
          }

          const snapshot = upsertTripSnapshot(db, payloadJson, req.user.id);
          applied.push({
            tripId: snapshot.tripId,
            serverUpdatedAt: snapshot.serverUpdatedAt,
            sourceUpdatedAt: snapshot.sourceUpdatedAt
          });
          continue;
        }

        const role = findSnapshotRole(db, existing.tripId, req.user.id);
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

        const snapshot = upsertTripSnapshot(db, payloadJson, req.user.id);
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

    res.json({ applied, rejected, serverTime: now() });
  });

  router.get("/pull", requireAuth, (req, res) => {
    const since = Number(req.query?.since || 0);
    const items = listSnapshotsForUser(db, req.user.id, since).map((snapshot) => ({
      tripId: snapshot.tripId,
      payloadJson: snapshot.payloadJson,
      serverUpdatedAt: snapshot.serverUpdatedAt,
      sourceUpdatedAt: snapshot.sourceUpdatedAt
    }));

    res.json({ items, serverTime: now() });
  });

  return router;
}
