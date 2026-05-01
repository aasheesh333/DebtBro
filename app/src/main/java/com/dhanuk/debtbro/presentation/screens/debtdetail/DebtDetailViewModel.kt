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
    private val prefs: AppPreferences
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
    }

    fun deletePayment(paymentId: Int) = viewModelScope.launch {
        paymentRepository.deletePayment(paymentId)
    }

    fun generateRoast() = viewModelScope.launch {
        val d = debt.value ?: return@launch
        isGeneratingAi.value = true
        val message = groqRepository.generateRoast(d, roastLevel.value)
            .getOrElse { "Could not generate roast. Check your API key." }
        aiMessage.value = message
        debtRepository.updateRoast(d.id, message, System.currentTimeMillis())
        isGeneratingAi.value = false
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
    }

    fun deleteDebt() = viewModelScope.launch {
        debt.value?.let {
            debtRepository.deleteDebt(it)
        }
    }

    fun shareCard(context: Context, debt: DebtEntity, message: String) {
        val bitmap = CanvasExporter.createDebtCard(context, debt, message)
        CanvasExporter.shareDebtCard(context, bitmap)
    }
}
