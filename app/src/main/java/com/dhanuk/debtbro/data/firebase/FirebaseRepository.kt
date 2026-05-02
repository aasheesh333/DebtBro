package com.dhanuk.debtbro.data.firebase

import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.PaymentEntity
import com.dhanuk.debtbro.data.db.entity.SplitEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor(private val firestore: FirebaseFirestore) {

    // ── Debts ──────────────────────────────────────────────
    suspend fun pushDebtToFirestore(userId: String, debt: DebtEntity): String {
        val collection = firestore.collection("users").document(userId).collection("debts")
        val document = if (debt.firebaseId.isNullOrBlank()) collection.document() else collection.document(debt.firebaseId)
        document.set(debt.copy(firebaseId = document.id, isSynced = true)).await()
        return document.id
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

    suspend fun pullSplitsFromFirestore(userId: String): List<SplitEntity> {
        return firestore.collection("users").document(userId).collection("splits").get().await().documents.mapNotNull { document ->
            document.toObject(SplitEntity::class.java)?.copy(firebaseId = document.id, isSynced = true)
        }
    }
}
