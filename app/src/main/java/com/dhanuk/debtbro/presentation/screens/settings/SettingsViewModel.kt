package com.dhanuk.debtbro.presentation.screens.settings

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.RealTimeSyncManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.GroqRepository
import com.dhanuk.debtbro.util.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "",
    val groqKey: String = "",
    val roastLevel: String = "MEDIUM",
    val currency: String = "₹",
    val isSignedIn: Boolean = false,
    val googleName: String = "",
    val email: String = "",
    val userPhoto: String = "",
    val lastSynced: Long = 0L,
    val selectedLanguage: String = "en",
    val testResult: String = "",
    val isSyncing: Boolean = false,
    val syncMessage: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val auth: AuthManager,
    private val sync: SyncManager,
    private val realTimeSyncManager: RealTimeSyncManager,
    private val debts: DebtRepository,
    private val groq: GroqRepository
) : ViewModel() {
        private val isSyncing = MutableStateFlow(false)
    private val syncMessage = MutableStateFlow("")

    val state: StateFlow<SettingsUiState> = combine(
        prefs.userName, prefs.roastLevel,
        prefs.defaultCurrency, prefs.isGoogleSignedIn, prefs.googleUserName,
        prefs.googleUserEmail, prefs.googleUserPhoto, prefs.lastSyncedAt,
        prefs.selectedLanguage, isSyncing, syncMessage
    ) { v ->
        SettingsUiState(
            v[0] as String, v[1] as String,
            v[2] as String, v[3] as Boolean, v[4] as String,
            v[5] as String, v[6] as String, v[7] as Long,
            v[8] as String, v[9] as Boolean, v[10] as String
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun saveUserName(name: String) = viewModelScope.launch { prefs.saveUserName(name) }
    
    fun setRoastLevel(level: String) = viewModelScope.launch { prefs.setRoastLevel(level) }
    fun setCurrency(c: String) = viewModelScope.launch { prefs.setCurrency(c) }
    fun setLanguage(code: String) = viewModelScope.launch { prefs.setLanguage(code) }
    

    fun signInWithGoogle(activity: Activity) = viewModelScope.launch {
        auth.signInWithGoogle(activity).onSuccess { user ->
            prefs.setGoogleSignedIn(true, user.displayName ?: "DebtBro user", user.email ?: "", user.photoUrl?.toString().orEmpty())
            // Auto-sync: pull cloud data after sign-in so previous data appears
            user.uid?.let { uid ->
                isSyncing.value = true
                syncMessage.value = "Syncing your data..."
                runCatching {
                    realTimeSyncManager.startListening(uid)
                    sync.fullSync(uid)
                }.onSuccess { syncMessage.value = "" }
                    .onFailure { syncMessage.value = "Sync failed: ${it.message}" }
                isSyncing.value = false
            }
        }
    }

    fun signOut() = viewModelScope.launch {
        realTimeSyncManager.stopListening()
        auth.signOut()
        prefs.setGoogleSignedIn(false)
    }

    fun exportCsv(context: Context) = viewModelScope.launch {
        val uriResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { CsvExporter.exportDebts(context, debts.getAllDebtsOnce()) }
        }
        uriResult.onSuccess { uri ->
            com.dhanuk.debtbro.util.shareFile(context, uri, "text/csv")
        }.onFailure { e ->
            e.printStackTrace()
            syncMessage.value = "Export failed: ${e.message}"
        }
    }

    fun clearSettledDebts() = viewModelScope.launch { debts.deleteSettledDebts() }

    fun syncNow() = viewModelScope.launch {
        val userId = auth.getCurrentUser()?.uid ?: return@launch
        isSyncing.value = true
        syncMessage.value = "Syncing..."
        runCatching { sync.fullSync(userId) }
            .onSuccess { syncMessage.value = "" }
            .onFailure { syncMessage.value = "Sync failed: ${it.message}" }
        isSyncing.value = false
    }
}
