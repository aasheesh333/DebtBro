package com.dhanuk.debtbro.presentation.screens.adddebt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDebtScreen(onSaved: () -> Unit, viewModel: AddDebtViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add Debt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(state.personName, { viewModel.update { copy(personName = it, error = null) } }, label = { Text("Person name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(state.amount, { viewModel.update { copy(amount = it, error = null) } }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.type == "THEY_OWE_ME", { viewModel.update { copy(type = "THEY_OWE_ME") } }, label = { Text("They owe me") })
            FilterChip(state.type == "I_OWE_THEM", { viewModel.update { copy(type = "I_OWE_THEM") } }, label = { Text("I owe them") })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("🙂","😎","🤝","🍕","🚕","☕","🏠","💼","🎁","🎮","📚","✈️","🏖️","🎬","🥘","💸","🔥","⭐","👑","🧾").forEach { emoji ->
                Text(emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable { viewModel.update { copy(personEmoji = emoji) } }.padding(4.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("₹", "$", "€", "£").forEach { c -> FilterChip(state.currency == c, { viewModel.update { copy(currency = c) } }, label = { Text(c) }) }
        }
        OutlinedTextField(state.description, { viewModel.update { copy(description = it) } }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.dueDateDays, { viewModel.update { copy(dueDateDays = it.filter(Char::isDigit)) } }, label = { Text("Due in days") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(state.notes, { viewModel.update { copy(notes = it) } }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { viewModel.saveDebt(onSaved) }, modifier = Modifier.fillMaxWidth()) { Text("Save Debt") }
    }
}
