package com.dhanuk.debtbro.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dhanuk.debtbro.util.toTimeAgo

@Composable
fun GoogleSignInCard(
    isSignedIn: Boolean,
    name: String,
    email: String,
    lastSynced: Long,
    isSyncing: Boolean = false,
    syncMessage: String = "",
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSync: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isSignedIn) "Cloud backup active" else "Back up your debts",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (isSignedIn) "${name.ifBlank { "DebtBro user" }}\n$email\nLast synced: ${if (lastSynced == 0L) "never" else lastSynced.toTimeAgo()}" else "Sign in with Google to sync debts across devices.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (isSyncing) {
                LinearProgressIndicator(modifier = Modifier.padding(vertical = 4.dp))
                if (syncMessage.isNotBlank()) {
                    Text(syncMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSignedIn) {
                    Button(onClick = onSync, enabled = !isSyncing) { Text("Sync Now") }
                    OutlinedButton(onClick = onSignOut, enabled = !isSyncing) { Text("Sign Out") }
                } else {
                    Button(onClick = onSignIn) { Text("Sign in with Google") }
                }
            }
        }
    }
}
