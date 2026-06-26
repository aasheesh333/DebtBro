package com.dhanuk.debtbro.presentation.screens.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            item {
                Text(
                    LocalizedString.get("settings"),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
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
                    onLinkEmail = { showLinkEmailDialog = true }
                )
            }

            // ── THEME SELECTION ──────────────────────────────────────────────
            item { SectionHeader("Theme", Icons.Default.Palette) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                                val label = when (mode) {
                                    "LIGHT" -> "\u2600\uFE0F Light"
                                    "DARK" -> "\uD83C\uDF19 Dark"
                                    else -> "\uD83D\uDD04 System"
                                }
                                FilterChip(
                                    selected = state.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryGreen,
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
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = state.userName,
                            onValueChange = viewModel::saveUserName,
                            label = { Text(LocalizedString.get("display_name")) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryGreen,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Text(LocalizedString.get("default_currency"), color = extra.subtitleGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("\u20B9", "$", "\u20AC", "\u00A3", "\u00A5").forEach { c ->
                                FilterChip(
                                    selected = state.currency == c,
                                    onClick = { viewModel.setCurrency(c) },
                                    label = { Text(c) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryGreen,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        Text(LocalizedString.get("nudge_roast_level"), color = extra.subtitleGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("MILD", "MEDIUM", "SAVAGE").forEach { r ->
                                FilterChip(
                                    selected = state.roastLevel == r,
                                    onClick = { viewModel.setRoastLevel(r) },
                                    label = { Text(r) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryGreen,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = extra.divider)

                        Text(LocalizedString.get("card_display_options"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)

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
            item { SectionHeader("Notifications", Icons.Default.Notifications) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsToggleItem(
                            title = "Daily Reminders",
                            subtitle = "Get reminded about overdue debts",
                            checked = state.notifyDailyReminder,
                            onCheckedChange = viewModel::setNotifyDailyReminder
                        )
                        SettingsToggleItem(
                            title = "Weekly Summary",
                            subtitle = "Receive a weekly debt summary",
                            checked = state.notifyWeeklySummary,
                            onCheckedChange = viewModel::setNotifyWeeklySummary
                        )
                        SettingsToggleItem(
                            title = "Payment Alerts",
                            subtitle = "Get notified when debts are nearly settled",
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
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Language, null, tint = PrimaryGreen)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(LocalizedString.get("language"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            val currentLang = SUPPORTED_LANGUAGES.find { it.code == state.selectedLanguage }
                            Text(
                                currentLang?.nativeName ?: "English",
                                color = extra.subtitleGray,
                                fontSize = 13.sp
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = extra.subtitleGray)
                    }
                }
            }

            // ── DATA SECTION ─────────────────────────────────────────────────
            item { SectionHeader(LocalizedString.get("data_export"), Icons.Default.Storage) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Policy, null, tint = PrimaryGreen)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Privacy Policy", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            Text("View our data practices", color = extra.subtitleGray, fontSize = 13.sp)
                        }
                        Icon(Icons.Default.OpenInNew, null, tint = extra.subtitleGray, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── ABOUT SECTION ────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("DebtBro v${BuildConfig.VERSION_NAME}", color = extra.subtitleGray, fontSize = 13.sp)
                    Text("Made with \u2764\uFE0F for financial chaos", color = extra.subtitleGray.copy(alpha = 0.7f), fontSize = 11.sp)
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
            title = { Text("Sign Out?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Your local data stays on this device. Cloud backups remain on your Google account.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        viewModel.signOut()
                    },
                    enabled = !state.isSigningOut
                ) {
                    if (state.isSigningOut) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel", color = extra.subtitleGray)
                }
            }
        )
    }

    // ── Delete Account Confirmation (GDPR) ─────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete Account?", color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    "This permanently deletes your account and ALL cloud data. This action cannot be undone. Local data on this device will also be cleared.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteAccount(
                            context = context,
                            onReauthRequired = { showReauthHint = true },
                            onFailure = {}
                        )
                    },
                    enabled = !state.isDeletingAccount
                ) {
                    if (state.isDeletingAccount) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = extra.subtitleGray)
                }
            }
        )
    }

    // ── Re-auth Hint ───────────────────────────────────────────────────────
    if (showReauthHint) {
        AlertDialog(
            onDismissRequest = { showReauthHint = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Re-authentication required", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("For your security, please sign in with Google again before deleting your account.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showReauthHint = false
                    val activity = context as? Activity
                    if (activity != null) {
                        viewModel.signInWithGoogle(activity)
                    }
                }) { Text("Sign in again", color = PrimaryGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showReauthHint = false }) { Text("Cancel", color = extra.subtitleGray) }
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
            title = { Text("Add Email/Password", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Link an email so you can sign in without Google.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (6+ chars)") },
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
                }) { Text("Link", color = PrimaryGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showLinkEmailDialog = false }) { Text("Cancel", color = extra.subtitleGray) }
            }
        )
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
        Text(title, color = PrimaryGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = extra.subtitleGray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = PrimaryGreen,
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
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = if (color == MaterialTheme.colorScheme.onSurface) extra.subtitleGray else color)
        Column {
            Text(title, color = color, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = extra.subtitleGray, fontSize = 12.sp)
        }
    }
}
