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
    val state: StateFlow<SettingsUiState> = combine(prefs.userName, prefs.groqApiKey, prefs.roastLevel, prefs.defaultCurrency, prefs.isGoogleSignedIn, prefs.googleUserName, prefs.googleUserEmail, prefs.googleUserPhoto, prefs.lastSyncedAt, testMessage) { values ->
        SettingsUiState(values[0] as String, values[1] as String, values[2] as String, values[3] as String, values[4] as Boolean, values[5] as String, values[6] as String, values[7] as String, values[8] as Long, values[9] as String)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())
    fun saveUserName(name: String) = viewModelScope.launch { prefs.saveUserName(name) }
    fun saveGroqKey(key: String) = viewModelScope.launch { prefs.saveGroqKey(key) }
    fun setRoastLevel(level: String) = viewModelScope.launch { prefs.setRoastLevel(level) }
    fun setCurrency(c: String) = viewModelScope.launch { prefs.setCurrency(c) }
    fun testGroqConnection() = viewModelScope.launch { testMessage.value = if (groq.testConnection()) "Groq OK" else "Add a valid API key" }
    fun signInWithGoogle(activity: Activity) = viewModelScope.launch {
        auth.signInWithGoogle(activity).onSuccess { user -> prefs.setGoogleSignedIn(true, user.displayName ?: "DebtBro user", user.email ?: "", user.photoUrl?.toString().orEmpty()) }
    }
    fun signOut() = viewModelScope.launch { auth.signOut(); prefs.setGoogleSignedIn(false) }
    fun exportCsv(context: Context) = viewModelScope.launch { CsvExporter.exportDebts(context, debts.getAllDebtsOnce()) }
    fun clearSettledDebts() = viewModelScope.launch { debts.deleteSettledDebts() }
    fun syncNow() = viewModelScope.launch { auth.getCurrentUser()?.uid?.let { sync.mergePendingUnsynced(it) } }
}
