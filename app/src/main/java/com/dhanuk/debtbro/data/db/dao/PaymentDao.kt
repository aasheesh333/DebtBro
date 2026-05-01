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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("SELECT SUM(amount) FROM payments WHERE debtId = :debtId")
    suspend fun getTotalPaidForDebt(debtId: Int): Double?

    @Query("SELECT * FROM payments WHERE isSynced = 0")
    suspend fun getUnsyncedPayments(): List<PaymentEntity>
}
