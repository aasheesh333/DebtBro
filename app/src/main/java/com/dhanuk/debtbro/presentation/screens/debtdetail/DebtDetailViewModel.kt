package com.dhanuk.debtbro.presentation.screens.debtdetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.PaymentEntity
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.GroqRepository
import com.dhanuk.debtbro.data.repository.PaymentRepository
import com.dhanuk.debtbro.util.CanvasExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debtRepository: DebtRepository,
    private val paymentRepository: PaymentRepository,
    private val groqRepository: GroqRepository,
    prefs: AppPreferences
) : ViewModel() {
    private val debtId = checkNotNull(savedStateHandle.get<Int>("debtId"))
    val debt: StateFlow<DebtEntity?> = debtRepository.observeDebtById(debtId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val payments: StateFlow<List<PaymentEntity>> = debt.flatMapLatest { if (it == null) flowOf(emptyList()) else paymentRepository.getPaymentsForDebt(it.id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val aiMessage = MutableStateFlow("")
    val isGeneratingAi = MutableStateFlow(false)
    val roastLevel: StateFlow<String> = prefs.roastLevel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MEDIUM")
    val showAddPaymentSheet = MutableStateFlow(false)
    val settledCountInSession = MutableStateFlow(0)
    private val _showConfetti = MutableStateFlow(false)
    val showConfetti: StateFlow<Boolean> = _showConfetti.asStateFlow()
    fun addPayment(amount: Double, note: String) = viewModelScope.launch {
        val before = debt.value
        paymentRepository.recordPayment(debtId, amount, note)
        val after = debtRepository.getDebtById(debtId)
        if (before?.status != "SETTLED" && after?.status == "SETTLED") settledCountInSession.value += 1
        showAddPaymentSheet.value = false
    }
    fun generateRoast() = viewModelScope.launch {
        val d = debt.value ?: return@launch
        isGeneratingAi.value = true
        val message = groqRepository.generateRoast(d, roastLevel.value).getOrElse { "No API key yet. Add one in Settings and BroBot will start roasting responsibly." }
        aiMessage.value = message
        debtRepository.updateRoast(d.id, message, System.currentTimeMillis())
        isGeneratingAi.value = false
    }
    fun markSettled() = viewModelScope.launch {
        debt.value?.let {
            debtRepository.updateDebt(it.copy(amountPaid = it.amount, status = "SETTLED"))
            _showConfetti.value = true
        }
    }
    fun shareCard(context: Context, debt: DebtEntity, message: String) = CanvasExporter.shareDebtCard(context, CanvasExporter.createDebtCard(context, debt, message))
}
