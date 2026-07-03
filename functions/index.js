const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const GRACE_WINDOW_MS = 24 * 60 * 60 * 1000;

exports.requestAccountDeletion = functions
  .https.onRequest(async (req, res) => {
    const uid = req.body?.uid;
    if (!uid) {
      res.status(400).json({ error: "uid is required" });
      return;
    }
    await db.collection("deletionQueue").doc(uid).set({
      uid,
      requestAt: admin.firestore.FieldValue.serverTimestamp(),
      status: "pending"
    });
    res.status(200).json({ ok: true, graceWindowHours: 24 });
  });

exports.cancelAccountDeletion = functions
  .https.onRequest(async (req, res) => {
    const uid = req.body?.uid;
    if (!uid) {
      res.status(400).json({ error: "uid is required" });
      return;
    }
    await db.collection("deletionQueue").doc(uid).delete();
    res.status(200).json({ ok: true, cancelled: true });
  });

exports.processDeletionQueue = functions
  .pubsub.schedule("every 30 minutes")
  .onRun(async (context) => {
    const now = admin.firestore.Timestamp.now();
    const cutoff = new Date(now.toMillis() - GRACE_WINDOW_MS);
    const snapshot = await db.collection("deletionQueue")
      .where("status", "==", "pending")
      .where("requestAt", "<", cutoff)
      .get();
    if (snapshot.empty) {
      console.log("processDeletionQueue: no pending entries past grace window");
      return null;
    }
    const batch = db.batch();
    const deletions = [];
    snapshot.forEach((doc) => {
      const uid = doc.get("uid");
      deletions.push(
        admin.auth().deleteUser(uid)
          .then(() => wipeUserData(uid))
          .then(() => batch.delete(doc.ref))
          .catch((err) => console.error(`Failed to delete ${uid}:`, err.message))
      );
    });
    await Promise.all(deletions);
    await batch.commit();
    return null;
  });

async function wipeUserData(uid) {
  const userCollections = ["debts", "payments", "splits", "userSettings"];
  for (const coll of userCollections) {
    const snap = await db.collection("users").doc(uid).collection(coll).get();
    const batch = db.batch();
    snap.forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
  await db.collection("users").doc(uid).delete().catch(() => {});
}
