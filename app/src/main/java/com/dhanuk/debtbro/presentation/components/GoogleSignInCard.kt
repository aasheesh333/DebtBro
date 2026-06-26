package com.dhanuk.debtbro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.util.toTimeAgo

@Composable
fun GoogleSignInCard(
    isSignedIn: Boolean,
    name: String,
    email: String,
    userPhoto: String,
    customAvatarUri: String,
    lastSynced: Long,
    isSyncing: Boolean,
    syncMessage: String,
    linkedProviders: List<String>,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSync: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLinkEmail: () -> Unit
) {
    val extra = LocalExtraColors.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isSignedIn) "Cloud backup active" else "Back up your debts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isSignedIn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PrimaryGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarUri = customAvatarUri.ifBlank { userPhoto }
                        if (avatarUri.isNotBlank()) {
                            AsyncImage(
                                model = avatarUri,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                name.take(1).ifBlank { "?" },
                                color = PrimaryGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            name.ifBlank { "DebtBro user" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            email,
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.subtitleGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    "Last synced: ${if (lastSynced == 0L) "never" else lastSynced.toTimeAgo()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.subtitleGray
                )

                if (linkedProviders.isNotEmpty()) {
                    Text(
                        "Linked: ${linkedProviders.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.subtitleGray
                    )
                }
            } else {
                Text(
                    "Sign in with Google to sync debts across devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSyncing) {
                LinearProgressIndicator(modifier = Modifier.padding(vertical = 4.dp))
                if (syncMessage.isNotBlank()) {
                    Text(syncMessage, style = MaterialTheme.typography.bodySmall, color = extra.subtitleGray)
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

            if (isSignedIn) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if ("password" !in linkedProviders) {
                        TextButton(onClick = onLinkEmail) {
                            Text("Add Email Login", fontSize = 12.sp, color = PrimaryGreen)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDeleteAccount) {
                        Text("Delete Account", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
