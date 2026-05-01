package com.dhanuk.debtbro.data.repository

import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebtRepository @Inject constructor(private val debtDao: DebtDao) {
    fun getAllDebts(): Flow<List<DebtEntity>> = debtDao.getAllDebts()
    suspend fun getAllDebtsOnce(): List<DebtEntity> = debtDao.getAllDebtsOnce()
    suspend fun getDebtById(id: Int): DebtEntity? = debtDao.getDebtById(id)
    fun observeDebtById(id: Int): Flow<DebtEntity?> = debtDao.observeDebtById(id)
    fun getPendingDebts(): Flow<List<DebtEntity>> = debtDao.getPendingDebts()
    suspend fun insertDebt(debt: DebtEntity): Long = debtDao.insertDebt(debt)
    suspend fun updateDebt(debt: DebtEntity) = debtDao.updateDebt(debt.copy(updatedAt = System.currentTimeMillis(), isSynced = false))
    suspend fun deleteDebt(debt: DebtEntity) = debtDao.deleteDebt(debt)
    suspend fun updatePaymentStatus(id: Int, amountPaid: Double, status: String) = debtDao.updatePaymentStatus(id, amountPaid, status, System.currentTimeMillis())
    suspend fun updateRoast(id: Int, roast: String, nudgedAt: Long) = debtDao.updateRoast(id, roast, nudgedAt)
    suspend fun getUnsyncedDebts(): List<DebtEntity> = debtDao.getUnsyncedDebts()
    suspend fun deleteSettledDebts() = debtDao.deleteSettledDebts()
    fun getTotalOwedToMe(): Flow<Double> = debtDao.getAllDebts().map { debts -> debts.filter { it.type == "THEY_OWE_ME" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid } }
    fun getTotalIOwe(): Flow<Double> = debtDao.getAllDebts().map { debts -> debts.filter { it.type == "I_OWE_THEM" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid } }
}
