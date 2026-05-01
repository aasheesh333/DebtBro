package com.dhanuk.debtbro.presentation.screens.debtdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.components.ConfettiOverlay
import com.dhanuk.debtbro.presentation.components.EmptyStateView
import com.dhanuk.debtbro.presentation.components.LoadingDotsIndicator
import com.dhanuk.debtbro.util.copyToClipboard
import com.dhanuk.debtbro.util.formatCurrency
import com.dhanuk.debtbro.util.shareTextToWhatsApp
import com.dhanuk.debtbro.util.toReadableDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(onBack: () -> Unit, viewModel: DebtDetailViewModel = hiltViewModel()) {
    val debt by viewModel.debt.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val ai by viewModel.aiMessage.collectAsStateWithLifecycle()
    val loading by viewModel.isGeneratingAi.collectAsStateWithLifecycle()
    val level by viewModel.roastLevel.collectAsStateWithLifecycle()
    val showSheet by viewModel.showAddPaymentSheet.collectAsStateWithLifecycle()
    val confetti by viewModel.showConfetti.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val d = debt
    if (d == null) {
        EmptyStateView("🧾", "Debt not found", "It may have been deleted.")
        return
    }
    Column {
        TopAppBar(title = { Text(d.personName) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.personEmoji, fontSize = 80.sp)
                    val remaining = (d.amount - d.amountPaid).coerceAtLeast(0.0)
                    Text(formatCurrency(remaining, d.currency), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                    LinearProgressIndicator(progress = { (d.amountPaid / d.amount).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("Created ${d.createdAt.toReadableDate()} → ${d.status.lowercase().replaceFirstChar { it.uppercase() }}")
                }
            }
            item { Button(onClick = { viewModel.showAddPaymentSheet.value = true }, modifier = Modifier.fillMaxWidth()) { Text("➕ Add Payment") } }
            item {
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🤖 BroBot Says...", style = MaterialTheme.typography.titleLarge)
                    if (loading) LoadingDotsIndicator(color = MaterialTheme.colorScheme.primary) else Text(ai.ifBlank { d.aiRoastGenerated ?: "Generate your first roast →" })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("MILD", "MEDIUM", "SAVAGE").forEach { FilterChip(level == it, {}, label = { Text(it.lowercase().replaceFirstChar { c -> c.uppercase() }) }) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::generateRoast) { Text("Generate") }
                        Button(onClick = { copyToClipboard(context, ai.ifBlank { d.aiRoastGenerated.orEmpty() }) }) { Text("Copy") }
                        Button(onClick = { shareTextToWhatsApp(context, ai.ifBlank { d.aiRoastGenerated.orEmpty() }) }) { Text("WhatsApp") }
                    }
                } }
            }
            item { Button(onClick = { viewModel.shareCard(context, d, ai.ifBlank { d.aiRoastGenerated.orEmpty() }) }, modifier = Modifier.fillMaxWidth()) { Text("Share Card") } }
            item { Button(onClick = viewModel::markSettled, modifier = Modifier.fillMaxWidth()) { Text("✅ Mark as Settled") } }
            item { Text("Payment history", style = MaterialTheme.typography.titleLarge) }
            if (payments.isEmpty()) item { Text("No payments recorded yet.") }
            items(payments) { p -> Text("${formatCurrency(p.amount, d.currency)} • ${p.paidAt.toReadableDate()} • ${p.note.orEmpty()}") }
        }
    }
    ConfettiOverlay(confetti)
    if (showSheet) AddPaymentBottomSheet(remaining = (d.amount - d.amountPaid).coerceAtLeast(0.0), currency = d.currency, onDismiss = { viewModel.showAddPaymentSheet.value = false }, onSave = viewModel::addPayment)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPaymentBottomSheet(remaining: Double, currency: String, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf(remaining.toInt().toString()) }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add Payment", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount $currency") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { amount.toDoubleOrNull()?.let { onSave(it, note) } }, modifier = Modifier.fillMaxWidth()) { Text("Save Payment") }
        }
    }
}
