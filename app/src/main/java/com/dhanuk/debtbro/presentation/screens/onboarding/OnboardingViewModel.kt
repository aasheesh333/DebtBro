package com.dhanuk.debtbro.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.util.LocalizedString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            onResult(true, null)
        } else {
            val msg = result.exceptionOrNull()?.message ?: "Sign-up failed"
            _authError.value = msg
            onResult(false, msg)
        }
    }

    fun signInEmailPassword(email: String, password: String, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val result = auth.signInWithEmailPassword(email, password)
        if (result.isSuccess) {
            val user = result.getOrThrow()
            prefs.setSignedIn("email", user.displayName ?: _userName.value.ifBlank { "DebtPayoff Pro user" }, user.email ?: email, "")
            onResult(true, null)
        } else {
            val msg = result.exceptionOrNull()?.message ?: "Sign-in failed"
            _authError.value = msg
            onResult(false, msg)
        }
    }

    fun dismissAuthError() { _authError.value = null }

    fun completeOnboarding(onDone: () -> Unit) = viewModelScope.launch {
        prefs.setOnboardingComplete(_userName.value)
        onDone()
    }
}
