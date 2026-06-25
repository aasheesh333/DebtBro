package com.dhanuk.debtbro.data.firebase

import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.db.dao.PaymentDao
import com.dhanuk.debtbro.data.db.dao.SplitDao
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.SplitEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.cancelChildren
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealTimeSyncManager @Inject constructor(
    private val firebaseRepository: FirebaseRepository,
    private val debtDao: DebtDao,
    private val paymentDao: PaymentDao,
    private val splitDao: SplitDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var currentUserId: String? = null

    fun startListening(userId: String) {
        if (currentUserId == userId) return
        stopListening()
        currentUserId = userId
        _isActive.value = true

        scope.launch {
            firebaseRepository.observeDebtsRealTime(userId)
                .catch { e -> android.util.Log.e("RealTimeSync", "Debt listener error: ${e.message}", e) }
                .collect { cloudDebts ->
                    try { mergeDebtsFromCloud(cloudDebts) }
                    catch (e: Exception) { android.util.Log.e("RealTimeSync", "mergeDebts failed: ${e.message}", e) }
                }
        }

        scope.launch {
            firebaseRepository.observeSplitsRealTime(userId)
                .catch { e -> android.util.Log.e("RealTimeSync", "Split listener error: ${e.message}", e) }
                .collect { cloudSplits ->
                    try { mergeSplitsFromCloud(cloudSplits) }
                    catch (e: Exception) { android.util.Log.e("RealTimeSync", "mergeSplits failed: ${e.message}", e) }
                }
        }
    }

    fun stopListening() {
        scope.coroutineContext.cancelChildren()
        currentUserId = null
        _isActive.value = false
    }

    private suspend fun mergeDebtsFromCloud(cloudDebts: List<DebtEntity>) {
        for (cloudDebt in cloudDebts) {
            if (cloudDebt.firebaseId.isNullOrBlank()) continue
            val existingLocal = debtDao.getDebtByFirebaseId(cloudDebt.firebaseId)
            if (existingLocal == null) {
                debtDao.insertDebtIgnore(cloudDebt.copy(id = 0, isSynced = true))
            } else if (cloudDebt.updatedAt > existingLocal.updatedAt) {
                debtDao.updateDebt(existingLocal.copy(
                    personName = cloudDebt.personName,
                    personEmoji = cloudDebt.personEmoji,
                    amount = cloudDebt.amount,
                    currency = cloudDebt.currency,
                    description = cloudDebt.description,
                    type = cloudDebt.type,
                    status = cloudDebt.status,
                    dueDate = cloudDebt.dueDate,
                    amountPaid = cloudDebt.amountPaid,
                    lastNudgedAt = cloudDebt.lastNudgedAt,
                    aiRoastGenerated = cloudDebt.aiRoastGenerated,
                    notes = cloudDebt.notes,
                    updatedAt = cloudDebt.updatedAt,
                    isSynced = true
                ))
            }
        }
    }

    private suspend fun mergeSplitsFromCloud(cloudSplits: List<SplitEntity>) {
        for (cloudSplit in cloudSplits) {
            if (cloudSplit.firebaseId.isNullOrBlank()) continue
            if (splitDao.getSplitByFirebaseId(cloudSplit.firebaseId) == null) {
                splitDao.insertSplit(cloudSplit.copy(id = 0, isSynced = true))
            }
        }
    }
}