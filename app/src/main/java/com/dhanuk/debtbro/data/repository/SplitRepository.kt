package com.dhanuk.debtbro.data.repository

import com.dhanuk.debtbro.data.db.dao.SplitDao
import com.dhanuk.debtbro.data.db.entity.SplitEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SplitRepository @Inject constructor(private val splitDao: SplitDao) {
    fun getAllSplits(): Flow<List<SplitEntity>> = splitDao.getAllSplits()
    suspend fun insertSplit(split: SplitEntity): Long = splitDao.insertSplit(split)
    suspend fun updateAiSummary(id: Int, summary: String) = splitDao.updateAiSummary(id, summary)
    suspend fun updateSplit(split: SplitEntity) = splitDao.updateSplit(split)
    suspend fun deleteSplit(id: Int) = splitDao.deleteSplit(id)
    suspend fun getSplitById(id: Int): SplitEntity? = splitDao.getAllSplitsOnce().find { it.id == id }
}
