package com.dhanuk.debtbro.presentation.screens.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.components.BannerAdView
import com.dhanuk.debtbro.util.formatCurrency

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitScreen(viewModel: SplitViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val past by viewModel.pastSplits.collectAsStateWithLifecycle()
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("New Split", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(state.title, viewModel::updateTitle, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.totalAmount, viewModel::updateTotal, label = { Text("Total amount") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(state.participantName, viewModel::updateParticipant, label = { Text("Participant") }, modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.addParticipant() }) { Text("+") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.participants.forEach { name -> FilterChip(true, { viewModel.removeParticipant(name) }, label = { Text(name) }) } }
                Text("${formatCurrency(state.perPerson)} per person", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { viewModel.createSplit { viewModel.getAiSummary(it) } }, modifier = Modifier.fillMaxWidth()) { Text("Create Split") }
                if (state.aiSummary.isNotBlank()) Text("🤖 ${state.aiSummary}")
            } }
        }
        item { Text("Past Splits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(past) { split ->
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(split.title, fontWeight = FontWeight.Bold)
                Text("${formatCurrency(split.totalAmount)} total • ${formatCurrency(split.perPersonAmount)} each")
                split.aiSummary?.let { Text("🤖 $it") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.getAiSummary(split) }) { Text("🤖 Get AI Take") }
                    Button(onClick = { viewModel.createDebtsFromSplit(split) }) { Text("Create Debts") }
                }
            } }
        }
        item { BannerAdView() }
    }
}
