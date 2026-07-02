package com.dhanuk.debtbro.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.NoApiKeyException
import com.dhanuk.debtbro.data.repository.AiRepository
import com.dhanuk.debtbro.util.LocalizedString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class AnalyticsUiState(
    val totalOwedToMe: Double = 0.0,
    val totalIOwe: Double = 0.0,
    val totalSettled: Double = 0.0,
    val netBalance: Double = 0.0,
    val recoveryRate: Int = 0,
    val mostTrustedFriend: String = "No winner yet",
    val worstOffender: String = "Nobody yet",
    val monthlyData: List<Pair<String, Double>> = emptyList(),
    val currency: String = "₹"
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(private val repo: DebtRepository,     private val ai: AiRepository, private val prefs: AppPreferences) : ViewModel() {
    val aiInsight = MutableStateFlow("")
    val isLoadingInsight = MutableStateFlow(false)
    private val currency = prefs.defaultCurrency.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "₹")
    val state: StateFlow<AnalyticsUiState> = combine(repo.getAllDebts(), aiInsight, currency) { debts, _, curr -> compute(debts, curr) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())

    init {
        // Wait for the first non-empty state emission before requesting AI
        // insight. The cold-start seeded UiState() has zero totals + "Nobody
        // yet", and `init {}` runs only once — so we observe state and only
        // invoke loadAiInsight() once Room has real debt data. This avoids
        // burning an API call on a misleading "your zero-debt wallet" prompt
        // AND auto-refreshes the insight when the user adds their first debt.
        // The in-function guard below is belt-and-suspenders for manual
        // refresh attempts against a still-empty wallet.
        viewModelScope.launch {
            state.first { it.totalOwedToMe + it.totalIOwe > 0 }
            loadAiInsight()
        }
    }
    private fun compute(debts: List<DebtEntity>, curr: String): AnalyticsUiState {
        val totalOwed = debts.filter { it.type == "THEY_OWE_ME" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid }
        val totalIOwe = debts.filter { it.type == "I_OWE_THEM" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid }
        val settled = debts.filter { it.status == "SETTLED" }.sumOf { it.amount }
        val lent = debts.filter { it.type == "THEY_OWE_ME" }.sumOf { it.amount }
        val worst = debts.filter { it.status != "SETTLED" }.minByOrNull { it.createdAt }?.personName ?: "Nobody yet"
        val trusted = debts.filter { it.status == "SETTLED" }.groupBy { it.personName }.maxByOrNull { it.value.sumOf { d -> d.amount } }?.key ?: "No winner yet"
        val fmt = SimpleDateFormat("MMM", Locale.getDefault())
        val months = (5 downTo 0).map { back ->
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -back) }
            val month = fmt.format(cal.time)
            val year = cal.get(Calendar.YEAR)
            val value = debts.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.createdAt }
                c.get(Calendar.YEAR) == year && fmt.format(c.time) == month && it.type == "THEY_OWE_ME"
            }.sumOf { it.amount }
            month to value
        }
        return AnalyticsUiState(totalOwed, totalIOwe, settled, totalOwed - totalIOwe, if (lent > 0) ((settled / lent) * 100).toInt().coerceIn(0, 100) else 0, trusted, worst, months, curr)
    }
    fun loadAiInsight() = viewModelScope.launch {
        isLoadingInsight.value = true
        try {
            val s = state.value
            // Skip the API call when the wallet is genuinely empty. The cold
            // init { loadAiInsight() } fires against the seeded UiState() before
            // Room emits real data; without this guard the user sees a
            // misleading roast about their "0 lent, 0 owed, Nobody yet" wallet
            // and we waste a free regeneration on an uninteresting prompt.
            if (s.totalOwedToMe + s.totalIOwe <= 0.0) {
                aiInsight.value = ""
                return@launch
            }
            val result = ai.analyzeDebts(s.totalOwedToMe, s.totalIOwe, s.recoveryRate, s.worstOffender)
            aiInsight.value = when {
                result.isSuccess -> result.getOrThrow()
                result.exceptionOrNull() is NoApiKeyException ->
                    LocalizedString.get("no_api_key_message")
                else ->
                    LocalizedString.get("ai_error_message")
            }
        } finally {
            // Reset the loading flag even on CancellationException so the
            // spinner doesn't stick on `true` when the screen is closed
            // mid-request or the user backs out before the API responds.
            isLoadingInsight.value = false
        }
    }
}
