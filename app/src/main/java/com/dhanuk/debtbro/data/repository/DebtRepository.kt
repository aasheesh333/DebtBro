package com.dhanuk.debtbro.data.repository

import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.db.dao.PaymentDao
import com.dhanuk.debtbro.data.db.dao.SplitDao
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.worker.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebtRepository @Inject constructor(
    private val debtDao: DebtDao,
    private val paymentDao: PaymentDao,
    private val splitDao: SplitDao,
    private val reminderScheduler: ReminderScheduler
) {
    fun getAllDebts(): Flow<List<DebtEntity>> = debtDao.getAllDebts()
    suspend fun getAllDebtsOnce(): List<DebtEntity> = debtDao.getAllDebtsOnce()
    suspend fun getDebtById(id: Int): DebtEntity? = debtDao.getDebtById(id)
    suspend fun getDebtByFirebaseId(firebaseId: String): DebtEntity? = debtDao.getDebtByFirebaseId(firebaseId)
    fun observeDebtById(id: Int): Flow<DebtEntity?> = debtDao.observeDebtById(id)
    fun getPendingDebts(): Flow<List<DebtEntity>> = debtDao.getPendingDebts()

    suspend fun insertDebt(debt: DebtEntity): Long {
        val id = debtDao.insertDebt(debt)
        val saved = debtDao.getDebtById(id.toInt())
        if (saved != null) {
            runCatching { reminderScheduler.scheduleForDebt(saved) }
            runCatching { debtDao.markReminderScheduled(saved.id) }
        }
        return id
    }

    suspend fun updateDebt(debt: DebtEntity) {
        val updated = debt.copy(updatedAt = System.currentTimeMillis(), isSynced = false)
        debtDao.updateDebt(updated)
        if (updated.dueDate != null && !updated.settled && updated.status != "SETTLED") {
            runCatching { reminderScheduler.scheduleForDebt(updated) }
            runCatching { debtDao.markReminderScheduled(updated.id) }
        } else {
            reminderScheduler.cancelForDebt(updated.id.toLong())
            runCatching { debtDao.markReminderScheduled(updated.id) }
        }
    }

    suspend fun deleteDebt(debt: DebtEntity) {
        debtDao.deleteDebt(debt)
        reminderScheduler.cancelForDebt(debt.id.toLong())
    }

    suspend fun markSettled(debt: DebtEntity) {
        val settled = debt.copy(
            settled = true,
            status = "SETTLED",
            amountPaid = debt.amount,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        debtDao.updateDebt(settled)
        reminderScheduler.cancelForDebt(debt.id.toLong())
    }

    suspend fun updatePaymentStatus(id: Int, amountPaid: Double, status: String) {
        debtDao.updatePaymentStatus(id, amountPaid, status, System.currentTimeMillis())
        if (status == "SETTLED") {
            reminderScheduler.cancelForDebt(id.toLong())
        } else {
            debtDao.getDebtById(id)?.let {
                runCatching { reminderScheduler.scheduleForDebt(it) }
                runCatching { debtDao.markReminderScheduled(it.id) }
            }
        }
    }

    suspend fun updateRoast(id: Int, roast: String, nudgedAt: Long) = debtDao.updateRoast(id, roast, nudgedAt)
    suspend fun getUnsyncedDebts(): List<DebtEntity> = debtDao.getUnsyncedDebts()
    suspend fun deleteSettledDebts() = debtDao.deleteSettledDebts()
    suspend fun getAllUnscheduledDueDebts(): List<DebtEntity> = debtDao.getUnscheduledDueDebts()
    suspend fun markReminderScheduled(id: Int) = debtDao.markReminderScheduled(id)

    /** Wipes ALL local user data (debts, payments, splits) — used on sign-out / account deletion. */
    suspend fun clearLocalData() {
        debtDao.deleteAll()
        paymentDao.deleteAll()
        splitDao.deleteAll()
    }

    /**
     * Clears only debts + payments but keeps the table structure intact. Used when wiping
     * sensitive data while the user is still signed in (e.g., "Reset app data" flow).
     */
    suspend fun clearLocalDebtsOnly() {
        paymentDao.deleteAll()
        debtDao.deleteAll()
    }

    fun getTotalOwedToMe(): Flow<Double> = debtDao.getAllDebts().map { debts -> debts.filter { it.type == "THEY_OWE_ME" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid } }
    fun getTotalIOwe(): Flow<Double> = debtDao.getAllDebts().map { debts -> debts.filter { it.type == "I_OWE_THEM" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid } }
}
