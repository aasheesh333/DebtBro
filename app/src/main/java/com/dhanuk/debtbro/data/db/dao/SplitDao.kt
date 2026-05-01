package com.dhanuk.debtbro.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dhanuk.debtbro.data.db.entity.SplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitDao {
    @Query("SELECT * FROM splits ORDER BY createdAt DESC")
    fun getAllSplits(): Flow<List<SplitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplit(split: SplitEntity): Long

    @Update
    suspend fun updateSplit(split: SplitEntity)

    @Query("UPDATE splits SET aiSummary = :summary WHERE id = :id")
    suspend fun updateAiSummary(id: Int, summary: String)
}
