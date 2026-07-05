package com.dhanuk.debtbro.presentation.screens.adddebt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.ads.AdManager
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddDebtViewModel @Inject constructor(
    private val debtRepository: DebtRepository,
    private val prefs: AppPreferences,
    private val authManager: AuthManager,
    private val syncManager: SyncManager,
    private val adManager: AdManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showAuthPrompt = MutableStateFlow(false)
    val showAuthPrompt: StateFlow<Boolean> = _showAuthPrompt.asStateFlow()

    private val _showVerifyGate = MutableStateFlow(false)
    val showVerifyGate: StateFlow<Boolean> = _showVerifyGate.asStateFlow()

    private val _showInterstitial = MutableSharedFlow<Unit>()
    val showInterstitial: SharedFlow<Unit> = _showInterstitial.asSharedFlow()

    fun dismissAuthPrompt() {
        _showAuthPrompt.value = false
    }

    fun dismissVerifyGate() {
        _showVerifyGate.value = false
    }

    fun resendVerificationEmail() = viewModelScope.launch {
        authManager.resendVerificationEmail()
    }

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
            if (!authManager.isGoogleProvider() && !authManager.isCurrentUserEmailVerified()) {
                _showVerifyGate.value = true
                return@launch
            }
            _isLoading.value = true
            try {
                // Local-first: always persist to Room so offline users can use
                // the app. Cloud sync only fires if a Firebase user actually
                // exists (independent of the prefs.isGoogleSignedIn flag, which
                // historically was set for ALL providers incl. email/password
                // — not a reliable gate for "should we sync to the cloud?" and
                // its blocking use blocked onboarding-skip users from any
                // primary action. See offline-mode audit 2026-07-03.)
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

                // Sync with Firebase only if the user is actually signed in —
                // failure here MUST NOT roll back the local insert (offline
                // still wins).
                authManager.getCurrentUser()?.uid?.let { uid ->
                    val debt = debtRepository.getDebtById(id.toInt())
                    if (debt != null) {
                        runCatching { syncManager.pushNewDebt(uid, debt) }
                    }
                }

                onSaved()
                emitShowInterstitial()
            } catch (e: Exception) {
                android.util.Log.e("AddDebtViewModel", "Failed to save debt: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun emitShowInterstitial() = viewModelScope.launch {
        _showInterstitial.emit(Unit)
    }

    fun showInterstitialIfReady(activity: android.app.Activity): Boolean {
        return adManager.showInterstitialIfReady(activity, onDismissed = { /* AdManager already pre-loads the next ad */ })
    }

    suspend fun getDefaultCurrency(): String {
        return prefs.defaultCurrency.first()
    }
}
