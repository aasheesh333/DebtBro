package com.dhanuk.debtbro.presentation.screens.settings

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
import com.dhanuk.debtbro.util.CsvExporter
import com.dhanuk.debtbro.util.LocalizedString
import com.dhanuk.debtbro.worker.AccountDeletionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
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
    val notifyPaymentAlerts: Boolean = true,
    val engagementNotificationsEnabled: Boolean = true
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
    val engagementNotificationsEnabled: Boolean = true,
    val exportFormat: String = "CSV",
    val themeMode: String = "SYSTEM",
    val isSigningOut: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val pendingDeletionTimestamp: Long = 0L
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
            // Local DataStore check: user may have requested deletion on this
            // device earlier and just relaunched the app past the grace window.
            // Only honor the local timestamp when the stored UID matches the
            // currently-signed-in UID — otherwise a foreign user's stale
            // pending timestamp would falsely trigger commitAccountDeletion.
            val ts = prefs.pendingDeletionTimestamp.first()
            val storedUid = prefs.pendingDeletionUid.first()
            val currentUid = auth.getUserId()
            val uidMatchesLocal = storedUid.isNotBlank() && currentUid != null && storedUid == currentUid
            if (ts > 0L && uidMatchesLocal && (System.currentTimeMillis() - ts) >= GRACE_PERIOD_MS) {
                commitAccountDeletion()
                return@launch
            }
            // Server-side check: user may have requested deletion on another
            // device, or uninstalled + reinstalled. Local DataStore wouldn't
            // carry the pending timestamp across those scenarios — Firestore is
            // the source of truth. Should only run if the user is signed in.
            val uid = auth.getUserId()
            if (uid != null) {
                val serverRequestedAt = auth.checkDeletionRequest(uid)
                if (serverRequestedAt != null) {
                    val elapsed = System.currentTimeMillis() - serverRequestedAt
                    if (elapsed >= GRACE_PERIOD_MS) {
                        // Grace has elapsed while the user was away — commit deletion.
                        commitAccountDeletion()
                    } else {
                        // Within grace — surface the alert so the user can cancel.
                        // Also reflect this into local DataStore so the counter
                        // badge + dialog stay consistent across relaunches.
                        prefs.setPendingDeletionTimestamp(serverRequestedAt, uid)
                        _showDeletionGraceAlert.value = true
                    }
                }
            }
        }
    }

    private suspend fun commitAccountDeletion() {
        val userId = auth.getUserId()
        if (userId != null) {
            try {
                firebaseRepository.deleteAllUserData(userId)
                auth.deleteAccount()
                // Clear the server-side queue doc once the wipe + Auth delete
                // have completed — don't leave orphaned PENDING state behind.
                auth.clearDeletionRequest(userId)
            } catch (_: Exception) { }
        }
        prefs.clearPendingDeletion()
        prefs.clearUserSession()
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
        prefs.notifyPaymentAlerts,
        prefs.engagementNotificationsEnabled
    ) { daily, weekly, payment, engagement ->
        NotificationSettingsState(
            notifyDailyReminder = daily,
            notifyWeeklySummary = weekly,
            notifyPaymentAlerts = payment,
            engagementNotificationsEnabled = engagement
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
            engagementNotificationsEnabled = notification.engagementNotificationsEnabled,
            lastSynced = sync.lastSynced,
            isSyncing = sync.isSyncing,
            syncMessage = sync.syncMessage,
            isSigningOut = deletion.signingOut,
            isDeletingAccount = deletion.deleting,
            pendingDeletionTimestamp = deletion.pendingTs,
            linkedProviders = auth.linkedProviders()
        )
    }
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
    fun setEngagementNotificationsEnabled(value: Boolean) = viewModelScope.launch { prefs.setEngagementNotificationsEnabled(value) }
    fun setExportFormat(format: String) = viewModelScope.launch { prefs.setExportFormat(format) }
    fun setThemeMode(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setCustomAvatarUri(uri: String) = viewModelScope.launch { prefs.setCustomAvatarUri(uri) }

    private val _showDeletionGraceAlert = MutableStateFlow(false)
    val showDeletionGraceAlert: StateFlow<Boolean> = _showDeletionGraceAlert.asStateFlow()

    fun signInWithGoogle(activity: Activity) = viewModelScope.launch {
        auth.signInWithGoogle(activity).onSuccess { user ->
            prefs.setGoogleSignedIn(true, user.displayName ?: "DebtPayoff Pro user", user.email ?: "", user.photoUrl?.toString().orEmpty())
            user.uid?.let { uid ->
                // Server-side deletion-request check (the source of truth — a
                // pending request could have been created on another device
                // or before a reinstall, in which case local DataStore knows
                // nothing about it). Local DataStore is then synchronised from
                // the server value so the rest of the app (counter badge,
                // Settings dialog) stays consistent.
                val serverRequestedAt = auth.checkDeletionRequest(uid)
                if (serverRequestedAt != null) {
                    val elapsed = System.currentTimeMillis() - serverRequestedAt
                    if (elapsed >= GRACE_PERIOD_MS) {
                        // Grace already elapsed — finish what was started elsewhere.
                        commitAccountDeletion()
                        // Don't proceed with sync — the data has been wiped.
                        return@let
                    }
                    // Within window — mirror into local prefs and alert the user
                    prefs.setPendingDeletionTimestamp(serverRequestedAt, uid)
                    _showDeletionGraceAlert.value = true
                    return@let
                }
                // No server-side pending deletion — sync as normal.
                isSyncing.value = true
                syncMessage.value = LocalizedString.get("syncing_your_data")
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

    suspend fun checkGracePeriodOnSignIn(): Boolean {
        // AuthManager.checkDeletionRequest already absorbs failures and
        // logs them loudly — don't double-wrap, or a future firestore.rules
        // regression stays invisible again.
        val uid = auth.getUserId() ?: return false
        val serverRequestedAt = auth.checkDeletionRequest(uid) ?: return false
        val elapsed = System.currentTimeMillis() - serverRequestedAt
        return if (elapsed < GRACE_PERIOD_MS) {
            prefs.setPendingDeletionTimestamp(serverRequestedAt, uid)
            true
        } else {
            false
        }
    }

    fun cancelDeletion() = viewModelScope.launch {
        val uid = auth.getUserId()
        if (uid != null) {
            auth.cancelAccountDeletion(uid, System.currentTimeMillis())
        }
        prefs.clearPendingDeletion()
        _showDeletionGraceAlert.value = false
    }

    fun signOut() = viewModelScope.launch {
        isSigningOut.value = true
        try {
            realTimeSyncManager.stopListening()
            auth.signOut()
            prefs.setSignedIn(null)
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
        // Mirrors the worker's grace window (30 min) and the Privacy Policy
        // wording ("we complete account deletion within 30 minutes").
        private const val GRACE_PERIOD_MS = AccountDeletionWorker.GRACE_PERIOD_MS
    }

    fun requestAccountDeletion(context: Context, onSuccess: () -> Unit) = viewModelScope.launch {
        // Write the server-side deletion-request doc FIRST so the server-of-
        // record is updated even if the user uninstalls before the WorkManager
        // backup fires. Then record the local timestamp for the counter badge
        // and enqueue the WorkManager failsafe.
        val requestedAt = System.currentTimeMillis()
        val uid = auth.getUserId()
        if (uid != null) {
            auth.requestAccountDeletion(uid, requestedAt)
            prefs.setPendingDeletionTimestamp(requestedAt, uid)
        }
        // Failsafe worker: if the user uninstalls / loses the device during
        // the grace window, the SettingsViewModel.init path never runs (the
        // user never reopens the app). This WorkManager one-shot fires after
        // GRACE_PERIOD_MS to commit deletion on the local device. The
        // server-side deletionRequests/{uid} doc is the cross-device failsafe.
        runCatching {
            val workRequest = OneTimeWorkRequestBuilder<AccountDeletionWorker>()
                .setInitialDelay(AccountDeletionWorker.GRACE_PERIOD_MS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                AccountDeletionWorker.UNIQUE_WORK_NAME,
                // KEEP (instead of REPLACE) so a re-tap on "Schedule Grace"
                // during an existing pending grace doesn't reset the timer
                // back to the second-tap time. The earliest grace start wins.
                // (User can still cancel by signing back in.)
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
        onSuccess()
    }

    /**
     * P0-1 (2026-07-03): immediate account deletion path, paired with the
     * grace path in the Settings delete-account dialog. Calls the existing
     * [deleteAccount] implementation which already handles
     * [FirebaseAuthRecentLoginRequiredException] via the [onReauthRequired]
     * callback. On success, also cancels any pending WorkManager grace
     * backup so the worker doesn't double-fire later.
     */
    fun requestImmediateDeletion(context: Context, onReauthRequired: () -> Unit, onFailure: (String) -> Unit, onSuccess: () -> Unit = {}) = viewModelScope.launch {
        val uid = auth.getUserId()
        if (uid == null) {
            onFailure(LocalizedString.get("not_signed_in"))
            return@launch
        }
        // Schedule the cloud-side + WorkManager 24h failsafe rather than
        // auth.deleteAccount() right now. Firebase must keep the account
        // alive so the server's deletionRequests/{uid} doc stays valid.
        // On a same-account re-login within 24h the SignIn/Settings flow
        // shows the "Log in and reactivate vs Cancel" alert; if the user
        // never returns, AccountDeletionWorker (or SettingsViewModel.init
        // once they next sign in) commits the actual Firebase deletion.
        val requestedAt = System.currentTimeMillis()
        isDeletingAccount.value = true
        try {
            auth.requestAccountDeletion(uid, requestedAt)
            prefs.setPendingDeletionTimestamp(requestedAt, uid)
            runCatching {
                val workRequest = OneTimeWorkRequestBuilder<AccountDeletionWorker>()
                    .setInitialDelay(AccountDeletionWorker.GRACE_PERIOD_MS, TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    AccountDeletionWorker.UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
            // Wipe local data and sign out — the user expects to land on
            // the SignIn screen, NOT for the Firebase account to vanish
            // yet. That happens 24h later via the worker/init block.
            realTimeSyncManager.stopListening()
            debts.clearLocalData()
            prefs.clearUserSession()
            secureStorage.clearSensitiveData()
            // clearUserSession wipes ALL prefs including the just-set
            // pendingDeletionTimestamp. Re-write it (with the requestor's
            // UID) so the Settings counter badge + init-block check fire
            // correctly if the user relaunches before the worker fires.
            prefs.setPendingDeletionTimestamp(requestedAt, uid)
            auth.signOut()
            onSuccess()
        } catch (e: Exception) {
            if (e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                onReauthRequired()
            } else {
                onFailure(e.message ?: LocalizedString.get("account_deletion_failed"))
            }
        } finally {
            isDeletingAccount.value = false
        }
    }

    fun deleteAccount(context: Context, onReauthRequired: () -> Unit, onFailure: (String) -> Unit, onSuccess: () -> Unit = {}) = viewModelScope.launch {
        val userId = auth.getUserId()
        if (userId == null) {
            onFailure(LocalizedString.get("not_signed_in"))
            return@launch
        }
        isDeletingAccount.value = true
        try {
            runCatching {
                firebaseRepository.deleteAllUserData(userId)
            }
            // Always wipe local data + sign out, regardless of whether the
            // cloud wipe succeeded. The user has requested deletion; we
            // must not strand them in a half-state. The 24h failsafe
            // (server-side deletionRequests/{uid} doc + WorkManager) handles
            // the actual Firebase Auth account removal if this call fails.
            auth.deleteAccount()
            realTimeSyncManager.stopListening()
            debts.clearLocalData()
            prefs.clearUserSession()
            secureStorage.clearSensitiveData()
            onSuccess()
        } catch (e: Exception) {
            if (e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                onReauthRequired()
            } else {
                onFailure(e.message ?: LocalizedString.get("account_deletion_failed"))
            }
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
                    android.widget.Toast.makeText(context, LocalizedString.get("could_not_share_csv"), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.onFailure { e ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, LocalizedString.get("export_failed") + ": " + e.message, android.widget.Toast.LENGTH_LONG).show()
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
