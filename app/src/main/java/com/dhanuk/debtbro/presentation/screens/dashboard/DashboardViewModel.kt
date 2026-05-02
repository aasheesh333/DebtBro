package com.dhanuk.debtbro.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val userName: String = "",
    val totalOwedToMe: Double = 0.0,
    val totalIOwe: Double = 0.0,
    val netBalance: Double = 0.0,
    val recoveryRate: Int = 0,
    val recentDebts: List<DebtEntity> = emptyList(),
    val overdueDebts: List<DebtEntity> = emptyList(),
    val leaderboard: List<DebtEntity> = emptyList(),
    val isSignedIn: Boolean = false,
    val userPhoto: String = "",
    val hasShownSignInPrompt: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val debts: DebtRepository,
    private val prefs: AppPreferences,
    private val authManager: AuthManager,
    private val syncManager: SyncManager
) : ViewModel() {

    val state: StateFlow<DashboardUiState> = combine(
        debts.getAllDebts(),
        prefs.userName,
        prefs.isGoogleSignedIn,
        prefs.googleUserPhoto,
        prefs.hasShownSignInPrompt
    ) { all, name, signedIn, photo, shown ->
        val owedToMe = all.filter { it.type == "THEY_OWE_ME" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid }
        val iOwe = all.filter { it.type == "I_OWE_THEM" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid }

        val allTimeOwed = all.filter { it.type == "THEY_OWE_ME" }.sumOf { it.amount }
        val allTimeSettled = all.filter { it.type == "THEY_OWE_ME" }.sumOf { it.amountPaid }
        val recoveryRate = if (allTimeOwed > 0) ((allTimeSettled / allTimeOwed) * 100).toInt() else 100

        DashboardUiState(
            userName = name.ifBlank { "Bro" },
            totalOwedToMe = owedToMe,
            totalIOwe = iOwe,
            netBalance = owedToMe - iOwe,
            recoveryRate = recoveryRate,
            recentDebts = all.filter { it.status != "SETTLED" }.sortedByDescending { it.createdAt }.take(5),
            overdueDebts = all.filter { it.dueDate != null && it.dueDate < System.currentTimeMillis() && it.status != "SETTLED" }
                .sortedBy { it.dueDate }.take(3),
            leaderboard = all.filter { it.type == "THEY_OWE_ME" && it.status != "SETTLED" }
                .sortedByDescending { it.amount - it.amountPaid }.take(5),
            isSignedIn = signedIn,
            userPhoto = photo,
            hasShownSignInPrompt = shown
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        // Auto-pull cloud data for already-signed-in users (e.g. after reinstall)
        viewModelScope.launch {
            if (authManager.isSignedIn()) {
                authManager.getCurrentUser()?.uid?.let { uid ->
                    runCatching { syncManager.fullSync(uid) }
                }
            }
        }
    }

    fun dismissPrompt() = viewModelScope.launch { prefs.setHasShownSignInPrompt(true) }
}
