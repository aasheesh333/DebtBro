package com.dhanuk.debtbro.presentation.screens.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.presentation.components.GoogleSignInCard

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item { Section("ACCOUNT") { GoogleSignInCard(state.isSignedIn, state.googleName, state.email, state.lastSynced, { viewModel.signInWithGoogle(context as Activity) }, viewModel::signOut, viewModel::syncNow) } }
        item {
            Section("PREFERENCES") {
                OutlinedTextField(state.userName, viewModel::saveUserName, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("₹", "$", "€", "£").forEach { c -> FilterChip(state.currency == c, { viewModel.setCurrency(c) }, label = { Text(c) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("MILD", "MEDIUM", "SAVAGE").forEach { r -> FilterChip(state.roastLevel == r, { viewModel.setRoastLevel(r) }, label = { Text(r) }) } }
            }
        }
        item { Section("NOTIFICATIONS") { Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text("Daily reminders"); Switch(checked = true, onCheckedChange = {}) } } }
        item {
            Section("AI") {
                OutlinedTextField(state.groqKey, viewModel::saveGroqKey, label = { Text("Groq API key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(viewModel::testGroqConnection) { Text("Test") }; Text(state.testResult) }
            }
        }
        item {
            Section("DATA") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.exportCsv(context) }) { Text("Export CSV") }
                    Button(onClick = viewModel::clearSettledDebts) { Text("Clear Settled") }
                }
                if (state.isSignedIn) Button(onClick = viewModel::syncNow) { Text("Force Sync") }
            }
        }
        item { Section("ABOUT") { Text("DebtBro ${BuildConfig.VERSION_NAME}\nMoney memory with a sense of humor.") } }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.labelLarge); content() } }
}
