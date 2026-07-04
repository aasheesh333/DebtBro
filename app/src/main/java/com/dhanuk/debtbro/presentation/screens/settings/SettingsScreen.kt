package com.dhanuk.debtbro.presentation.screens.settings

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.presentation.components.GoogleSignInCard
import com.dhanuk.debtbro.presentation.components.LanguageSelectorGrid
import com.dhanuk.debtbro.presentation.components.NotificationsPermissionCard
import com.dhanuk.debtbro.presentation.components.SUPPORTED_LANGUAGES
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.ActivityFinder
import com.dhanuk.debtbro.util.LocalizedString
import com.dhanuk.debtbro.util.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extra = LocalExtraColors.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // P0-1 (2026-07-03): two-step delete flow.
    // Step 1 — `showDeleteConfirm` opens when the user taps "Delete
    //   Account" in the link list. Shows a single "Delete Now" button.
    // Step 2 — tapping "Delete Now" opens `showDeleteOptionsDialog`
    //   which presents the actual two paths: "Delete Immediately"
    //   (fires requestImmediateDeletion) and "Request Account Deletion"
    //   (the 24h reversible grace path).
    // The intermediate step gives the user one extra moment to back out
    // before seeing the irreversible/option distinction, matching the
    // pattern used by Google / WhatsApp account-deletion flows.
    var showDeleteOptionsDialog by remember { mutableStateOf(false) }
    var showReauthHint by remember { mutableStateOf(false) }
    var showLinkEmailDialog by remember { mutableStateOf(false) }
    val showDeletionGraceAlert by viewModel.showDeletionGraceAlert.collectAsStateWithLifecycle()

    // CSV export: on API ≤ 28, WRITE_EXTERNAL_STORAGE must be requested at
    // runtime. If the user denies, CsvExporter falls back to writing the
    // file to app cache and sharing it via FileProvider (so the export
    // still works — just not saved to the public Downloads folder). The
    // launcher always forwards to exportCsv regardless of grant result;
    // the only effect of denial is WHERE the CSV lands.
    val csvPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.exportCsv(context)
    }

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
                    onSignIn = { ActivityFinder.find(context)?.let { viewModel.signInWithGoogle(it) } },
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

            item { SectionHeader(LocalizedString.get("notifications"), Icons.Default.Notifications) }
            item { NotificationsPermissionCard() }
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
                            onClick = {
                                // API ≤ 28 (P and below) requires runtime
                                // WRITE_EXTERNAL_STORAGE for the public
                                // Downloads write path. API ≥ Q uses
                                // MediaStore (no permission needed).
                                val needsPermission =
                                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != PackageManager.PERMISSION_GRANTED
                                if (needsPermission) {
                                    csvPermissionLauncher.launch(
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                } else {
                                    viewModel.exportCsv(context)
                                }
                            }
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

            // ── LEGAL & SUPPORT SECTION ───────────────────────────────────────
            item { SectionHeader(LocalizedString.get("legal_and_support"), Icons.Default.Gavel) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = UITokens.ShapeLarge
                ) {
                    Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        LinkRow(
                            icon = Icons.Default.Policy,
                            title = LocalizedString.get("privacy_policy"),
                            subtitle = LocalizedString.get("privacy_policy_desc"),
                            onClick = { openUrl(context, BuildConfig.PRIVACY_POLICY_URL) }
                        )
                        HorizontalDivider(color = extra.divider)
                        LinkRow(
                            icon = Icons.Default.Description,
                            title = LocalizedString.get("terms_and_conditions"),
                            subtitle = LocalizedString.get("terms_and_conditions"),
                            onClick = { openUrl(context, BuildConfig.TERMS_OF_SERVICE_URL) }
                        )
                        HorizontalDivider(color = extra.divider)
                        LinkRow(
                            icon = Icons.Default.HelpOutline,
                            title = LocalizedString.get("help_and_support"),
                            subtitle = LocalizedString.get("help_and_support"),
                            onClick = { openUrl(context, BuildConfig.HELP_URL) }
                        )
                        HorizontalDivider(color = extra.divider)
                        LinkRow(
                            icon = Icons.Default.Email,
                            title = LocalizedString.get("contact_us"),
                            subtitle = "support@dhanuksoftwares.com",
                            onClick = {
                                runCatching {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                        data = android.net.Uri.parse("mailto:support@dhanuksoftwares.com")
                                    }
                                    context.startActivity(intent)
                                }.onFailure {
                                    runCatching { android.widget.Toast.makeText(context, LocalizedString.get("no_browser_found"), android.widget.Toast.LENGTH_SHORT).show() }
                                }
                            }
                        )
                        HorizontalDivider(color = extra.divider)
                        LinkRow(
                            icon = Icons.Default.DeleteForever,
                            title = LocalizedString.get("delete_account"),
                            subtitle = LocalizedString.get("delete_account"),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { showDeleteConfirm = true }
                        )
                    }
                }
            }

            // ── ABOUT SECTION ────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("DebtPayoff Pro v${BuildConfig.VERSION_NAME}", color = extra.subtitleGray, fontSize = UITokens.FontSmall)
                    Text(
                        if (state.isSignedIn) LocalizedString.get("cloud_backup_active") else LocalizedString.get("data_saved_locally"),
                        color = extra.subtitleGray.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
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

    // ── Delete Account — Step 1: entry confirmation ────────────────────────
    // P0-1 (2026-07-03): first of two dialogs. The user just tapped
    // "Delete Account" in the link list. This dialog presents a single
    // "Delete Now" button which then advances to the options dialog
    // (`showDeleteOptionsDialog`) below.
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
                        showDeleteOptionsDialog = true
                    }
                ) {
                    Text(LocalizedString.get("delete_now_button"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(LocalizedString.get("cancel"), color = extra.subtitleGray)
                }
            }
        )
    }

    // ── Delete Account — Step 2: choose immediate vs grace ──────────────────
    // P0-1 (2026-07-03): second dialog, shown only after the user has
    // explicitly tapped "Delete Now" in the step-1 dialog above.
    // Two paths:
    //   - **Delete Immediately** — calls [SettingsViewModel.requestImmediateDeletion]
    //     which delegates to [SettingsViewModel.deleteAccount] and also
    //     cancels any previously-scheduled WorkManager grace backup.
    //   - **Request Account Deletion** — calls [SettingsViewModel.requestAccountDeletion]
    //     which records the 24h grace timestamp, enqueues the WorkManager
    //     one-shot backup, and lets the user cancel by signing back in
    //     within the 24h window.
    if (showDeleteOptionsDialog) {
        var reauthMessage by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = {
                showDeleteOptionsDialog = false
                reauthMessage = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LocalizedString.get("deletion_requested"), color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text(
                        LocalizedString.get("choose_how_to_proceed"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = UITokens.FontBody,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "• Delete Immediately — immediate. Cannot be undone.\n" +
                            "• Request Account Deletion — reversible by signing back in within 24h.",
                        color = extra.subtitleGray,
                        fontSize = UITokens.FontBody
                    )
                    reauthMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            msg,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = UITokens.FontBody
                        )
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            showDeleteOptionsDialog = false
                            viewModel.requestImmediateDeletion(
                                context = context,
                                onReauthRequired = {
                                    reauthMessage = "Re-sign in to confirm deletion — your last sign-in was too long ago for Firebase to allow account deletion."
                                    showDeleteOptionsDialog = true
                                },
                                onFailure = { msg ->
                                    reauthMessage = msg
                                    showDeleteOptionsDialog = true
                                }
                            )
                        },
                        enabled = !state.isDeletingAccount
                    ) {
                        if (state.isDeletingAccount) CircularProgressIndicator(modifier = Modifier.size(UITokens.IconSmall), strokeWidth = 2.dp)
                        Text(LocalizedString.get("delete_immediately_button"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            showDeleteOptionsDialog = false
                            viewModel.requestAccountDeletion(context) { }
                        }
                    ) {
                        Text(LocalizedString.get("request_account_deletion_button"), color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteOptionsDialog = false
                    reauthMessage = null
                }) {
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
                    ActivityFinder.find(context)?.let { viewModel.linkEmailPassword(email, password, it) }
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

@Composable
fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val extra = LocalExtraColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = UITokens.SpaceXS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)
    ) {
        Icon(icon, title, tint = tint)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = UITokens.FontBody)
            Text(subtitle, color = extra.subtitleGray, fontSize = UITokens.FontCaption)
        }
        Icon(Icons.Default.OpenInNew, title, tint = extra.subtitleGray, modifier = Modifier.size(16.dp))
    }
}
