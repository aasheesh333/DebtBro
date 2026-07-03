package com.dhanuk.debtbro.data.repository

import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.db.dao.PaymentDao
import com.dhanuk.debtbro.data.db.dao.SplitDao
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebtRepository @Inject constructor(
    private val debtDao: DebtDao,
    private val paymentDao: PaymentDao,
    private val splitDao: SplitDao
) {
    fun getAllDebts(): Flow<List<DebtEntity>> = debtDao.getAllDebts()
    suspend fun getAllDebtsOnce(): List<DebtEntity> = debtDao.getAllDebtsOnce()
    suspend fun getDebtById(id: Int): DebtEntity? = debtDao.getDebtById(id)
    suspend fun getDebtByFirebaseId(firebaseId: String): DebtEntity? = debtDao.getDebtByFirebaseId(firebaseId)
    fun observeDebtById(id: Int): Flow<DebtEntity?> = debtDao.observeDebtById(id)
    fun getPendingDebts(): Flow<List<DebtEntity>> = debtDao.getPendingDebts()
    suspend fun insertDebt(debt: DebtEntity): Long = debtDao.insertDebt(debt)
    suspend fun updateDebt(debt: DebtEntity) = debtDao.updateDebt(debt.copy(updatedAt = System.currentTimeMillis(), isSynced = false))
    suspend fun deleteDebt(debt: DebtEntity) = debtDao.deleteDebt(debt)
    suspend fun updatePaymentStatus(id: Int, amountPaid: Double, status: String) = debtDao.updatePaymentStatus(id, amountPaid, status, System.currentTimeMillis())
    suspend fun updateRoast(id: Int, roast: String, nudgedAt: Long) = debtDao.updateRoast(id, roast, nudgedAt)
    suspend fun getUnsyncedDebts(): List<DebtEntity> = debtDao.getUnsyncedDebts()
    suspend fun deleteSettledDebts() = debtDao.deleteSettledDebts()

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
