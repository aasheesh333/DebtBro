package com.dhanuk.debtbro.data.firebase

import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.db.dao.PaymentDao
import com.dhanuk.debtbro.data.db.dao.SplitDao
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.SplitEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val debtDao: DebtDao,
    private val paymentDao: PaymentDao,
    private val splitDao: SplitDao,
    private val firebaseRepository: FirebaseRepository,
    private val prefs: AppPreferences
) {
    // ── Push local → cloud ────────────────────────────────

    suspend fun pushLocalToCloud(userId: String) {
        // Push all debts + their payments
        debtDao.getAllDebtsOnce().forEach { debt ->
            val firebaseId = syncSingleDebt(userId, debt)
            syncPaymentsForDebt(userId, firebaseId, debt.id)
        }
        // Push all splits
        splitDao.getAllSplitsOnce().forEach { split ->
            syncSingleSplit(userId, split)
        }
        prefs.setLastSyncedAt(System.currentTimeMillis())
    }

    suspend fun syncSingleDebt(userId: String, debt: DebtEntity): String {
        val firebaseId = firebaseRepository.pushDebtToFirestore(userId, debt)
        debtDao.updateFirebaseId(debt.id, firebaseId)
        return firebaseId
    }

    private suspend fun syncPaymentsForDebt(userId: String, debtFirebaseId: String, localDebtId: Int) {
        val payments = paymentDao.getPaymentsForDebtStatic(localDebtId)
        for (payment in payments) {
            val pfId = firebaseRepository.pushPaymentToFirestore(userId, debtFirebaseId, payment)
            paymentDao.updatePaymentFirebaseId(payment.id, pfId)
        }
    }

    private suspend fun syncSingleSplit(userId: String, split: SplitEntity): String {
        val firebaseId = firebaseRepository.pushSplitToFirestore(userId, split)
        splitDao.updateSplitFirebaseId(split.id, firebaseId)
        return firebaseId
    }

    suspend fun mergePendingUnsynced(userId: String) {
        // Push unsynced debts + their payments
        debtDao.getUnsyncedDebts().forEach { debt ->
            val firebaseId = if (debt.firebaseId.isNullOrBlank()) {
                syncSingleDebt(userId, debt)
            } else {
                firebaseRepository.pushDebtToFirestore(userId, debt)
                debt.firebaseId
            }
            syncPaymentsForDebt(userId, firebaseId, debt.id)
        }
        // Push unsynced payments for already-synced debts
        paymentDao.getUnsyncedPayments().forEach { payment ->
            val debt = debtDao.getDebtById(payment.debtId) ?: return@forEach
            if (!debt.firebaseId.isNullOrBlank()) {
                val pfId = firebaseRepository.pushPaymentToFirestore(userId, debt.firebaseId, payment)
                paymentDao.updatePaymentFirebaseId(payment.id, pfId)
            }
        }
        // Push unsynced splits
        splitDao.getUnsyncedSplits().forEach { split ->
            syncSingleSplit(userId, split)
        }
        prefs.setLastSyncedAt(System.currentTimeMillis())
    }

    // ── Pull cloud → local ────────────────────────────────

    suspend fun pullCloudToLocal(userId: String) {
        // 1. Pull debts
        val cloudDebts = firebaseRepository.pullAllFromFirestore(userId)
        for (cloudDebt in cloudDebts) {
            if (cloudDebt.firebaseId.isNullOrBlank()) continue

            val existingLocal = debtDao.getDebtByFirebaseId(cloudDebt.firebaseId)
            if (existingLocal == null) {
                // New debt from cloud — insert with fresh local id
                val newLocalId = debtDao.insertDebt(cloudDebt.copy(id = 0, isSynced = true)).toInt()
                // Pull payments for this debt and fix debtId mapping
                pullPaymentsForDebt(userId, cloudDebt.firebaseId, newLocalId)
            } else {
                // Debt exists locally — update if cloud version is newer
                if (cloudDebt.updatedAt > existingLocal.updatedAt) {
                    debtDao.updateDebt(existingLocal.copy(
                        personName = cloudDebt.personName,
                        personEmoji = cloudDebt.personEmoji,
                        amount = cloudDebt.amount,
                        currency = cloudDebt.currency,
                        description = cloudDebt.description,
                        type = cloudDebt.type,
                        status = cloudDebt.status,
                        dueDate = cloudDebt.dueDate,
                        amountPaid = cloudDebt.amountPaid,
                        lastNudgedAt = cloudDebt.lastNudgedAt,
                        aiRoastGenerated = cloudDebt.aiRoastGenerated,
                        notes = cloudDebt.notes,
                        updatedAt = cloudDebt.updatedAt,
                        isSynced = true
                    ))
                }
                // Pull payments for this debt (also fixes debtId mapping)
                pullPaymentsForDebt(userId, cloudDebt.firebaseId, existingLocal.id)
            }
        }

        // 2. Pull splits
        val cloudSplits = firebaseRepository.pullSplitsFromFirestore(userId)
        for (cloudSplit in cloudSplits) {
            if (cloudSplit.firebaseId.isNullOrBlank()) continue
            if (splitDao.getSplitByFirebaseId(cloudSplit.firebaseId) == null) {
                splitDao.insertSplit(cloudSplit.copy(id = 0, isSynced = true))
            }
        }

        prefs.setLastSyncedAt(System.currentTimeMillis())
    }

    private suspend fun pullPaymentsForDebt(userId: String, debtFirebaseId: String, localDebtId: Int) {
        val cloudPayments = firebaseRepository.pullPaymentsForDebt(userId, debtFirebaseId)
        for (cloudPayment in cloudPayments) {
            if (cloudPayment.firebaseId.isNullOrBlank()) continue
            val existing = paymentDao.getPaymentByFirebaseId(cloudPayment.firebaseId)
            if (existing == null) {
                // Insert with correct local debtId (cloud payment's debtId is wrong/stale)
                paymentDao.insertPayment(cloudPayment.copy(id = 0, debtId = localDebtId, isSynced = true))
            }
        }
    }

    // ── Immediate push after local mutation ──────────────

    /** Push a single newly created debt to Firestore immediately */
    suspend fun pushNewDebt(userId: String, debt: DebtEntity): String {
        val firebaseId = firebaseRepository.pushDebtToFirestore(userId, debt)
        debtDao.updateFirebaseId(debt.id, firebaseId)
        return firebaseId
    }

    /** Push a single updated debt to Firestore immediately */
    suspend fun pushUpdatedDebt(userId: String, debt: DebtEntity) {
        val firebaseId = debt.firebaseId
        if (firebaseId.isNullOrBlank()) {
            pushNewDebt(userId, debt)
        } else {
            firebaseRepository.pushDebtToFirestoreWithId(userId, debt, firebaseId)
        }
    }

    /** Push a single payment to Firestore immediately */
    suspend fun pushNewPayment(userId: String, debtFirebaseId: String, payment: PaymentEntity): String {
        val pfId = firebaseRepository.pushPaymentToFirestore(userId, debtFirebaseId, payment)
        paymentDao.updatePaymentFirebaseId(payment.id, pfId)
        return pfId
    }

    /** Push a single new split to Firestore immediately */
    suspend fun pushNewSplit(userId: String, split: SplitEntity): String {
        val firebaseId = firebaseRepository.pushSplitToFirestore(userId, split)
        splitDao.updateSplitFirebaseId(split.id, firebaseId)
        return firebaseId
    }

    /** Delete a debt from Firestore immediately */
    suspend fun deleteDebtFromCloud(userId: String, debt: DebtEntity) {
        if (!debt.firebaseId.isNullOrBlank()) {
            firebaseRepository.deleteDebtFromFirestore(userId, debt.firebaseId)
        }
    }

    /** Delete a payment from Firestore immediately */
    suspend fun deletePaymentFromCloud(userId: String, debtFirebaseId: String, payment: PaymentEntity) {
        if (!payment.firebaseId.isNullOrBlank()) {
            firebaseRepository.deletePaymentFromFirestore(userId, debtFirebaseId, payment.firebaseId)
        }
    }

    // ── Full bidirectional sync ───────────────────────────

    suspend fun fullSync(userId: String) {
        mergePendingUnsynced(userId)
        pullCloudToLocal(userId)
    }
}
