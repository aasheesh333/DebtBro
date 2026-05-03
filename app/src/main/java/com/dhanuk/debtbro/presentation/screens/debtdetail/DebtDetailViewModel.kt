package com.dhanuk.debtbro.presentation.screens.debtdetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.PaymentEntity
import com.dhanuk.debtbro.data.ads.AdManager
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.GroqRepository
import com.dhanuk.debtbro.data.repository.PaymentRepository
import com.dhanuk.debtbro.util.CanvasExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debtRepository: DebtRepository,
    private val paymentRepository: PaymentRepository,
    private val groqRepository: GroqRepository,
    private val prefs: AppPreferences,
    private val authManager: AuthManager,
    private val syncManager: SyncManager,
    private val adManager: AdManager
) : ViewModel() {

    private val debtId = checkNotNull(savedStateHandle.get<Int>("debtId"))

    val debt: StateFlow<DebtEntity?> = debtRepository.observeDebtById(debtId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val payments: StateFlow<List<PaymentEntity>> = debt.flatMapLatest { d ->
        if (d == null) flowOf(emptyList()) else paymentRepository.getPaymentsForDebt(d.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiMessage = MutableStateFlow("")
    val isGeneratingAi = MutableStateFlow(false)
    val roastLevel: StateFlow<String> = prefs.roastLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MEDIUM")

    val showAddPaymentSheet = MutableStateFlow(false)
    val showEditDebtSheet = MutableStateFlow(false)
    private val _showConfetti = MutableStateFlow(false)
    val showConfetti: StateFlow<Boolean> = _showConfetti.asStateFlow()

    fun addPayment(amount: Double, note: String) = viewModelScope.launch {
        paymentRepository.recordPayment(debtId, amount, note)
        val after = debtRepository.getDebtById(debtId)
        if (after?.status == "SETTLED") {
            _showConfetti.value = true
        }
        showAddPaymentSheet.value = false
        syncIfSignedIn()
    }

    fun deletePayment(paymentId: Int) = viewModelScope.launch {
        paymentRepository.deletePayment(paymentId)
        syncIfSignedIn()
    }

    private val _showRewardAd = MutableStateFlow(false)
    val showRewardAd: StateFlow<Boolean> = _showRewardAd.asStateFlow()

    private val _remainingFree = MutableStateFlow(groqRepository.remainingFreeRegenerations())
    val remainingFree: StateFlow<Int> = _remainingFree.asStateFlow()

    fun generateRoast(activity: android.app.Activity? = null) = viewModelScope.launch {
        val d = debt.value ?: return@launch

        // Check if user has free regenerations left
        if (!groqRepository.canRegenerate()) {
            // Show reward ad to earn more
            if (activity != null) {
                adManager.showRewardedAd(activity, onRewarded = {
                    groqRepository.resetRegenerationCount()
                    _remainingFree.value = groqRepository.remainingFreeRegenerations()
                    // Retry generation after reward
                    viewModelScope.launch { generateRoastInternal(d) }
                }, onFailed = {
                    _showRewardAd.value = true
                })
            } else {
                _showRewardAd.value = true
            }
            return@launch
        }

        generateRoastInternal(d)
    }

    fun dismissRewardAd() {
        _showRewardAd.value = false
    }

    fun dismissConfetti() {
        _showConfetti.value = false
    }

    private suspend fun generateRoastInternal(d: DebtEntity) {
        isGeneratingAi.value = true
        groqRepository.incrementRegenerationCount()
        _remainingFree.value = groqRepository.remainingFreeRegenerations()
        val message = groqRepository.generateRoast(d, roastLevel.value)
            .getOrElse { "Could not generate roast. Check your API key." }
        aiMessage.value = message
        debtRepository.updateRoast(d.id, message, System.currentTimeMillis())
        isGeneratingAi.value = false
        syncIfSignedIn()
    }

    fun setRoastLevel(level: String) = viewModelScope.launch {
        prefs.setRoastLevel(level)
    }

    fun markSettled() = viewModelScope.launch {
        debt.value?.let { d ->
            val remaining = d.amount - d.amountPaid
            if (remaining > 0) {
                paymentRepository.recordPayment(d.id, remaining, "Full settlement")
                _showConfetti.value = true
            }
        }
        syncIfSignedIn()
    }

    fun updateDebt(name: String, amount: Double, description: String, emoji: String) = viewModelScope.launch {
        debt.value?.let { d ->
            debtRepository.updateDebt(d.copy(
                personName = name,
                amount = amount,
                description = description,
                personEmoji = emoji
            ))
            showEditDebtSheet.value = false
        }
        syncIfSignedIn()
    }

    fun deleteDebt() = viewModelScope.launch {
        debt.value?.let { debtRepository.deleteDebt(it) }
    }

    fun shareCard(context: Context, debt: DebtEntity, message: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val bitmap = CanvasExporter.createDebtCard(context, debt, message, roastLevel.value)
            val uri = CanvasExporter.saveDebtCard(context, bitmap)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share DebtBro card").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }.onFailure {
            it.printStackTrace()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Failed to create image: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun shareCardToWhatsApp(context: Context, debt: DebtEntity, message: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val bitmap = CanvasExporter.createDebtCard(context, debt, message, roastLevel.value)
            val uri = CanvasExporter.saveDebtCard(context, bitmap)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }.onFailure {
            it.printStackTrace()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                com.dhanuk.debtbro.util.shareTextToWhatsApp(context, message)
            }
        }
    }

    private fun syncIfSignedIn() = viewModelScope.launch {
        authManager.getCurrentUser()?.uid?.let { uid ->
            runCatching { syncManager.mergePendingUnsynced(uid) }
        }
    }
}
