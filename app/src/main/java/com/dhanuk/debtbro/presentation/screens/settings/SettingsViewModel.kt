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
    val syncMessage: String = "",
    val showDescription: Boolean = true,
    val showDueDate: Boolean = true,
    val showEmoji: Boolean = true
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
        prefs.selectedLanguage, isSyncing, syncMessage,
        prefs.showDescription, prefs.showDueDate, prefs.showEmoji
    ) { v ->
        SettingsUiState(
            userName = v[0] as String,
            roastLevel = v[1] as String,
            currency = v[2] as String,
            isSignedIn = v[3] as Boolean,
            googleName = v[4] as String,
            email = v[5] as String,
            userPhoto = v[6] as String,
            lastSynced = v[7] as Long,
            selectedLanguage = v[8] as String,
            isSyncing = v[9] as Boolean,
            syncMessage = v[10] as String,
            showDescription = v[11] as Boolean,
            showDueDate = v[12] as Boolean,
            showEmoji = v[13] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun saveUserName(name: String) = viewModelScope.launch { prefs.saveUserName(name) }
    
    fun setRoastLevel(level: String) = viewModelScope.launch { prefs.setRoastLevel(level) }
    fun setCurrency(c: String) = viewModelScope.launch { prefs.setCurrency(c) }
    fun setLanguage(code: String) = viewModelScope.launch { prefs.setLanguage(code) }
    fun setShowDescription(value: Boolean) = viewModelScope.launch { prefs.setShowDescription(value) }
    fun setShowDueDate(value: Boolean) = viewModelScope.launch { prefs.setShowDueDate(value) }
    fun setShowEmoji(value: Boolean) = viewModelScope.launch { prefs.setShowEmoji(value) }

    fun signInWithGoogle(activity: Activity) = viewModelScope.launch {
        auth.signInWithGoogle(activity).onSuccess { user ->
            prefs.setGoogleSignedIn(true, user.displayName ?: "DebtBro user", user.email ?: "", user.photoUrl?.toString().orEmpty())
            user.uid?.let { uid ->
                isSyncing.value = true
                syncMessage.value = "Syncing your data..."
                try {
                    realTimeSyncManager.startListening(uid)
                    sync.fullSync(uid)
                    syncMessage.value = ""
                } catch (e: Exception) {
                    syncMessage.value = "Sync failed: ${e.message}"
                } finally {
                    isSyncing.value = false
                }
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
            context.startActivity(chooser)
        }.onFailure { e ->
            e.printStackTrace()
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
            syncMessage.value = ""
        } catch (e: Exception) {
            syncMessage.value = "Sync failed: ${e.message}"
        } finally {
            isSyncing.value = false
        }
    }
}
