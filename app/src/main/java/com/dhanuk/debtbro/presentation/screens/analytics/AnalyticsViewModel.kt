package com.dhanuk.debtbro.presentation.screens.analytics

import android.content.Context
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
import kotlinx.coroutines.flow.asStateFlow
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
    val mostTrustedFriend: String = LocalizedString.get("no_winner_yet"),
    val worstOffender: String = "Nobody yet",
    val monthlyData: List<Pair<String, Double>> = emptyList(),
    val currency: String = "₹"
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repo: DebtRepository,
    private val ai: AiRepository,
    private val prefs: AppPreferences,
    private val adManager: com.dhanuk.debtbro.data.ads.AdManager
) : ViewModel() {
    val aiInsight = MutableStateFlow("")
    val isLoadingInsight = MutableStateFlow(false)
    private val currency = prefs.defaultCurrency.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "₹")
    val state: StateFlow<AnalyticsUiState> = combine(repo.getAllDebts(), aiInsight, currency) { debts, _, curr -> compute(debts, curr) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())

    private val _showRewardAd = MutableStateFlow(false)
    val showRewardAd: StateFlow<Boolean> = _showRewardAd.asStateFlow()

    private val _remainingFree = MutableStateFlow(5)
    val remainingFree: StateFlow<Int> = _remainingFree.asStateFlow()

    fun dismissRewardAd() { _showRewardAd.value = false }

    init {
        // Wait for the first non-empty state emission before requesting AI
        // insight. The cold-start seeded UiState() has zero totals + "Nobody
        // yet", and `init {}` runs only once — so we observe state and only
        // invoke loadAiInsight() once Room has real debt data. This avoids
        // burning an API call on a misleading "your zero-debt wallet" prompt
        // AND auto-refreshes the insight when the user adds their first debt.
        // The in-function guard below is belt-and-suspenders for manual
        // refresh attempts against a still-empty wallet.
        //
        // Wave 3 (Tasks 14+15): before hitting Gemini, try a content-addressed
        // cache hit — compare the current debts-checksum to the one stored
        // alongside the last persisted insight. Match => reuse the cached
        // text (no network); mismatch => fetch fresh and rewrite the cache.
        // Suppresses screen-switch refetch spam when the user navigates
        // between tabs without changing any debts.
        viewModelScope.launch {
            _remainingFree.value = ai.remainingFreeRegenerations()
            state.first { it.totalOwedToMe + it.totalIOwe > 0 }
            // Cache hit first — avoid network if debts unchanged since last fetch
            val cachedText = prefs.lastAiInsightText.first()
            val cachedChecksum = prefs.lastAiInsightChecksum.first()
            val currentChecksum = checksumOf()
            if (cachedText != null && cachedChecksum == currentChecksum) {
                aiInsight.value = cachedText
                isLoadingInsight.value = false
            } else {
                loadAiInsight()
            }
        }
    }

    /**
     * Wave 3 (Tasks 14+15): stable content checksum of the current debts
     * snapshot. Sorted by id so insertion order doesn't shift the hash;
     * concatenates id/amount/amountPaid/status/dueDate/personName so any
     * add/remove/payment/settle/edit invalidates the cache. Hex string
     * keeps the value compact for DataStore storage.
     */
    private suspend fun checksumOf(): String {
        val debts = repo.getAllDebts().first()
        return debts.sortedBy { it.id }.joinToString("|") {
            "${it.id}:${it.amount}:${it.amountPaid}:${it.status}:${it.dueDate ?: 0L}:${it.personName}"
        }.hashCode().toString(16)
    }
    private fun compute(debts: List<DebtEntity>, curr: String): AnalyticsUiState {
        val totalOwed = debts.filter { it.type == "THEY_OWE_ME" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid }
        val totalIOwe = debts.filter { it.type == "I_OWE_THEM" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid }
        val settled = debts.filter { it.status == "SETTLED" }.sumOf { it.amount }
        val lent = debts.filter { it.type == "THEY_OWE_ME" }.sumOf { it.amount }
        val worst = debts.filter { it.status != "SETTLED" }.minByOrNull { it.createdAt }?.personName ?: "Nobody yet"
        val trusted = debts.filter { it.status == "SETTLED" }.groupBy { it.personName }.maxByOrNull { it.value.sumOf { d -> d.amount } }?.key ?: LocalizedString.get("no_winner_yet")
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
    fun preloadRewardedAd(context: Context) {
        adManager.loadRewardedAd(context)
    }

    fun loadAiInsight(activity: android.app.Activity? = null) = viewModelScope.launch {
        if (isLoadingInsight.value) return@launch
        isLoadingInsight.value = true

        val s = state.value
        if (s.totalOwedToMe + s.totalIOwe <= 0.0) {
            isLoadingInsight.value = false
            return@launch
        }

        if (!ai.canRegenerate()) {
            val connectivityManager = activity?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val isOffline = connectivityManager?.activeNetwork == null
            if (isOffline) {
                isLoadingInsight.value = false
                return@launch
            }
            if (activity != null) {
                isLoadingInsight.value = false
                adManager.showRewardedAd(activity, onRewarded = {
                    viewModelScope.launch {
                        ai.resetRegenerationCount()
                        _remainingFree.value = ai.remainingFreeRegenerations()
                        loadAiInsightInternal()
                    }
                }, onFailed = {
                    adManager.loadRewardedAd(activity)
                    _showRewardAd.value = false
                })
            } else {
                _showRewardAd.value = true
            }
            return@launch
        }

        loadAiInsightInternal()
    }

    private fun loadAiInsightInternal() = viewModelScope.launch {
        try {
            val s = state.value
            if (s.totalOwedToMe + s.totalIOwe <= 0.0) {
                aiInsight.value = ""
                return@launch
            }
            val result = ai.analyzeDebts(s.totalOwedToMe, s.totalIOwe, s.recoveryRate, s.worstOffender)
            val insightText = when {
                result.isSuccess -> result.getOrThrow()
                result.exceptionOrNull() is NoApiKeyException ->
                    LocalizedString.get("no_api_key_message")
                else ->
                    LocalizedString.get("ai_error_message")
            }
            aiInsight.value = insightText
            // Wave 3 (Tasks 14+15): persist the cache only on successful
            // Gemini generation. Localized fallback texts (no_api_key /
            // ai_error) must NOT be cached — those aren't real insights,
            // and storing them would suppress legitimate refetches once the
            // user fixes their API key or reconnects.
            if (result.isSuccess) {
                val checksum = checksumOf()
                prefs.setAiInsightCache(checksum, insightText)
            }
            _remainingFree.value = ai.remainingFreeRegenerations()
        } finally {
            isLoadingInsight.value = false
        }
    }
}
