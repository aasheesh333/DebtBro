package com.dhanuk.debtbro.data.firebase

import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.PaymentEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor(private val firestore: FirebaseFirestore) {
    suspend fun pushDebtToFirestore(userId: String, debt: DebtEntity): String {
        val collection = firestore.collection("users").document(userId).collection("debts")
        val document = if (debt.firebaseId.isNullOrBlank()) collection.document() else collection.document(debt.firebaseId)
        document.set(debt.copy(firebaseId = document.id, isSynced = true)).await()
        return document.id
    }
    suspend fun pushPaymentToFirestore(userId: String, payment: PaymentEntity) {
        val document = firestore.collection("users").document(userId).collection("payments").document(payment.firebaseId ?: payment.id.toString())
        document.set(payment.copy(firebaseId = document.id, isSynced = true)).await()
    }
    suspend fun pullAllFromFirestore(userId: String): List<DebtEntity> {
        return firestore.collection("users").document(userId).collection("debts").get().await().documents.mapNotNull { document ->
            document.toObject(DebtEntity::class.java)?.copy(firebaseId = document.id, isSynced = true)
        }
    }
    suspend fun getFirestoreDebtCount(userId: String): Int = firestore.collection("users").document(userId).collection("debts").get().await().size()
    suspend fun deleteDebtFromFirestore(userId: String, firebaseId: String) {
        firestore.collection("users").document(userId).collection("debts").document(firebaseId).delete().await()
    }
}
