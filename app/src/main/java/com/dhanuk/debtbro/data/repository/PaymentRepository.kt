package com.dhanuk.debtbro.data.repository

import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.db.dao.PaymentDao
import com.dhanuk.debtbro.data.db.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val debtDao: DebtDao,
    private val paymentDao: PaymentDao
) {
    fun getPaymentsForDebt(debtId: Int): Flow<List<PaymentEntity>> = paymentDao.getPaymentsForDebt(debtId)
    
    suspend fun recordPayment(debtId: Int, amount: Double, note: String?) {
        val debt = debtDao.getDebtById(debtId) ?: return
        paymentDao.insertPayment(PaymentEntity(debtId = debtId, amount = amount, note = note?.ifBlank { null }))
        updateDebtStatus(debtId)
    }

    suspend fun deletePayment(paymentId: Int) {
        val payment = paymentDao.getPaymentById(paymentId) ?: return
        paymentDao.deletePaymentById(paymentId)
        updateDebtStatus(payment.debtId)
    }

    private suspend fun updateDebtStatus(debtId: Int) {
        val debt = debtDao.getDebtById(debtId) ?: return
        val totalPaid = paymentDao.getTotalPaidForDebt(debtId) ?: 0.0
        val status = when {
            totalPaid <= 0.0 -> "PENDING"
            totalPaid >= debt.amount -> "SETTLED"
            else -> "PARTIAL"
        }
        debtDao.updatePaymentStatus(debtId, totalPaid.coerceAtMost(debt.amount), status, System.currentTimeMillis())
    }
}
