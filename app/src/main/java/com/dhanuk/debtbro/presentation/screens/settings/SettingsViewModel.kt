package com.dhanuk.debtbro.presentation.screens.settings

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.GroqRepository
import com.dhanuk.debtbro.util.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val testResult: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val auth: AuthManager,
    private val sync: SyncManager,
    private val debts: DebtRepository,
    private val groq: GroqRepository
) : ViewModel() {
    private val testMessage = kotlinx.coroutines.flow.MutableStateFlow("")
    val state: StateFlow<SettingsUiState> = combine(
        prefs.userName, prefs.groqApiKey, prefs.roastLevel, 
        prefs.defaultCurrency, prefs.isGoogleSignedIn, prefs.googleUserName, 
        prefs.googleUserEmail, prefs.googleUserPhoto, prefs.lastSyncedAt, 
        prefs.selectedLanguage, testMessage
    ) { v ->
        SettingsUiState(
            v[0] as String, v[1] as String, v[2] as String, 
            v[3] as String, v[4] as Boolean, v[5] as String, 
            v[6] as String, v[7] as String, v[8] as Long, 
            v[9] as String, v[10] as String
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())
    fun saveUserName(name: String) = viewModelScope.launch { prefs.saveUserName(name) }
    fun saveGroqKey(key: String) = viewModelScope.launch { prefs.saveGroqKey(key) }
    fun setRoastLevel(level: String) = viewModelScope.launch { prefs.setRoastLevel(level) }
    fun setCurrency(c: String) = viewModelScope.launch { prefs.setCurrency(c) }
    fun setLanguage(code: String) = viewModelScope.launch { prefs.setLanguage(code) }
    fun testGroqConnection() = viewModelScope.launch { testMessage.value = if (groq.testConnection()) "Groq OK" else "Add a valid API key" }
    fun signInWithGoogle(activity: Activity) = viewModelScope.launch {
        auth.signInWithGoogle(activity).onSuccess { user -> prefs.setGoogleSignedIn(true, user.displayName ?: "DebtBro user", user.email ?: "", user.photoUrl?.toString().orEmpty()) }
    }
    fun signOut() = viewModelScope.launch { auth.signOut(); prefs.setGoogleSignedIn(false) }
    fun exportCsv(context: Context) = viewModelScope.launch { 
        val uri = CsvExporter.exportDebts(context, debts.getAllDebtsOnce())
        com.dhanuk.debtbro.util.shareFile(context, uri, "text/csv")
    }
    fun clearSettledDebts() = viewModelScope.launch { debts.deleteSettledDebts() }
    fun syncNow() = viewModelScope.launch { auth.getCurrentUser()?.uid?.let { sync.mergePendingUnsynced(it) } }
}
