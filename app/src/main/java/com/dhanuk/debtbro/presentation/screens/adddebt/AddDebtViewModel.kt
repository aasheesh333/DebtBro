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

@HiltViewModel
class AddDebtViewModel @Inject constructor(
    private val debtRepository: DebtRepository,
    private val prefs: AppPreferences,
    private val authManager: AuthManager,
    private val syncManager: SyncManager
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun saveDebt(
        personName: String,
        personEmoji: String,
        amount: Double,
        currency: String,
        type: String,
        description: String,
        dueDate: Long?,
        notes: String,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val id = debtRepository.insertDebt(
                    DebtEntity(
                        personName = personName,
                        personEmoji = personEmoji,
                        amount = amount,
                        currency = currency,
                        type = type,
                        description = description,
                        dueDate = dueDate,
                        notes = notes.ifBlank { null }
                    )
                )
                
                // Sync with Firebase if user is logged in
                authManager.getCurrentUser()?.uid?.let { uid ->
                    val debt = debtRepository.getDebtById(id.toInt())
                    if (debt != null) {
                        runCatching { syncManager.pushNewDebt(uid, debt) }
                    }
                }
                
                onSaved()
            } catch (e: Exception) {
                android.util.Log.e("AddDebtViewModel", "Failed to save debt: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    suspend fun getDefaultCurrency(): String {
        return prefs.defaultCurrency.first()
    }
}
