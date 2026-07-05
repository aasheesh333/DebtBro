package com.dhanuk.debtbro.presentation.screens.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.RealTimeSyncManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.worker.AccountDeletionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
    val isBusy: Boolean = false,
    val errorRes: String? = null
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val auth: AuthManager,
    private val prefs: AppPreferences,
    private val sync: SyncManager,
    private val realTimeSyncManager: RealTimeSyncManager
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _showVerifyAlert = MutableStateFlow(false)
    val showVerifyAlert: StateFlow<Boolean> = _showVerifyAlert.asStateFlow()

    private val _showGraceReLoginAlert = MutableStateFlow(false)
    val showGraceReLoginAlert: StateFlow<Boolean> = _showGraceReLoginAlert.asStateFlow()

    fun setName(value: String) {
        val capped = value.take(MAX_NAME_LENGTH)
        _state.value = _state.value.copy(name = capped, errorRes = null)
    }

    fun setEmail(value: String) {
        _state.value = _state.value.copy(email = value.trim(), errorRes = null)
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(
            password = value,
            passwordStrength = computePasswordStrength(value),
            errorRes = null
        )
    }

    fun setConfirmPassword(value: String) {
        _state.value = _state.value.copy(confirmPassword = value, errorRes = null)
    }

    fun signUpWithGoogle(activity: Activity) {
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signInWithGoogle(activity).fold(
                onSuccess = { user ->
                    _showVerifyAlert.value = false
                    onAuthSuccess(
                        uid = user.uid,
                        displayName = user.displayName ?: user.email?.substringBefore('@') ?: "DebtPayoff Pro user",
                        email = user.email ?: "",
                        photo = user.photoUrl?.toString().orEmpty(),
                        provider = "google"
                    )
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
        when {
            !isNameValid(current.name) -> _state.value = current.copy(errorRes = "name_required")
            !isEmailValid(current.email) -> _state.value = current.copy(errorRes = "email_required")
            current.password.length < MIN_PASSWORD_LENGTH -> _state.value = current.copy(errorRes = "password_6_chars_min")
            current.password != current.confirmPassword -> _state.value = current.copy(errorRes = "passwords_dont_match")
            else -> doSignUp()
        }
    }

    private fun doSignUp() {
        val current = _state.value
        _state.value = current.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signUpWithEmailPassword(current.email, current.password).fold(
                onSuccess = { user ->
                    runCatching { auth.updateProfile(displayName = current.name, photoUri = null) }
                    _showVerifyAlert.value = true
                    onAuthSuccess(
                        uid = user.uid,
                        displayName = current.name,
                        email = user.email ?: current.email,
                        photo = "",
                        provider = "email"
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message ?: "Sign-up failed")
                }
            )
        }
    }

    private suspend fun onAuthSuccess(uid: String?, displayName: String, email: String, photo: String, provider: String) {
        if (uid == null) {
            _state.value = _state.value.copy(isBusy = false, errorRes = "auth_no_uid_returned")
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
            name = "",
            email = "",
            password = "",
            confirmPassword = "",
            passwordStrength = PasswordStrength.WEAK,
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

    fun dismissVerifyAlert() { _showVerifyAlert.value = false }

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
}
