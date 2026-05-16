package com.dhanuk.debtbro.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.GroqRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val monthlyData: List<Pair<String, Double>> = emptyList()
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(private val repo: DebtRepository, private val groq: GroqRepository) : ViewModel() {
    val aiInsight = MutableStateFlow("")
    val isLoadingInsight = MutableStateFlow(false)
    val state: StateFlow<AnalyticsUiState> = repo.getAllDebts().combine(aiInsight) { debts, _ -> compute(debts) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())

    init {
        loadAiInsight()
    }
    private fun compute(debts: List<DebtEntity>): AnalyticsUiState {
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
        return AnalyticsUiState(totalOwed, totalIOwe, settled, totalOwed - totalIOwe, if (lent > 0) ((settled / lent) * 100).toInt().coerceIn(0, 100) else 0, trusted, worst, months)
    }
    fun loadAiInsight() = viewModelScope.launch {
        isLoadingInsight.value = true
        val s = state.value
        aiInsight.value = groq.analyzeDebts(s.totalOwedToMe, s.totalIOwe, s.recoveryRate, s.worstOffender).getOrElse { "Add a Groq API key in Settings for the spicy financial roast." }
        isLoadingInsight.value = false
    }
}
