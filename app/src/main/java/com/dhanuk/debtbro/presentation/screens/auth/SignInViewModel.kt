package com.dhanuk.debtbro.presentation.screens.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.RealTimeSyncManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.util.LocalizedString
import com.dhanuk.debtbro.worker.AccountDeletionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInMode { SIGN_IN, FORGOT_PASSWORD }

data class SignInUiState(
    val mode: SignInMode = SignInMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val isBusy: Boolean = false,
    val errorRes: String? = null,
    val resetLinkSent: Boolean = false,
    val resendCountdownSeconds: Int = 0
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthManager,
    private val prefs: AppPreferences,
    private val sync: SyncManager,
    private val realTimeSyncManager: RealTimeSyncManager
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _showGraceReLoginAlert = MutableStateFlow(false)
    val showGraceReLoginAlert: StateFlow<Boolean> = _showGraceReLoginAlert.asStateFlow()

    private var countdownJob: Job? = null

    fun setEmail(value: String) {
        _state.value = _state.value.copy(email = value.trim(), errorRes = null)
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(password = value, errorRes = null)
    }

    fun setMode(mode: SignInMode) {
        countdownJob?.cancel()
        _state.value = _state.value.copy(
            mode = mode,
            errorRes = null,
            resendCountdownSeconds = 0,
            resetLinkSent = false
        )
    }

    fun signInWithGoogle(activity: Activity) {
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signInWithGoogle(activity).fold(
                onSuccess = { user ->
                    onAuthSuccess(user.uid, user.displayName ?: "DebtPayoff Pro user", user.email ?: "", user.photoUrl?.toString().orEmpty(), "google")
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message)
                }
            )
        }
    }

    fun submit() {
        val current = _state.value
        if (current.isBusy) return
        when (current.mode) {
            SignInMode.SIGN_IN -> doSignIn()
            SignInMode.FORGOT_PASSWORD -> sendResetEmail()
        }
    }

    private fun doSignIn() {
        val current = _state.value
        if (current.password.length < MIN_PASSWORD_LENGTH) {
            _state.value = current.copy(errorRes = "password_6_chars_min")
            return
        }
        if (!isEmailValid(current.email)) {
            _state.value = current.copy(errorRes = "email_required")
            return
        }
        _state.value = current.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signInWithEmailPassword(current.email, current.password).fold(
                onSuccess = { user -> onAuthSuccess(user.uid, user.displayName ?: "DebtPayoff Pro user", user.email ?: "", "", "email") },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message ?: "Sign-in failed")
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
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message ?: LocalizedString.get("could_not_send_reset_email"))
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

    private suspend fun onAuthSuccess(uid: String?, displayName: String, email: String, photo: String, provider: String) {
        if (uid == null) {
            _state.value = _state.value.copy(isBusy = false, errorRes = LocalizedString.get("auth_no_uid_returned"))
            return
        }
        prefs.setSignedIn(provider, displayName, email, photo)
        runCatching {
            realTimeSyncManager.startListening(uid)
            sync.fullSync(uid)
        }
        runCatching { checkGracePeriodOnSignIn(uid) }
        _state.value = _state.value.copy(
            isBusy = false,
            email = "",
            password = "",
            errorRes = null
        )
        if (!_showGraceReLoginAlert.value) _signedIn.value = true
    }

    private suspend fun checkGracePeriodOnSignIn(uid: String) {
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
        _signedIn.value = true
    }

    fun signOutFromGraceAlert() = viewModelScope.launch {
        auth.signOut()
        prefs.setSignedIn(null)
        _showGraceReLoginAlert.value = false
    }

    companion object {
        const val MAX_DAILY_RESETS = 5
        const val RESEND_COOLDOWN_SECONDS = 60
        private const val GRACE_PERIOD_MS = AccountDeletionWorker.GRACE_PERIOD_MS
    }
}
