package com.dhanuk.debtbro.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    fun getAllDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    suspend fun getAllDebtsOnce(): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebtById(id: Int): DebtEntity?

    @Query("SELECT * FROM debts WHERE id = :id")
    fun observeDebtById(id: Int): Flow<DebtEntity?>

    @Query("SELECT * FROM debts WHERE firebaseId = :firebaseId LIMIT 1")
    suspend fun getDebtByFirebaseId(firebaseId: String): DebtEntity?

    @Query("SELECT * FROM debts WHERE status != 'SETTLED' ORDER BY createdAt DESC")
    fun getPendingDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE type = :type ORDER BY createdAt DESC")
    fun getDebtsByType(type: String): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE isSynced = 0")
    suspend fun getUnsyncedDebts(): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE dueDate IS NOT NULL AND dueDate < :now AND status != 'SETTLED'")
    suspend fun getOverdueDebts(now: Long): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE dueDate IS NOT NULL AND dueDate BETWEEN :from AND :to AND status != 'SETTLED'")
    suspend fun getDebtsDueBetween(from: Long, to: Long): List<DebtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDebtIgnore(debt: DebtEntity): Long

    @Update
    suspend fun updateDebt(debt: DebtEntity)

    @Delete
    suspend fun deleteDebt(debt: DebtEntity)

    @Query("UPDATE debts SET firebaseId = :firebaseId, isSynced = 1 WHERE id = :id")
    suspend fun updateFirebaseId(id: Int, firebaseId: String)

    @Query("UPDATE debts SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)

    @Query("UPDATE debts SET amountPaid = :amountPaid, status = :status, updatedAt = :updatedAt, isSynced = 0 WHERE id = :id")
    suspend fun updatePaymentStatus(id: Int, amountPaid: Double, status: String, updatedAt: Long)

    @Query("UPDATE debts SET aiRoastGenerated = :roast, lastNudgedAt = :nudgedAt, updatedAt = :nudgedAt, isSynced = 0 WHERE id = :id")
    suspend fun updateRoast(id: Int, roast: String, nudgedAt: Long)

    @Query("DELETE FROM debts WHERE status = 'SETTLED'")
    suspend fun deleteSettledDebts()

    @Query("DELETE FROM debts")
    suspend fun deleteAll()
}
