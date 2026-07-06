package com.dhanuk.debtbro.data.firebase

import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.PaymentEntity
import com.dhanuk.debtbro.data.db.entity.SplitEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor(private val firestore: FirebaseFirestore) {

    // ── Debts ──────────────────────────────────────────────

    /** Generate a unique Firestore document ID client-side (no network call) */
    fun generateDebtId(userId: String): String {
        return firestore.collection("users").document(userId).collection("debts").document().id
    }

    suspend fun pushDebtToFirestore(userId: String, debt: DebtEntity): String {
        val collection = firestore.collection("users").document(userId).collection("debts")
        val document = if (debt.firebaseId.isNullOrBlank()) collection.document() else collection.document(debt.firebaseId)
        document.set(debt.copy(firebaseId = document.id, isSynced = true)).await()
        return document.id
    }

    suspend fun pushDebtToFirestoreWithId(userId: String, debt: DebtEntity, firebaseId: String) {
        firestore.collection("users").document(userId).collection("debts").document(firebaseId)
            .set(debt.copy(firebaseId = firebaseId, isSynced = true)).await()
    }

    suspend fun pullAllFromFirestore(userId: String): List<DebtEntity> {
        return firestore.collection("users").document(userId).collection("debts").get().await().documents.mapNotNull { document ->
            document.toObject(DebtEntity::class.java)?.copy(firebaseId = document.id, isSynced = true)
        }
    }

    suspend fun getFirestoreDebtCount(userId: String): Int =
        firestore.collection("users").document(userId).collection("debts").get().await().size()

    suspend fun deleteDebtFromFirestore(userId: String, firebaseId: String) {
        firestore.collection("users").document(userId).collection("debts").document(firebaseId).delete().await()
    }

    /** Real-time snapshot listener for debts */
    fun observeDebtsRealTime(userId: String): Flow<List<DebtEntity>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection("users").document(userId).collection("debts")
            .addSnapshotListener { snapshot: QuerySnapshot?, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val debts = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(DebtEntity::class.java)?.copy(firebaseId = doc.id, isSynced = true)
                    }
                    trySend(debts)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Real-time snapshot listener for splits */
    fun observeSplitsRealTime(userId: String): Flow<List<SplitEntity>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection("users").document(userId).collection("splits")
            .addSnapshotListener { snapshot: QuerySnapshot?, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val splits = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(SplitEntity::class.java)?.copy(firebaseId = doc.id, isSynced = true)
                    }
                    trySend(splits)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Real-time snapshot listener for payments of a specific debt */
    fun observePaymentsForDebtRealTime(userId: String, debtFirebaseId: String): Flow<List<PaymentEntity>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection("users").document(userId)
            .collection("debts").document(debtFirebaseId).collection("payments")
            .addSnapshotListener { snapshot: QuerySnapshot?, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val payments = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(PaymentEntity::class.java)?.copy(firebaseId = doc.id, isSynced = true)
                    }
                    trySend(payments)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun deletePaymentFromFirestore(userId: String, debtFirebaseId: String, paymentFirebaseId: String) {
        firestore.collection("users").document(userId)
            .collection("debts").document(debtFirebaseId)
            .collection("payments").document(paymentFirebaseId).delete().await()
    }

    // ── Payments ───────────────────────────────────────────
    suspend fun pushPaymentToFirestore(userId: String, debtFirebaseId: String, payment: PaymentEntity): String {
        val collection = firestore.collection("users").document(userId)
            .collection("debts").document(debtFirebaseId).collection("payments")
        val document = if (payment.firebaseId.isNullOrBlank()) collection.document() else collection.document(payment.firebaseId)
        document.set(payment.copy(firebaseId = document.id, isSynced = true)).await()
        return document.id
    }

    suspend fun pullPaymentsForDebt(userId: String, debtFirebaseId: String): List<PaymentEntity> {
        return firestore.collection("users").document(userId)
            .collection("debts").document(debtFirebaseId).collection("payments")
            .get().await().documents.mapNotNull { document ->
                document.toObject(PaymentEntity::class.java)?.copy(firebaseId = document.id, isSynced = true)
            }
    }

    // ── Splits ─────────────────────────────────────────────
    suspend fun pushSplitToFirestore(userId: String, split: SplitEntity): String {
        val collection = firestore.collection("users").document(userId).collection("splits")
        val document = if (split.firebaseId.isNullOrBlank()) collection.document() else collection.document(split.firebaseId)
        document.set(split.copy(firebaseId = document.id, isSynced = true)).await()
        return document.id
    }

    suspend fun pushSplitToFirestoreWithId(userId: String, split: SplitEntity, firebaseId: String) {
        firestore.collection("users").document(userId).collection("splits").document(firebaseId)
            .set(split.copy(firebaseId = firebaseId, isSynced = true)).await()
    }

    suspend fun pullSplitsFromFirestore(userId: String): List<SplitEntity> {
        return firestore.collection("users").document(userId).collection("splits").get().await().documents.mapNotNull { document ->
            document.toObject(SplitEntity::class.java)?.copy(firebaseId = document.id, isSynced = true)
        }
    }

    suspend fun deleteSplitFromFirestore(userId: String, firebaseId: String) {
        firestore.collection("users").document(userId).collection("splits").document(firebaseId).delete().await()
    }

    /**
     * GDPR: delete every Firestore doc owned by this user. Walks debts + splits + their
     * payments. Soft-warns on individual doc failures but does not abort (we want a clean
     * wipe even if a single doc is missing or stale).
     */
    suspend fun deleteAllUserData(userId: String) {
        try {
            val debts = firestore.collection("users").document(userId).collection("debts").get().await()
            for (debtDoc in debts.documents) {
                runCatching {
                    val payments = debtDoc.reference.collection("payments").get().await()
                    for (p in payments.documents) runCatching { p.reference.delete().await() }
                }
                runCatching { debtDoc.reference.delete().await() }
            }
        } catch (e: Exception) {
            android.util.Log.w("FirebaseRepository", "deleteAllUserData: debts/payments walk failed: ${e.message}", e)
        }

        try {
            val splits = firestore.collection("users").document(userId).collection("splits").get().await()
            for (s in splits.documents) runCatching { s.reference.delete().await() }
        } catch (e: Exception) {
            android.util.Log.w("FirebaseRepository", "deleteAllUserData: splits walk failed: ${e.message}", e)
        }

        try {
            firestore.collection("users").document(userId).delete().await()
        } catch (e: Exception) {
            android.util.Log.w("FirebaseRepository", "deleteAllUserData: user doc delete failed: ${e.message}", e)
        }
    }

    // ── Account-deletion queue (Spark-plan compatible; no Cloud Functions) ──
    //
    // Schema: /deletionRequests/{uid} -> { uid, status, requestedAt, cancelledAt? }
    //   status: "PENDING" | "CANCELLED"
    //   requestedAt: Long (epoch ms)
    //   cancelledAt: Long (epoch ms, only present after cancellation)
    //
    // Flow:
    //   requestAccountDeletion(uid) -> writes a PENDING doc with requestedAt = now
    //   fetchDeletionRequest(uid)    -> reads the doc (or null if missing)
    //   cancelDeletionRequest(uid)   -> sets status=CANCELLED + cancelledAt=now
    //   deleteDeletionRequest(uid)   -> hard-deletes the doc (post-deletion cleanup)

    suspend fun recordDeletionRequest(userId: String, requestedAt: Long) {
        val data = mapOf(
            "uid" to userId,
            "status" to "PENDING",
            "requestedAt" to requestedAt
        )
        firestore.collection("deletionRequests").document(userId).set(data).await()
    }

    /** Returns `requestedAt` epoch-ms if a PENDING request exists, else null. */
    suspend fun fetchDeletionRequest(userId: String): Long? {
        // Surface Firestore permission failures loudly — a silent null here
        // (caused by firestore.rules denying reads on deletionRequests/{uid})
        // was the root cause of the "Delete now -> same id re-login shows no
        // alert" bug. Don't let it recur invisibly.
        val snap = runCatching {
            firestore.collection("deletionRequests").document(userId).get().await()
        }.getOrElse { e ->
            android.util.Log.e("FirebaseRepository", "fetchDeletionRequest read failed for uid=$userId: ${e.message}", e)
            return null
        }
        if (!snap.exists()) return null
        val status = snap.getString("status") ?: return null
        if (status != "PENDING") return null
        return snap.getLong("requestedAt")
    }

    suspend fun cancelDeletionRequest(userId: String, cancelledAt: Long) {
        val data = mapOf(
            "status" to "CANCELLED",
            "cancelledAt" to cancelledAt
        )
        runCatching {
            firestore.collection("deletionRequests").document(userId)
                .set(data, com.google.firebase.firestore.SetOptions.merge()).await()
        }.onFailure { e ->
            android.util.Log.w("FirebaseRepository", "cancelDeletionRequest failed: ${e.message}", e)
        }
    }

    suspend fun deleteDeletionRequest(userId: String) {
        runCatching {
            firestore.collection("deletionRequests").document(userId).delete().await()
        }.onFailure { e ->
            android.util.Log.w("FirebaseRepository", "deleteDeletionRequest failed: ${e.message}", e)
        }
    }
}
