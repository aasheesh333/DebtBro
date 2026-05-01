package com.dhanuk.debtbro.presentation.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.components.BannerAdView
import com.dhanuk.debtbro.presentation.components.LoadingDotsIndicator
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.util.formatCurrency

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val insight by viewModel.aiInsight.collectAsStateWithLifecycle()
    val loading by viewModel.isLoadingInsight.collectAsStateWithLifecycle()
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("Owed", state.totalOwedToMe, Modifier.weight(1f)); Stat("I owe", state.totalIOwe, Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("Settled", state.totalSettled, Modifier.weight(1f)); Stat("Net", state.netBalance, Modifier.weight(1f)) } }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Monthly activity", style = MaterialTheme.typography.titleLarge)
                val max = state.monthlyData.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
                state.monthlyData.forEach { (m, v) -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(m, Modifier.weight(0.22f)); Column(Modifier.weight(1f).height(18.dp).background(PrimaryGreen.copy(alpha = (v / max).toFloat().coerceIn(0.12f, 1f)), RoundedCornerShape(4.dp))) {} } }
            } }
        }
        item { Text("🏅 Most Trusted: ${state.mostTrustedFriend}\n💀 Worst Offender: ${state.worstOffender}") }
        item { LinearProgressIndicator(progress = { state.recoveryRate / 100f }, modifier = Modifier.fillMaxWidth()); Text("Recovery rate ${state.recoveryRate}%") }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("AI Insight"); if (loading) LoadingDotsIndicator(color = PrimaryGreen) else Text(insight.ifBlank { "Tap the button for a sharp financial roast." }); Button(viewModel::loadAiInsight) { Text("🤖 Get Financial Roast") } } } }
        item { BannerAdView() }
    }
}

@Composable
private fun Stat(label: String, amount: Double, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(14.dp)) { Text(label); Text(formatCurrency(amount), fontWeight = FontWeight.Bold) } }
}
