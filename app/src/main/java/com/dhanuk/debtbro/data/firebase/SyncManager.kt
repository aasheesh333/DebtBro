package com.dhanuk.debtbro.data.firebase

import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val debtDao: DebtDao,
    private val firebaseRepository: FirebaseRepository,
    private val prefs: AppPreferences
) {
    suspend fun pushLocalToCloud(userId: String) {
        debtDao.getAllDebtsOnce().forEach { syncSingleDebt(userId, it) }
        prefs.setLastSyncedAt(System.currentTimeMillis())
    }
    suspend fun pullCloudToLocal(userId: String) {
        firebaseRepository.pullAllFromFirestore(userId).forEach { cloudDebt ->
            if (!cloudDebt.firebaseId.isNullOrBlank() && debtDao.getDebtByFirebaseId(cloudDebt.firebaseId) == null) {
                debtDao.insertDebt(cloudDebt.copy(id = 0, isSynced = true))
            }
        }
        prefs.setLastSyncedAt(System.currentTimeMillis())
    }
    suspend fun syncSingleDebt(userId: String, debt: DebtEntity) {
        val firebaseId = firebaseRepository.pushDebtToFirestore(userId, debt)
        debtDao.updateFirebaseId(debt.id, firebaseId)
    }
    suspend fun mergePendingUnsynced(userId: String) {
        debtDao.getUnsyncedDebts().forEach { syncSingleDebt(userId, it) }
        prefs.setLastSyncedAt(System.currentTimeMillis())
    }
}
