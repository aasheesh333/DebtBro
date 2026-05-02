package com.dhanuk.debtbro.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dhanuk.debtbro.data.db.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE debtId = :debtId ORDER BY paidAt DESC")
    fun getPaymentsForDebt(debtId: Int): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE debtId = :debtId ORDER BY paidAt DESC")
    suspend fun getPaymentsForDebtStatic(debtId: Int): List<PaymentEntity>

    @Query("SELECT * FROM payments ORDER BY paidAt DESC")
    suspend fun getAllPaymentsOnce(): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("SELECT SUM(amount) FROM payments WHERE debtId = :debtId")
    suspend fun getTotalPaidForDebt(debtId: Int): Double?

    @Query("SELECT * FROM payments WHERE isSynced = 0")
    suspend fun getUnsyncedPayments(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: Int): PaymentEntity?

    @Query("SELECT * FROM payments WHERE firebaseId = :firebaseId LIMIT 1")
    suspend fun getPaymentByFirebaseId(firebaseId: String): PaymentEntity?

    @Query("UPDATE payments SET firebaseId = :firebaseId, isSynced = 1 WHERE id = :id")
    suspend fun updatePaymentFirebaseId(id: Int, firebaseId: String)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePaymentById(id: Int)
}
