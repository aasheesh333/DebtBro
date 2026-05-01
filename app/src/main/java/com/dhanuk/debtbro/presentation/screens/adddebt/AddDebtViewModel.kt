package com.dhanuk.debtbro.presentation.screens.adddebt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddDebtUiState(
    val personName: String = "",
    val personEmoji: String = "🙂",
    val amount: String = "",
    val type: String = "THEY_OWE_ME",
    val description: String = "",
    val currency: String = "₹",
    val notes: String = "",
    val dueDateDays: String = "",
    val error: String? = null
)

@HiltViewModel
class AddDebtViewModel @Inject constructor(
    private val debtRepository: DebtRepository,
    private val prefs: AppPreferences,
    private val authManager: AuthManager,
    private val syncManager: SyncManager
) : ViewModel() {
    private val _state = MutableStateFlow(AddDebtUiState())
    val state: StateFlow<AddDebtUiState> = _state.asStateFlow()
    init { viewModelScope.launch { _state.value = _state.value.copy(currency = prefs.defaultCurrency.first()) } }
    fun update(transform: AddDebtUiState.() -> AddDebtUiState) { _state.value = _state.value.transform() }
    fun saveDebt(onSaved: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        val amount = s.amount.toDoubleOrNull()
        if (s.personName.isBlank() || amount == null || amount <= 0.0) {
            _state.value = s.copy(error = "Enter a name and a valid amount")
            return@launch
        }
        val dueDate = s.dueDateDays.toIntOrNull()?.let { System.currentTimeMillis() + it * 86400000L }
        val id = debtRepository.insertDebt(DebtEntity(personName = s.personName.trim(), personEmoji = s.personEmoji, amount = amount, currency = s.currency, description = s.description.trim(), type = s.type, notes = s.notes.ifBlank { null }, dueDate = dueDate))
        authManager.getCurrentUser()?.uid?.let { uid -> syncManager.syncSingleDebt(uid, debtRepository.getDebtById(id.toInt()) ?: return@let) }
        onSaved()
    }
}
