package com.dhanuk.debtbro.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.RealTimeSyncManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.CurrencyRepository
import com.dhanuk.debtbro.data.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val hasShownSignInPrompt: Boolean = true,
    /** Symbol (e.g. "₹", "$") in which the monetary totals above are
     *  denominated. DashboardScreen passes this to `formatCurrency`
     *  so totals always render in the user's default currency even
     *  when individual debts were entered under different currencies. */
    val currency: String = "₹"
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val debts: DebtRepository,
    private val prefs: AppPreferences,
    private val authManager: AuthManager,
    private val syncManager: SyncManager,
    private val realTimeSyncManager: RealTimeSyncManager,
    private val currencyRepository: CurrencyRepository
) : ViewModel() {

    private val syncMutex = Mutex()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Three-layer combine since Kotlin's `combine()` only has typed
    // overloads up to 5 sources. We split the 6 reactive inputs across
    // two layers:
    //   Layer 1: combine(debts, currency, rates) -> Triple — collapses
    //            the 3 most-frequently-changing sources into one typed
    //            source so the outer combine stays at 5 typed args.
    //   Layer 2: combine(layer1, name, signedIn, photo, shown) -> state.
    // The triples carry the full rates map so totals recompute whenever
    // the FX table refreshes on cold start (after the network round
    // trip completes) — without this, totals would be stale-idempotent
    // until the user manually pulled to refresh.
    private data class DebtsAndCurrency(
        val all: List<DebtEntity>,
        val defaultCurrency: String
    )

    private val debtsAndCurrency: kotlinx.coroutines.flow.Flow<DebtsAndCurrency> =
        combine(
            debts.getAllDebts(),
            prefs.defaultCurrency,
            currencyRepository.rates
        ) { all, cur, _ -> DebtsAndCurrency(all, cur) }

    val state: StateFlow<DashboardUiState> = combine(
        debtsAndCurrency,
        prefs.userName,
        prefs.isGoogleSignedIn,
        prefs.googleUserPhoto,
        prefs.hasShownSignInPrompt
    ) { dc, name, signedIn, photo, shown ->
        val all = dc.all
        val defaultCurrency = dc.defaultCurrency

        // Convert each debt's outstanding amount into the default
        // currency before summing. Fall-back in CurrencyRepository.convert
        // is identity (returns the amount unchanged) when rates aren't
        // loaded yet or the debt's currency equals the default — so
        // pre-FX-load behavior matches the legacy naive-sum path.
        val owedToMe = all
            .filter { it.type == "THEY_OWE_ME" && it.status != "SETTLED" }
            .sumOf { currencyRepository.convert(it.amount - it.amountPaid, it.currency, defaultCurrency) }
        val iOwe = all
            .filter { it.type == "I_OWE_THEM" && it.status != "SETTLED" }
            .sumOf { currencyRepository.convert(it.amount - it.amountPaid, it.currency, defaultCurrency) }

        // Recovery rate is a ratio, so mixing currencies here would be
        // misleading; convert both sums to the default currency first.
        val allTimeOwed = all
            .filter { it.type == "THEY_OWE_ME" }
            .sumOf { currencyRepository.convert(it.amount, it.currency, defaultCurrency) }
        val allTimeSettled = all
            .filter { it.type == "THEY_OWE_ME" }
            .sumOf { currencyRepository.convert(it.amountPaid, it.currency, defaultCurrency) }
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
            hasShownSignInPrompt = shown,
            currency = defaultCurrency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        // Start real-time sync when user is signed in
        viewModelScope.launch {
            authManager.authStateFlow().collect { user ->
                if (user != null) {
                    realTimeSyncManager.startListening(user.uid)
                    // Also do an initial full sync to catch up
                    runCatching { syncManager.fullSync(user.uid) }
                } else {
                    realTimeSyncManager.stopListening()
                }
            }
        }
    }

    fun dismissPrompt() = viewModelScope.launch { prefs.setHasShownSignInPrompt(true) }

    fun refresh() = viewModelScope.launch {
        if (_isRefreshing.value) return@launch
        _isRefreshing.value = true
        try {
            syncMutex.withLock {
                val user = authManager.authStateFlow().first()
                if (user != null) {
                    runCatching { syncManager.fullSync(user.uid) }
                }
            }
        } finally {
            _isRefreshing.value = false
        }
    }
}
