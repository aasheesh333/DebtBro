package com.dhanuk.debtbro.presentation.screens.settings

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.datastore.SecureStorage
import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.db.dao.PaymentDao
import com.dhanuk.debtbro.data.db.dao.SplitDao
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.FirebaseRepository
import com.dhanuk.debtbro.data.firebase.RealTimeSyncManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.AiRepository
import com.dhanuk.debtbro.util.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountSettingsState(
    val userName: String = "",
    val roastLevel: String = "MEDIUM",
    val currency: String = "₹",
    val customAvatarUri: String = "",
    val selectedLanguage: String = "en",
    val exportFormat: String = "CSV",
    val themeMode: String = "SYSTEM"
)

data class GoogleSignInState(
    val isSignedIn: Boolean = false,
    val googleName: String = "",
    val email: String = "",
    val userPhoto: String = ""
)

data class DisplaySettingsState(
    val showDescription: Boolean = true,
    val showDueDate: Boolean = true,
    val showEmoji: Boolean = true
)

data class NotificationSettingsState(
    val notifyDailyReminder: Boolean = true,
    val notifyWeeklySummary: Boolean = true,
    val notifyPaymentAlerts: Boolean = true
)

private data class SyncStateValue(
    val lastSynced: Long = 0L,
    val isSyncing: Boolean = false,
    val syncMessage: String = ""
)

private data class DeletionStateValue(
    val signingOut: Boolean = false,
    val deleting: Boolean = false,
    val pendingTs: Long = 0L
)

data class SettingsUiState(
    val userName: String = "",
    val roastLevel: String = "MEDIUM",
    val currency: String = "₹",
    val isSignedIn: Boolean = false,
    val googleName: String = "",
    val email: String = "",
    val userPhoto: String = "",
    val customAvatarUri: String = "",
    val lastSynced: Long = 0L,
    val selectedLanguage: String = "en",
    val testResult: String = "",
    val isSyncing: Boolean = false,
    val syncMessage: String = "",
    val showDescription: Boolean = true,
    val showDueDate: Boolean = true,
    val showEmoji: Boolean = true,
    val linkedProviders: List<String> = emptyList(),
    val notifyDailyReminder: Boolean = true,
    val notifyWeeklySummary: Boolean = true,
    val notifyPaymentAlerts: Boolean = true,
    val exportFormat: String = "CSV",
    val themeMode: String = "SYSTEM",
    val isSigningOut: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val pendingDeletionTimestamp: Long = 0L,
    val geminiApiKey: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val secureStorage: SecureStorage,
    private val auth: AuthManager,
    private val sync: SyncManager,
    private val realTimeSyncManager: RealTimeSyncManager,
    private val firebaseRepository: FirebaseRepository,
    private val debts: DebtRepository,
    private val ai: AiRepository,
    private val debtDao: DebtDao,
    private val paymentDao: PaymentDao,
    private val splitDao: SplitDao
) : ViewModel() {
    private val isSyncing = MutableStateFlow(false)
    private val syncMessage = MutableStateFlow("")
    private val isSigningOut = MutableStateFlow(false)
    private val isDeletingAccount = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val ts = prefs.pendingDeletionTimestamp.first()
            if (ts > 0L && (System.currentTimeMillis() - ts) >= GRACE_PERIOD_MS) {
                commitAccountDeletion()
            }
        }
    }

    private suspend fun commitAccountDeletion() {
        val userId = auth.getUserId()
        if (userId != null) {
            try {
                firebaseRepository.deleteAllUserData(userId)
                auth.deleteAccount()
            } catch (_: Exception) { }
        }
        prefs.clearPendingDeletion()
        prefs.clearAll()
        realTimeSyncManager.stopListening()
        debts.clearLocalData()
    }

    private val accountSettingsState = combine(
        prefs.userName,
        prefs.roastLevel,
        prefs.defaultCurrency,
        prefs.customAvatarUri,
        prefs.selectedLanguage
    ) { userName, roastLevel, currency, customAvatarUri, selectedLanguage ->
        AccountSettingsState(
            userName = userName,
            roastLevel = roastLevel,
            currency = currency,
            customAvatarUri = customAvatarUri,
            selectedLanguage = selectedLanguage,
            exportFormat = "",
            themeMode = ""
        )
    }

    private val accountSettingsStateFull = combine(
        accountSettingsState,
        prefs.exportFormat,
        prefs.themeMode
    ) { account, exportFormat, themeMode ->
        account.copy(exportFormat = exportFormat, themeMode = themeMode)
    }

    private val googleSignInState = combine(
        prefs.isGoogleSignedIn,
        prefs.googleUserName,
        prefs.googleUserEmail,
        prefs.googleUserPhoto
    ) { isSignedIn, googleName, email, userPhoto ->
        GoogleSignInState(
            isSignedIn = isSignedIn,
            googleName = googleName,
            email = email,
            userPhoto = userPhoto
        )
    }

    private val displaySettingsState = combine(
        prefs.showDescription,
        prefs.showDueDate,
        prefs.showEmoji
    ) { showDescription, showDueDate, showEmoji ->
        DisplaySettingsState(
            showDescription = showDescription,
            showDueDate = showDueDate,
            showEmoji = showEmoji
        )
    }

    private val notificationSettingsState = combine(
        prefs.notifyDailyReminder,
        prefs.notifyWeeklySummary,
        prefs.notifyPaymentAlerts
    ) { daily, weekly, payment ->
        NotificationSettingsState(
            notifyDailyReminder = daily,
            notifyWeeklySummary = weekly,
            notifyPaymentAlerts = payment
        )
    }

    private val syncState = combine(
        prefs.lastSyncedAt,
        isSyncing,
        syncMessage
    ) { lastSynced, syncing, syncMsg ->
        SyncStateValue(lastSynced, syncing, syncMsg)
    }

    private val deletionState = combine(
        isSigningOut,
        isDeletingAccount,
        prefs.pendingDeletionTimestamp
    ) { signingOut, deleting, pendingTs ->
        DeletionStateValue(signingOut, deleting, pendingTs)
    }

    val state: StateFlow<SettingsUiState> = combine(
        accountSettingsStateFull,
        googleSignInState,
        displaySettingsState,
        notificationSettingsState,
        syncState
    ) { account, google, display, notification, sync ->
        Pair(account, google) to Pair(Pair(display, notification), sync)
    }.combine(deletionState) { pairs, deletion ->
        val (accountGoogle, displayNotifSync) = pairs
        val (account, google) = accountGoogle
        val (displayNotif, sync) = displayNotifSync
        val (display, notification) = displayNotif
        SettingsUiState(
            userName = account.userName,
            roastLevel = account.roastLevel,
            currency = account.currency,
            customAvatarUri = account.customAvatarUri,
            selectedLanguage = account.selectedLanguage,
            exportFormat = account.exportFormat,
            themeMode = account.themeMode,
            isSignedIn = google.isSignedIn,
            googleName = google.googleName,
            email = google.email,
            userPhoto = google.userPhoto,
            showDescription = display.showDescription,
            showDueDate = display.showDueDate,
            showEmoji = display.showEmoji,
            notifyDailyReminder = notification.notifyDailyReminder,
            notifyWeeklySummary = notification.notifyWeeklySummary,
            notifyPaymentAlerts = notification.notifyPaymentAlerts,
            lastSynced = sync.lastSynced,
            isSyncing = sync.isSyncing,
            syncMessage = sync.syncMessage,
            isSigningOut = deletion.signingOut,
            isDeletingAccount = deletion.deleting,
            pendingDeletionTimestamp = deletion.pendingTs,
            linkedProviders = auth.linkedProviders()
        )
    }.combine(prefs.geminiApiKey) { ui, key -> ui.copy(geminiApiKey = key) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun saveUserName(name: String) = viewModelScope.launch { prefs.saveUserName(name) }
    fun setRoastLevel(level: String) = viewModelScope.launch { prefs.setRoastLevel(level) }
    fun setCurrency(c: String) = viewModelScope.launch { prefs.setCurrency(c) }
    fun setLanguage(code: String) = viewModelScope.launch { prefs.setLanguage(code) }
    fun setShowDescription(value: Boolean) = viewModelScope.launch { prefs.setShowDescription(value) }
    fun setShowDueDate(value: Boolean) = viewModelScope.launch { prefs.setShowDueDate(value) }
    fun setShowEmoji(value: Boolean) = viewModelScope.launch { prefs.setShowEmoji(value) }
    fun setNotifyDailyReminder(value: Boolean) = viewModelScope.launch { prefs.setNotifyDailyReminder(value) }
    fun setNotifyWeeklySummary(value: Boolean) = viewModelScope.launch { prefs.setNotifyWeeklySummary(value) }
    fun setNotifyPaymentAlerts(value: Boolean) = viewModelScope.launch { prefs.setNotifyPaymentAlerts(value) }
    fun setExportFormat(format: String) = viewModelScope.launch { prefs.setExportFormat(format) }
    fun setThemeMode(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setCustomAvatarUri(uri: String) = viewModelScope.launch { prefs.setCustomAvatarUri(uri) }
    fun saveGeminiKey(key: String) = viewModelScope.launch { prefs.saveGeminiKey(key) }

    private val _showDeletionGraceAlert = MutableStateFlow(false)
    val showDeletionGraceAlert: StateFlow<Boolean> = _showDeletionGraceAlert.asStateFlow()

    fun signInWithGoogle(activity: Activity) = viewModelScope.launch {
        val pendingTs = prefs.pendingDeletionTimestamp.first()
        if (pendingTs > 0) {
            val elapsed = System.currentTimeMillis() - pendingTs
            if (elapsed < GRACE_PERIOD_MS) {
                _showDeletionGraceAlert.value = true
                return@launch
            } else {
                prefs.clearPendingDeletion()
            }
        }
        auth.signInWithGoogle(activity).onSuccess { user ->
            prefs.setGoogleSignedIn(true, user.displayName ?: "DebtBro user", user.email ?: "", user.photoUrl?.toString().orEmpty())
            user.uid?.let { uid ->
                isSyncing.value = true
                syncMessage.value = "Syncing your data..."
                try {
                    realTimeSyncManager.startListening(uid)
                    sync.fullSync(uid)
                    syncMessage.value = "Sync complete"
                } catch (e: Exception) {
                    syncMessage.value = "Sync failed: ${e.message}"
                } finally {
                    isSyncing.value = false
                }
            }
        }
    }

    fun dismissDeletionGraceAlert() { _showDeletionGraceAlert.value = false }

    fun cancelDeletion() = viewModelScope.launch {
        prefs.clearPendingDeletion()
        _showDeletionGraceAlert.value = false
    }

    fun signOut() = viewModelScope.launch {
        isSigningOut.value = true
        try {
            realTimeSyncManager.stopListening()
            auth.signOut()
            prefs.setGoogleSignedIn(false)
            secureStorage.clearSensitiveData()
            debtDao.deleteAll()
            paymentDao.deleteAll()
            splitDao.deleteAll()
        } finally {
            isSigningOut.value = false
        }
    }

    fun linkEmailPassword(email: String, password: String, activity: Activity) = viewModelScope.launch {
        auth.linkWithEmailPassword(email, password)
            .onFailure { e ->
                android.util.Log.e("SettingsViewModel", "linkEmailPassword failed: ${e.message}", e)
            }
    }

    /**
     * GDPR-compliant account deletion: wipes all cloud data, then deletes the Firebase account.
     * On `FirebaseAuthRecentLoginRequiredException`, surfaces a re-auth requirement.
     */
    companion object {
        private const val GRACE_PERIOD_MS = 24 * 60 * 60 * 1000L
    }

    fun requestAccountDeletion(onSuccess: () -> Unit) = viewModelScope.launch {
        // Best-effort: post to the configured Cloud Function BEFORE recording
        // the local 24-hour grace timestamp. If the HTTP fails or the URL is
        // missing, we still set the local timestamp so the user's experience
        // (sign-in during grace window cancels deletion) works as designed.
        val uid = auth.getUserId()
        if (uid != null) {
            auth.requestAccountDeletion(uid)
        }
        prefs.setPendingDeletionTimestamp(System.currentTimeMillis())
        onSuccess()
    }

    fun deleteAccount(context: Context, onReauthRequired: () -> Unit, onFailure: (String) -> Unit) = viewModelScope.launch {
        val userId = auth.getUserId()
        if (userId == null) {
            onFailure("Not signed in.")
            return@launch
        }
        isDeletingAccount.value = true
        try {
            runCatching {
                firebaseRepository.deleteAllUserData(userId)
            }.onSuccess {
                auth.deleteAccount().onSuccess {
                    realTimeSyncManager.stopListening()
                    debts.clearLocalData()
                    prefs.clearAll()
                    secureStorage.clearSensitiveData()
                }.onFailure { e ->
                    if (e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                        onReauthRequired()
                    } else {
                        onFailure(e.message ?: "Account deletion failed")
                    }
                }
            }.onFailure { e ->
                onFailure(e.message ?: "Account deletion failed")
            }
        } catch (e: Exception) {
            onFailure(e.message ?: "Account deletion failed")
        } finally {
            isDeletingAccount.value = false
        }
    }

    fun exportCsv(context: Context) = viewModelScope.launch {
        val uriResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CsvExporter.exportDebts(context, debts.getAllDebtsOnce())
        }
        uriResult.onSuccess { uri ->
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(shareIntent, "Export CSV").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Could not share CSV", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.onFailure { e ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun clearSettledDebts() = viewModelScope.launch { debts.deleteSettledDebts() }

    fun syncNow() = viewModelScope.launch {
        val userId = auth.getCurrentUser()?.uid ?: return@launch
        isSyncing.value = true
        syncMessage.value = "Syncing..."
        try {
            sync.fullSync(userId)
            syncMessage.value = "Sync complete"
        } catch (e: Exception) {
            syncMessage.value = "Sync failed: ${e.message}"
        } finally {
            isSyncing.value = false
        }
    }
}
