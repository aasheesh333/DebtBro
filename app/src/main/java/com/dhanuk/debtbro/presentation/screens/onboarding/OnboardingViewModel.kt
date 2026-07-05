package com.dhanuk.debtbro.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.util.LocalizedString
import com.dhanuk.debtbro.worker.AccountDeletionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val auth: AuthManager
) : ViewModel() {
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isSignInMode = MutableStateFlow(false)
    val isSignInMode: StateFlow<Boolean> = _isSignInMode.asStateFlow()

    private val _showEmailForm = MutableStateFlow(false)
    val showEmailForm: StateFlow<Boolean> = _showEmailForm.asStateFlow()

    private val _showForgotPasswordDialog = MutableStateFlow(false)
    val showForgotPasswordDialog: StateFlow<Boolean> = _showForgotPasswordDialog.asStateFlow()

    private val _forgotPasswordError = MutableStateFlow<String?>(null)
    val forgotPasswordError: StateFlow<String?> = _forgotPasswordError.asStateFlow()

    private val _verifyGateVisible = MutableStateFlow(false)
    val verifyGateVisible: StateFlow<Boolean> = _verifyGateVisible.asStateFlow()

    private val _showGraceReLoginAlert = MutableStateFlow(false)
    val showGraceReLoginAlert: StateFlow<Boolean> = _showGraceReLoginAlert.asStateFlow()

    fun onNameChange(name: String) { _userName.value = name }

    fun setLanguage(code: String) {
        _selectedLanguage.value = code
        viewModelScope.launch {
            prefs.setLanguage(code)
        }
    }

    fun toggleAuthMode() { _isSignInMode.value = !_isSignInMode.value }

    fun toggleEmailForm() {
        if (!_showEmailForm.value) {
            _showEmailForm.value = true
            _isSignInMode.value = false
        } else {
            _isSignInMode.value = !_isSignInMode.value
        }
    }

    fun showForgotPasswordDialog() {
        _showForgotPasswordDialog.value = true
        _forgotPasswordError.value = null
    }

    fun dismissForgotPasswordDialog() {
        _showForgotPasswordDialog.value = false
        _forgotPasswordError.value = null
    }

    fun sendPasswordResetEmail(email: String) {
        if (email.isBlank()) {
            _forgotPasswordError.value = LocalizedString.get("email") + " required"
            return
        }
        viewModelScope.launch {
            runCatching { auth.sendPasswordResetEmail(email) }
                .onSuccess {
                    _forgotPasswordError.value = null
                    _showForgotPasswordDialog.value = false
                }
                .onFailure { _forgotPasswordError.value = it.message ?: "Could not send reset email" }
        }
    }

    fun signInWithGoogle(activity: android.app.Activity, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val result = auth.signInWithGoogle(activity)
        if (result.isSuccess) {
            val user = result.getOrThrow()
            prefs.setSignedIn("google", user.displayName ?: _userName.value.ifBlank { "DebtPayoff Pro user" }, user.email ?: "", user.photoUrl?.toString() ?: "")
            _verifyGateVisible.value = false
            checkGracePeriodOnSignIn(user.uid)
            onResult(true)
        } else {
            _authError.value = result.exceptionOrNull()?.message ?: "Sign-in failed"
            onResult(false)
        }
    }

    fun signUpEmailPassword(email: String, password: String, name: String, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val result = auth.signUpWithEmailPassword(email, password)
        if (result.isSuccess) {
            val user = result.getOrThrow()
            val displayName = name.ifBlank { _userName.value.ifBlank { "DebtPayoff Pro user" } }
            prefs.setSignedIn("email", displayName, user.email ?: email, "")
            if (_userName.value.isBlank() && name.isNotBlank()) _userName.value = name
            _verifyGateVisible.value = true
            onResult(true, null)
        } else {
            val msg = result.exceptionOrNull()?.message ?: "Sign-up failed"
            _authError.value = msg
            onResult(false, msg)
        }
    }

    fun resendVerificationEmail() = viewModelScope.launch {
        auth.resendVerificationEmail()
    }

    fun refreshVerificationState() = viewModelScope.launch {
        if (auth.reloadCurrentUser()) {
            if (auth.isGoogleProvider() || auth.isCurrentUserEmailVerified()) {
                _verifyGateVisible.value = false
            }
        }
    }

    fun signInEmailPassword(email: String, password: String, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val result = auth.signInWithEmailPassword(email, password)
        if (result.isSuccess) {
            val user = result.getOrThrow()
            prefs.setSignedIn("email", user.displayName ?: _userName.value.ifBlank { "DebtPayoff Pro user" }, user.email ?: email, "")
            checkGracePeriodOnSignIn(user.uid)
            onResult(true, null)
        } else {
            val msg = result.exceptionOrNull()?.message ?: "Sign-in failed"
            _authError.value = msg
            onResult(false, msg)
        }
    }

    private suspend fun checkGracePeriodOnSignIn(uid: String?) {
        if (uid == null) return
        val serverRequestedAt = runCatching { auth.checkDeletionRequest(uid) }.getOrNull()
        val effectiveTs = serverRequestedAt ?: prefs.pendingDeletionTimestamp.first().takeIf { it > 0L }
        if (effectiveTs != null && effectiveTs > 0L) {
            val elapsed = System.currentTimeMillis() - effectiveTs
            if (elapsed < GRACE_PERIOD_MS) {
                prefs.setPendingDeletionTimestamp(effectiveTs)
                _showGraceReLoginAlert.value = true
            }
        }
    }

    fun dismissGraceReLoginAlert() { _showGraceReLoginAlert.value = false }

    fun cancelDeletionViaGraceAlert() = viewModelScope.launch {
        val uid = auth.getUserId()
        if (uid != null) {
            runCatching { auth.cancelAccountDeletion(uid, System.currentTimeMillis()) }
        }
        prefs.clearPendingDeletion()
        _showGraceReLoginAlert.value = false
    }

    fun signOutFromGraceAlert() = viewModelScope.launch {
        auth.signOut()
        prefs.setSignedIn(null)
        _showGraceReLoginAlert.value = false
    }

    companion object {
        private const val GRACE_PERIOD_MS = AccountDeletionWorker.GRACE_PERIOD_MS
    }

    fun dismissAuthError() { _authError.value = null }

    fun completeOnboarding(onDone: () -> Unit) = viewModelScope.launch {
        prefs.setOnboardingComplete(_userName.value)
        onDone()
    }
}
