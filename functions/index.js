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
  try {
    const debts = await db.collection("users").doc(uid).collection("debts").get();
    for (const debtDoc of debts.docs) {
      try {
        const payments = await debtDoc.ref.collection("payments").get();
        for (const p of payments.docs) {
          await p.ref.delete().catch((err) => console.error(`  payment ${p.id}:`, err.message));
        }
      } catch (err) {
        console.error(`debt ${debtDoc.id} payments walk:`, err.message);
      }
      await debtDoc.ref.delete().catch((err) => console.error(`debt ${debtDoc.id} delete:`, err.message));
    }
  } catch (err) {
    console.error(`debts walk for ${uid}:`, err.message);
  }

  try {
    const splits = await db.collection("users").doc(uid).collection("splits").get();
    for (const s of splits.docs) {
      await s.ref.delete().catch((err) => console.error(`split ${s.id}:`, err.message));
    }
  } catch (err) {
    console.error(`splits walk for ${uid}:`, err.message);
  }

  await db.collection("users").doc(uid).delete().catch(() => {});
}
