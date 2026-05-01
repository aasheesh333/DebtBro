package com.dhanuk.debtbro.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.components.BannerAdView
import com.dhanuk.debtbro.presentation.components.DebtCard
import com.dhanuk.debtbro.presentation.theme.DangerRed
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.util.formatCurrency
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DashboardScreen(onAddDebt: () -> Unit, onDebts: () -> Unit, onSplit: () -> Unit, onSettings: () -> Unit, onDebtClick: (Int) -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(state.hasShownSignInPrompt, state.isSignedIn) {
        if (!state.hasShownSignInPrompt && !state.isSignedIn) { delay(2000); showPrompt = true }
    }
    Box {
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Hey ${state.userName}! 👋", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Icon(Icons.Default.Person, contentDescription = "Settings", modifier = Modifier.clickable(onClick = onSettings))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Owed to me", state.totalOwedToMe, Brush.horizontalGradient(listOf(PrimaryGreen, PrimaryGreen.copy(alpha = 0.55f))), Modifier.weight(1f))
                    SummaryCard("I owe", state.totalIOwe, Brush.horizontalGradient(listOf(DangerRed, DangerRed.copy(alpha = 0.55f))), Modifier.weight(1f))
                }
            }
            item { Text("Net ${formatCurrency(state.netBalance)}", color = if (state.netBalance >= 0) PrimaryGreen else DangerRed, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(16.dp), fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onAddDebt) { Text("+ Add Debt") }; Button(onSplit) { Text("➗ Split") }; Button(onDebts) { Text("📣 Nudge All") } } }
            if (state.overdueDebts.isNotEmpty()) {
                item { Text("Overdue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                itemsIndexed(state.overdueDebts) { _, debt -> DebtCard(debt, state.isSignedIn, { onDebtClick(debt.id) }) }
            }
            item { Text("Broke Friend Leaderboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.leaderboard.isEmpty()) item { Text("No debts yet. Peaceful wallet, suspiciously quiet friends.") }
            itemsIndexed(state.leaderboard) { index, debt ->
                Card { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("#${index + 1} ${debt.personEmoji} ${debt.personName}"); Text(formatCurrency(debt.amount - debt.amountPaid, debt.currency), color = PrimaryGreen, fontWeight = FontWeight.Bold) } }
            }
            item { BannerAdView() }
        }
        FloatingActionButton(onClick = onAddDebt, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) { Icon(Icons.Default.Add, null) }
    }
    if (showPrompt) ModalBottomSheet(onDismissRequest = { showPrompt = false; viewModel.dismissPrompt() }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("☁️ Back up your debts?", style = MaterialTheme.typography.titleLarge)
            Text("Sign in with Google to sync across devices. Your broke friends can't escape even if you change phones.")
            Button(onClick = { viewModel.dismissPrompt(); showPrompt = false }) { Text("Maybe Later") }
        }
    }
}

@Composable
private fun SummaryCard(title: String, amount: Double, brush: Brush, modifier: Modifier) {
    Column(modifier.background(brush, RoundedCornerShape(8.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = androidx.compose.ui.graphics.Color.White)
        Text(formatCurrency(amount), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.ExtraBold)
    }
}
