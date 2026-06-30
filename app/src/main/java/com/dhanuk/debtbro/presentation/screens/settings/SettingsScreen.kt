package com.dhanuk.debtbro.presentation.screens.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.presentation.components.GoogleSignInCard
import com.dhanuk.debtbro.presentation.components.LanguageSelectorGrid
import com.dhanuk.debtbro.presentation.components.SUPPORTED_LANGUAGES
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.LocalizedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extra = LocalExtraColors.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReauthHint by remember { mutableStateOf(false) }
    var showLinkEmailDialog by remember { mutableStateOf(false) }
    val showDeletionGraceAlert by viewModel.showDeletionGraceAlert.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = UITokens.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium),
            contentPadding = PaddingValues(top = UITokens.ScreenTopPadding, bottom = UITokens.ScreenBottomPadding)
        ) {
            item {
                Text(
                    LocalizedString.get("settings"),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = UITokens.FontDisplay,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ── ACCOUNT SECTION ──────────────────────────────────────────────
            item { SectionHeader(LocalizedString.get("account"), Icons.Default.AccountCircle) }
            item {
                GoogleSignInCard(
                    isSignedIn = state.isSignedIn,
                    name = state.googleName,
                    email = state.email,
                    userPhoto = state.userPhoto,
                    customAvatarUri = state.customAvatarUri,
                    lastSynced = state.lastSynced,
                    isSyncing = state.isSyncing,
                    syncMessage = state.syncMessage,
                    linkedProviders = state.linkedProviders,
                    onSignIn = { viewModel.signInWithGoogle(context as Activity) },
                    onSignOut = { showSignOutConfirm = true },
                    onSync = { viewModel.syncNow() },
                    onDeleteAccount = { showDeleteConfirm = true },
                    onLinkEmail = { showLinkEmailDialog = true },
                    pendingDeletionTimestamp = state.pendingDeletionTimestamp,
                    onCancelDeletion = { viewModel.cancelDeletion() }
                )
            }

            // ── THEME SELECTION ──────────────────────────────────────────────
            item { SectionHeader(LocalizedString.get("theme"), Icons.Default.Palette) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = UITokens.ShapeLarge
                ) {
                    Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)) {
                            listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                                val label = when (mode) {
                                    "LIGHT" -> LocalizedString.get("theme_light")
                                    "DARK" -> LocalizedString.get("theme_dark")
                                    else -> LocalizedString.get("theme_system")
                                }
                                FilterChip(
                                    selected = state.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(label, fontSize = UITokens.FontCaption) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── PREFERENCES SECTION ──────────────────────────────────────────
            item { SectionHeader(LocalizedString.get("preferences"), Icons.Default.Tune) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = UITokens.ShapeLarge
                ) {
                    Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium)) {
                        OutlinedTextField(
                            value = state.userName,
                            onValueChange = viewModel::saveUserName,
                            label = { Text(LocalizedString.get("display_name")) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Text(LocalizedString.get("default_currency"), color = extra.subtitleGray, fontSize = UITokens.FontSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)) {
                            listOf("\u20B9", "$", "\u20AC", "\u00A3", "\u00A5").forEach { c ->
                                FilterChip(
                                    selected = state.currency == c,
                                    onClick = { viewModel.setCurrency(c) },
                                    label = { Text(c) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        Text(LocalizedString.get("nudge_roast_level"), color = extra.subtitleGray, fontSize = UITokens.FontSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)) {
                            listOf("MILD", "MEDIUM", "SPICY").forEach { r ->
                                FilterChip(
                                    selected = state.roastLevel == r,
                                    onClick = { viewModel.setRoastLevel(r) },
                                    label = { Text(r) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = extra.divider)

                        Text(LocalizedString.get("card_display_options"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = UITokens.FontBody)

                        SettingsToggleItem(
                            title = LocalizedString.get("show_description"),
                            subtitle = LocalizedString.get("show_description_sub"),
                            checked = state.showDescription,
                            onCheckedChange = viewModel::setShowDescription
                        )

                        SettingsToggleItem(
                            title = LocalizedString.get("show_due_date"),
                            subtitle = LocalizedString.get("show_due_date_sub"),
                            checked = state.showDueDate,
                            onCheckedChange = viewModel::setShowDueDate
                        )

                        SettingsToggleItem(
                            title = LocalizedString.get("show_emoji"),
                            subtitle = LocalizedString.get("show_emoji_sub"),
                            checked = state.showEmoji,
                            onCheckedChange = viewModel::setShowEmoji
                        )
                    }
                }
            }

            // ── NOTIFICATIONS SECTION ────────────────────────────────────────
            item { SectionHeader(LocalizedString.get("notifications"), Icons.Default.Notifications) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = UITokens.ShapeLarge
                ) {
                    Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)) {
                        SettingsToggleItem(
                            title = LocalizedString.get("daily_reminders"),
                            subtitle = LocalizedString.get("daily_reminders_desc"),
                            checked = state.notifyDailyReminder,
                            onCheckedChange = viewModel::setNotifyDailyReminder
                        )
                        SettingsToggleItem(
                            title = LocalizedString.get("weekly_summary"),
                            subtitle = LocalizedString.get("weekly_summary_desc"),
                            checked = state.notifyWeeklySummary,
                            onCheckedChange = viewModel::setNotifyWeeklySummary
                        )
                        SettingsToggleItem(
                            title = LocalizedString.get("payment_alerts"),
                            subtitle = LocalizedString.get("payment_alerts_desc"),
                            checked = state.notifyPaymentAlerts,
                            onCheckedChange = viewModel::setNotifyPaymentAlerts
                        )
                    }
                }
            }

            // ── LANGUAGE SECTION ─────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = UITokens.ShapeLarge
                ) {
                    Row(
                        modifier = Modifier.padding(UITokens.CardInnerPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)
                    ) {
                        Box(
                            modifier = Modifier.size(UITokens.AvatarMedium).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Language, LocalizedString.get("language"), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(LocalizedString.get("language"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            val currentLang = SUPPORTED_LANGUAGES.find { it.code == state.selectedLanguage }
                            Text(
                                currentLang?.nativeName ?: "English",
                                color = extra.subtitleGray,
                                fontSize = UITokens.FontSmall
                            )
                        }
                        Icon(Icons.Default.ChevronRight, LocalizedString.get("language"), tint = extra.subtitleGray)
                    }
                }
            }

            // ── DATA SECTION ─────────────────────────────────────────────────
            item { SectionHeader(LocalizedString.get("data_export"), Icons.Default.Storage) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = UITokens.ShapeLarge
                ) {
                    Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)) {
                        SettingsActionItem(
                            title = LocalizedString.get("export_csv"),
                            subtitle = LocalizedString.get("export_csv_subtitle"),
                            icon = Icons.Default.FileUpload,
                            onClick = { viewModel.exportCsv(context) }
                        )
                        HorizontalDivider(color = extra.divider)
                        var showClearConfirm by remember { mutableStateOf(false) }

                        SettingsActionItem(
                            title = LocalizedString.get("clear_settled"),
                            subtitle = LocalizedString.get("clear_settled_subtitle"),
                            icon = Icons.Default.DeleteSweep,
                            color = MaterialTheme.colorScheme.error,
                            onClick = { showClearConfirm = true }
                        )

                        if (showClearConfirm) {
                            AlertDialog(
                                onDismissRequest = { showClearConfirm = false },
                                containerColor = MaterialTheme.colorScheme.surface,
                                title = { Text(LocalizedString.get("clear_settled_dialog"), color = MaterialTheme.colorScheme.onSurface) },
                                text = { Text(LocalizedString.get("clear_settled_dialog_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.clearSettledDebts()
                                        showClearConfirm = false
                                    }) {
                                        Text(LocalizedString.get("delete_all"), color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearConfirm = false }) {
                                        Text(LocalizedString.get("cancel"), color = extra.subtitleGray)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── PRIVACY POLICY ───────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val url = BuildConfig.PRIVACY_POLICY_URL
                        if (url.isNotBlank()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = UITokens.ShapeLarge
                ) {
                    Row(
                        modifier = Modifier.padding(UITokens.CardInnerPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)
                    ) {
                        Box(
                            modifier = Modifier.size(UITokens.AvatarMedium).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Policy, LocalizedString.get("privacy_policy"), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(LocalizedString.get("privacy_policy"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            Text(LocalizedString.get("privacy_policy_desc"), color = extra.subtitleGray, fontSize = UITokens.FontSmall)
                        }
                        Icon(Icons.Default.OpenInNew, LocalizedString.get("privacy_policy"), tint = extra.subtitleGray, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── ABOUT SECTION ────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("DebtBro v${BuildConfig.VERSION_NAME}", color = extra.subtitleGray, fontSize = UITokens.FontSmall)
                    Text(LocalizedString.get("made_with_love"), color = extra.subtitleGray.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }
    }

    // ── Language Dialog ────────────────────────────────────────────────────
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LocalizedString.get("select_language"), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                LanguageSelectorGrid(
                    selectedCode = state.selectedLanguage,
                    onLanguageSelected = {
                        viewModel.setLanguage(it.code)
                        showLanguageDialog = false
                    }
                )
            },
            confirmButton = {}
        )
    }

    // ── Sign-out Confirmation ──────────────────────────────────────────────
    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LocalizedString.get("sign_out_question"), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(LocalizedString.get("sign_out_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        viewModel.signOut()
                    },
                    enabled = !state.isSigningOut
                ) {
                    if (state.isSigningOut) CircularProgressIndicator(modifier = Modifier.size(UITokens.IconSmall), strokeWidth = 2.dp)
                    Text(LocalizedString.get("sign_out"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(LocalizedString.get("cancel"), color = extra.subtitleGray)
                }
            }
        )
    }

    // ── Delete Account Confirmation (GDPR) ─────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LocalizedString.get("deletion_requested"), color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    LocalizedString.get("deletion_grace_info"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.requestAccountDeletion { }
                    },
                    enabled = !state.isDeletingAccount
                ) {
                    if (state.isDeletingAccount) CircularProgressIndicator(modifier = Modifier.size(UITokens.IconSmall), strokeWidth = 2.dp)
                    Text(LocalizedString.get("delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(LocalizedString.get("cancel"), color = extra.subtitleGray)
                }
            }
        )
    }

    // ── Re-auth Hint ───────────────────────────────────────────────────────
    if (showReauthHint) {
        AlertDialog(
            onDismissRequest = { showReauthHint = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LocalizedString.get("reauth_required"), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(LocalizedString.get("reauth_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showReauthHint = false
                    val activity = context as? Activity
                    if (activity != null) {
                        viewModel.signInWithGoogle(activity)
                    }
                }) { Text(LocalizedString.get("sign_in_again"), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showReauthHint = false }) { Text(LocalizedString.get("cancel"), color = extra.subtitleGray) }
            }
        )
    }

    // ── Grace Period Login Alert ──────────────────────────────────────────
    if (showDeletionGraceAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeletionGraceAlert() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LocalizedString.get("deletion_grace_title"), color = MaterialTheme.colorScheme.error) },
            text = { Text(LocalizedString.get("deletion_grace_alert"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelDeletion() }) {
                    Text(LocalizedString.get("cancel_deletion"), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeletionGraceAlert() }) {
                    Text(LocalizedString.get("proceed_login"), color = extra.subtitleGray)
                }
            }
        )
    }

    // ── Link Email Dialog ──────────────────────────────────────────────────
    if (showLinkEmailDialog) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLinkEmailDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LocalizedString.get("add_email_password"), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)) {
                    Text(LocalizedString.get("link_email_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = UITokens.FontSmall)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(LocalizedString.get("email")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(LocalizedString.get("password_6_chars")) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showLinkEmailDialog = false
                    viewModel.linkEmailPassword(email, password, context as Activity)
                }) { Text(LocalizedString.get("link"), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showLinkEmailDialog = false }) { Text(LocalizedString.get("cancel"), color = extra.subtitleGray) }
            }
        )
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = UITokens.SpaceXS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)
    ) {
        Icon(icon, LocalizedString.get("settings"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(UITokens.IconMedium))
        Text(title, color = MaterialTheme.colorScheme.primary, fontSize = UITokens.FontBody, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val extra = LocalExtraColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = UITokens.SpaceTiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = extra.subtitleGray, fontSize = UITokens.FontCaption)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = extra.subtitleGray,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun SettingsActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val extra = LocalExtraColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = UITokens.SpaceXS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)
    ) {
        Icon(icon, LocalizedString.get("data_export"), tint = if (color == MaterialTheme.colorScheme.onSurface) extra.subtitleGray else color)
        Column {
            Text(title, color = color, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = extra.subtitleGray, fontSize = UITokens.FontCaption)
        }
    }
}
