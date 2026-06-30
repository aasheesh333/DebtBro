package com.dhanuk.debtbro.presentation.screens.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.RealTimeSyncManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT_PASSWORD }

enum class PasswordStrength { WEAK, MEDIUM, STRONG }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isBusy: Boolean = false,
    val errorRes: String? = null,
    val resetLinkSent: Boolean = false,
    val resendCountdownSeconds: Int = 0,
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthManager,
    private val prefs: AppPreferences,
    private val sync: SyncManager,
    private val realTimeSyncManager: RealTimeSyncManager
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.isGoogleSignedIn.collect { _signedIn.value = it }
        }
    }

    private var countdownJob: Job? = null

    fun setEmail(value: String) {
        _state.value = _state.value.copy(email = value.trim(), errorRes = null)
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(
            password = value,
            passwordStrength = computeStrength(value),
            errorRes = null
        )
    }

    fun setConfirmPassword(value: String) {
        _state.value = _state.value.copy(confirmPassword = value, errorRes = null)
    }

    fun setMode(mode: AuthMode) {
        countdownJob?.cancel()
        _state.value = _state.value.copy(
            mode = mode,
            errorRes = null,
            resendCountdownSeconds = 0,
            resetLinkSent = false
        )
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorRes = null)
    }

    private fun isEmailValid(s: String): Boolean =
        s.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()

    private fun computeStrength(pwd: String): PasswordStrength {
        if (pwd.length < 6) return PasswordStrength.WEAK
        val hasLetter = pwd.any { it.isLetter() }
        val hasDigit = pwd.any { it.isDigit() }
        val hasSymbol = pwd.any { !it.isLetterOrDigit() }
        return when {
            pwd.length >= 10 && hasLetter && hasDigit && hasSymbol -> PasswordStrength.STRONG
            pwd.length >= 8 && ((hasLetter && hasDigit) || hasSymbol) -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }
    }

    fun signInWithGoogle(activity: Activity) {
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signInWithGoogle(activity).fold(
                onSuccess = { user -> onAuthSuccess(user.uid, user.displayName, user.email, user.photoUrl?.toString().orEmpty()) },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message)
                }
            )
        }
    }

    fun submitEmailPassword() {
        val current = _state.value
        if (current.isBusy) return
        if (!isEmailValid(current.email)) {
            _state.value = current.copy(errorRes = "email_required")
            return
        }
        when (current.mode) {
            AuthMode.SIGN_IN -> doSignIn()
            AuthMode.SIGN_UP -> doSignUp()
            AuthMode.FORGOT_PASSWORD -> sendResetEmail()
        }
    }

    private fun doSignIn() {
        val current = _state.value
        if (current.password.length < 6) {
            _state.value = current.copy(errorRes = "password_6_chars_min")
            return
        }
        _state.value = current.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signInWithEmailPassword(current.email, current.password).fold(
                onSuccess = { user -> onAuthSuccess(user.uid, user.displayName ?: "", user.email ?: "", "") },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message ?: "Sign-in failed")
                }
            )
        }
    }

    private fun doSignUp() {
        val current = _state.value
        if (current.password.length < 6) {
            _state.value = current.copy(errorRes = "password_6_chars_min")
            return
        }
        if (current.password != current.confirmPassword) {
            _state.value = current.copy(errorRes = "passwords_dont_match")
            return
        }
        _state.value = current.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signUpWithEmailPassword(current.email, current.password).fold(
                onSuccess = { user -> onAuthSuccess(user.uid, user.displayName ?: "", user.email ?: "", "") },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message ?: "Sign-up failed")
                }
            )
        }
    }

    private fun sendResetEmail() {
        val current = _state.value
        if (!isEmailValid(current.email)) {
            _state.value = current.copy(errorRes = "email_required")
            return
        }
        viewModelScope.launch {
            val count = prefs.getForgotPasswordDailyCount()
            if (count >= MAX_DAILY_RESETS) {
                _state.value = _state.value.copy(errorRes = "daily_limit_reached")
                return@launch
            }
            val now = System.currentTimeMillis()
            val lastSent = prefs.forgotPasswordLastSent.first()
            val secondsSinceLastSend = if (lastSent == 0L) RESEND_COOLDOWN_SECONDS.toLong() else (now - lastSent) / 1000
            if (lastSent > 0 && secondsSinceLastSend < RESEND_COOLDOWN_SECONDS) {
                val remaining = (RESEND_COOLDOWN_SECONDS - secondsSinceLastSend).toInt().coerceAtLeast(1)
                startCountdown(remaining)
                return@launch
            }
            _state.value = current.copy(isBusy = true, errorRes = null)
            auth.sendPasswordResetEmail(current.email).fold(
                onSuccess = {
                    prefs.setForgotPasswordLastSent(System.currentTimeMillis())
                    val newCount = prefs.getForgotPasswordDailyCount() + 1
                    prefs.setForgotPasswordDailyCount(newCount)
                    _state.value = _state.value.copy(
                        isBusy = false,
                        resetLinkSent = true,
                        resendCountdownSeconds = RESEND_COOLDOWN_SECONDS
                    )
                    startCountdown(RESEND_COOLDOWN_SECONDS)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message ?: "Could not send reset email")
                }
            )
        }
    }

    private fun startCountdown(from: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = from
            while (remaining > 0) {
                _state.value = _state.value.copy(resendCountdownSeconds = remaining)
                delay(1000)
                remaining -= 1
            }
            _state.value = _state.value.copy(resendCountdownSeconds = 0)
        }
    }

    suspend fun dailyAttemptsRemaining(): Int {
        val count = prefs.getForgotPasswordDailyCount()
        return (MAX_DAILY_RESETS - count).coerceAtLeast(0)
    }

    private suspend fun onAuthSuccess(uid: String?, name: String?, email: String?, photo: String) {
        if (uid == null) {
            _state.value = _state.value.copy(isBusy = false, errorRes = "Auth succeeded but no UID returned")
            return
        }
        prefs.setGoogleSignedIn(true, name ?: "DebtBro user", email ?: "", photo)
        runCatching {
            realTimeSyncManager.startListening(uid)
            sync.fullSync(uid)
        }
        _state.value = _state.value.copy(isBusy = false)
    }

    companion object {
        const val MAX_DAILY_RESETS = 5
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}
