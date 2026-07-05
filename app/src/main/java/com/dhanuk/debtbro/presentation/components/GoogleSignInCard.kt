package com.dhanuk.debtbro.presentation.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.LocalizedString
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
    pendingDeletionTimestamp: Long = 0L,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSync: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLinkEmail: () -> Unit,
    onCancelDeletion: () -> Unit = {}
) {
    val extra = LocalExtraColors.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isSignedIn) LocalizedString.get("cloud_backup_active") else LocalizedString.get("back_up_debts"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isSignedIn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)
                ) {
                    Box(
                        modifier = Modifier
                            .size(UITokens.AvatarMedium)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarUri = customAvatarUri.ifBlank { userPhoto }
                        if (avatarUri.isNotBlank()) {
                            AsyncImage(
                                model = avatarUri,
                                contentDescription = "User avatar",
                                modifier = Modifier.size(UITokens.AvatarMedium).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                name.take(1).ifBlank { "?" },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            name.ifBlank { LocalizedString.get("debtbro_user") },
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
                    LocalizedString.get("sign_in_google_sync"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (pendingDeletionTimestamp > 0L) {
                Surface(
                    shape = UITokens.ShapeSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            LocalizedString.get("deletion_grace_title"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            LocalizedString.get("deletion_grace_info"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onCancelDeletion) {
                            Text(LocalizedString.get("cancel_deletion"), color = MaterialTheme.colorScheme.primary, fontSize = UITokens.FontCaption)
                        }
                    }
                }
            }

            if (isSyncing) {
                LinearProgressIndicator(modifier = Modifier.padding(vertical = 4.dp))
                if (syncMessage.isNotBlank()) {
                    Text(syncMessage, style = MaterialTheme.typography.bodySmall, color = extra.subtitleGray)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)) {
                if (isSignedIn) {
                    Button(onClick = onSync, enabled = !isSyncing) { Text(LocalizedString.get("sync_now")) }
                    OutlinedButton(onClick = onSignOut, enabled = !isSyncing) { Text(LocalizedString.get("sign_out")) }
                } else {
                    Button(onClick = onSignIn) { Text(LocalizedString.get("sign_in_google")) }
                }
            }

            if (isSignedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDeleteAccount) {
                        Text(LocalizedString.get("delete_account"), fontSize = UITokens.FontCaption, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
