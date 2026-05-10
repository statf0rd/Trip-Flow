import express from "express";
import { addParticipant, findSnapshotByInviteCode } from "../snapshot.js";

export function tripsRouter(db, requireAuth) {
  const router = express.Router();

  router.post("/join-by-invite", requireAuth, (req, res) => {
    const inviteCode = String(req.body?.inviteCode || "").trim().toUpperCase();
    const displayName = String(req.body?.displayName || "").trim() || req.user.displayName;

    if (!inviteCode) {
      return res.status(400).json({ code: "INVALID_INVITE_CODE", message: "Укажите код приглашения" });
    }

    const snapshot = findSnapshotByInviteCode(db, inviteCode);
    if (!snapshot) {
      return res.status(404).json({ code: "TRIP_NOT_FOUND", message: "Поездка по коду не найдена" });
    }

    const updated = addParticipant(
      db,
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

    res.json({
      tripId: updated.tripId,
      serverUpdatedAt: updated.serverUpdatedAt
    });
  });

  return router;
}
