package com.dhanuk.debtbro.data.firebase

import android.util.Log
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
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.cancelChildren
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

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var currentUserId: String? = null
    private var debtRetryAttempt = 0
    private var splitRetryAttempt = 0

    fun startListening(userId: String) {
        if (currentUserId == userId) return
        stopListening()
        currentUserId = userId
        _isActive.value = true
        _lastError.value = null
        debtRetryAttempt = 0
        splitRetryAttempt = 0

        scope.launch {
            firebaseRepository.observeDebtsRealTime(userId)
                .retryWhen { cause, _ ->
                    if (debtRetryAttempt < 3) {
                        debtRetryAttempt++
                        val msg = cause.message ?: cause.toString()
                        Log.w("RealTimeSync", "Retrying debt listener (attempt $debtRetryAttempt): $msg")
                        kotlinx.coroutines.delay(2_000L * debtRetryAttempt)
                        true
                    } else {
                        val msg = cause.message ?: cause.toString()
                        _lastError.value = "Debt listener stopped: $msg"
                        Log.e("RealTimeSync", "Debt listener permanently failed: $msg")
                        false
                    }
                }
                .catch { e -> Log.e("RealTimeSync", "Debt listener error: ${e.message}", e) }
                .collect { cloudDebts ->
                    try { mergeDebtsFromCloud(cloudDebts); debtRetryAttempt = 0 }
                    catch (e: Exception) { Log.e("RealTimeSync", "mergeDebts failed: ${e.message}", e) }
                }
        }

        scope.launch {
            firebaseRepository.observeSplitsRealTime(userId)
                .retryWhen { cause, _ ->
                    if (splitRetryAttempt < 3) {
                        splitRetryAttempt++
                        val msg = cause.message ?: cause.toString()
                        Log.w("RealTimeSync", "Retrying split listener (attempt $splitRetryAttempt): $msg")
                        kotlinx.coroutines.delay(2_000L * splitRetryAttempt)
                        true
                    } else {
                        val msg = cause.message ?: cause.toString()
                        _lastError.value = "Split listener stopped: $msg"
                        Log.e("RealTimeSync", "Split listener permanently failed: $msg")
                        false
                    }
                }
                .catch { e -> Log.e("RealTimeSync", "Split listener error: ${e.message}", e) }
                .collect { cloudSplits ->
                    try { mergeSplitsFromCloud(cloudSplits); splitRetryAttempt = 0 }
                    catch (e: Exception) { Log.e("RealTimeSync", "mergeSplits failed: ${e.message}", e) }
                }
        }
    }

    fun stopListening() {
        scope.coroutineContext.cancelChildren()
        currentUserId = null
        _isActive.value = false
        debtRetryAttempt = 0
        splitRetryAttempt = 0
    }

    private suspend fun mergeDebtsFromCloud(cloudDebts: List<DebtEntity>) {
        for (cloudDebt in cloudDebts) {
            if (cloudDebt.firebaseId.isNullOrBlank()) continue
            val existingLocal = debtDao.getDebtByFirebaseId(cloudDebt.firebaseId)
            if (existingLocal == null) {
                debtDao.insertDebtIgnore(cloudDebt.copy(id = 0, isSynced = true))
            } else if (cloudDebt.updatedAt > existingLocal.updatedAt) {
                debtDao.updateDebt(
                    existingLocal.copy(
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
                    )
                )
            }
        }
    }

    private suspend fun mergeSplitsFromCloud(cloudSplits: List<SplitEntity>) {
        for (cloudSplit in cloudSplits) {
            if (cloudSplit.firebaseId.isNullOrBlank()) continue
            if (splitDao.getSplitByFirebaseId(cloudSplit.firebaseId) == null) {
                splitDao.insertSplitIfAbsent(cloudSplit.copy(id = 0, isSynced = true))
            }
        }
    }
}
