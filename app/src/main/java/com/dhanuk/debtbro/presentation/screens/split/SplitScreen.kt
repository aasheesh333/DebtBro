package com.dhanuk.debtbro.presentation.screens.split

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.formatCurrency
import com.dhanuk.debtbro.util.LocalizedString

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitScreen(onAuthRequired: () -> Unit, viewModel: SplitViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pastSplits by viewModel.pastSplits.collectAsStateWithLifecycle()
    val showAuthPrompt by viewModel.showAuthPrompt.collectAsStateWithLifecycle()
    val showVerifyGate by viewModel.showVerifyGate.collectAsStateWithLifecycle()
    val splitsWithDebts by viewModel.splitsWithDebtsCreated.collectAsStateWithLifecycle()
    val showRewardAd by viewModel.showRewardAd.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var showContactPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(Unit) { viewModel.preloadRewardedAd(context) }

    // Interstitial after createDebtsFromSplit — driven by SplitViewModel's
    // showInterstitial SharedFlow. Same pattern as AddDebtBottomSheet and
    // DebtDetailScreen.
    LaunchedEffect(Unit) {
        viewModel.showInterstitial.collect {
            activity?.let { a -> viewModel.showInterstitialIfReady(a) }
        }
    }

    // Box (instead of an inner Scaffold) so we don't re-apply WindowInsets.systemBars
    // — the outer NavGraph Scaffold already accounts for the bottom nav bar inset.
    // The previous inner Scaffold caused a duplicate inset strip above the nav bar.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = UITokens.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall),
            contentPadding = PaddingValues(
                top = UITokens.ScreenTopPadding,
                bottom = UITokens.ScreenBottomPadding
            )
        ) {
        item {
            Text(
                LocalizedString.get("split_bill"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = UITokens.FontDisplay,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = UITokens.ShapeLarge
            ) {
                Column(
                    modifier = Modifier.padding(UITokens.CardInnerPadding),
                    verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium)
                ) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { viewModel.updateTitle(it) },
                        label = { Text(LocalizedString.get("whats_this_for")) },
                        placeholder = { Text(LocalizedString.get("goa_trip_placeholder")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    OutlinedTextField(
                        value = state.totalAmount,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) viewModel.updateTotal(it) },
                        label = { Text(LocalizedString.get("total_amount")) },
                        placeholder = { Text("0.00") },
                        prefix = { Text(state.currencySymbol, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    AddParticipantRow(
                        value = state.participantName,
                        onValueChange = { viewModel.updateParticipant(it) },
                        onAdd = { viewModel.addParticipant() },
                        onPickContact = { showContactPicker = true }
                    )

                    // Participants Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS),
                        verticalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)
                    ) {
                        state.participants.forEach { name ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.removeParticipant(name) },
                                label = { Text(name) },
                                trailingIcon = {
                                    if (name != LocalizedString.get("me")) {
                                        Icon(
                                            Icons.Default.Close,
                                            LocalizedString.get("cancel"),
                            modifier = Modifier.size(UITokens.IconSmall)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = null
                            )
                        }
                    }

                    // Result
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(LocalizedString.get("per_person"), color = extra.subtitleGray, fontSize = UITokens.FontCaption)
                            Text(
                                formatCurrency(state.perPerson, state.currencySymbol),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                LocalizedString.get("among_people").replace("{count}", state.participants.size.toString()),
                                color = extra.subtitleGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.createSplit { viewModel.getAiSummary(it, activity) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(UITokens.ButtonHeight),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = UITokens.ShapeMedium,
                        enabled = state.totalAmount.isNotEmpty() && state.participants.size > 1
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(UITokens.IconLarge))
                        } else {
                            Text(LocalizedString.get("create_split"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    AnimatedVisibility(visible = state.aiSummary.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🤖", fontSize = 24.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(state.aiSummary, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                LocalizedString.get("past_splits"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(pastSplits, key = { it.id }) { split ->
            SplitItemCard(
                split,
                onGetAi = { viewModel.getAiSummary(it, activity) },
                onCreateDebts = { viewModel.createDebtsFromSplit(it) },
                debtsCreated = split.id in splitsWithDebts
            )
        }
    }

    // SnackbarHost as a sibling of the LazyColumn — overlays the list
    // for short-lived messages (e.g. "AI Take failed, try again").
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )

    // Auth prompt is rendered OUTSIDE the LazyColumn so the AlertDialog
    // behaves as a top-level overlay (dim + center), not as a child slot
    // of a LazyColumn item Card — which was visually broken (the dialog
    // appeared inside the Card flow and distorted layout). Also fixes the
    // balance of the previously-malformed block at the bottom of the
    // create-split Card. (offline-mode audit 2026-07-03.)
    if (showAuthPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAuthPrompt() },
            title = { Text(LocalizedString.get("sign_in_to_sync")) },
            text = { Text(LocalizedString.get("sign_in_to_sync_desc")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissAuthPrompt()
                    onAuthRequired()
                }) {
                    Text(LocalizedString.get("sign_in"), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAuthPrompt() }) {
                    Text(LocalizedString.get("cancel"), color = extra.subtitleGray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showVerifyGate) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissVerifyGate() },
            title = { Text("Email verification required") },
            text = { Text("Please verify your email before adding debts or splits. Check your inbox (and spam folder).") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resendVerificationEmail()
                    viewModel.dismissVerifyGate()
                }) { Text("Resend email", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissVerifyGate() }) {
                    Text("Dismiss", color = extra.subtitleGray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showContactPicker) {
        ContactPickerBottomSheet(
            onDismiss = { showContactPicker = false },
            onContactSelected = { name ->
                viewModel.addParticipant(name)
                showContactPicker = false
            }
        )
    }

    // Reward-ad dialog (Wave 3 Issue 2). Shown when AI Take is tapped after the
    // daily 5-free cap and no Activity is available for a direct ad launch
    // (e.g. tapped from a non-Activity compose host). Mirrors AnalyticsScreen.
    if (showRewardAd) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRewardAd() },
            title = { Text(LocalizedString.get("watch_ad")) },
            text = { Text(LocalizedString.get("free_regenerations_desc")) },
            confirmButton = {
                TextButton(onClick = {
                    val pending = viewModel.pendingRewardSplit
                    viewModel.dismissRewardAd()
                    if (pending != null) viewModel.getAiSummary(pending, activity)
                }) { Text(LocalizedString.get("watch_ad")) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRewardAd() }) {
                    Text(LocalizedString.get("later"))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
    }
}

@Composable
fun AddParticipantRow(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    onPickContact: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(LocalizedString.get("add_person")) },
            placeholder = { Text(LocalizedString.get("name_placeholder")) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onPickContact) {
                    Icon(Icons.Default.ContactPage, LocalizedString.get("pick_contact"), tint = MaterialTheme.colorScheme.primary)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        IconButton(
            onClick = onAdd,
            modifier = Modifier
                .size(52.dp)
                .clip(UITokens.ShapeMedium)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, LocalizedString.get("add_person"), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun SplitItemCard(split: com.dhanuk.debtbro.data.db.entity.SplitEntity, onGetAi: (com.dhanuk.debtbro.data.db.entity.SplitEntity) -> Unit, onCreateDebts: (com.dhanuk.debtbro.data.db.entity.SplitEntity) -> Unit, debtsCreated: Boolean = false) {
    val extra = LocalExtraColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = UITokens.ShapeMedium
    ) {
        Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(split.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(formatCurrency(split.totalAmount, split.currency), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(
                "${formatCurrency(split.perPersonAmount, split.currency)} ${LocalizedString.get("each")}",
                color = extra.subtitleGray,
                fontSize = UITokens.FontSmall
            )
            
            if (split.aiSummary != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                    .clip(UITokens.ShapeSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(UITokens.SpaceXS)
                ) {
                    Text("🤖 ${split.aiSummary}", color = extra.subtitleGray, fontSize = UITokens.FontCaption)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS), modifier = Modifier.padding(top = UITokens.SpaceTiny)) {
                TextButton(
                    onClick = { onGetAi(split) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.AutoAwesome, LocalizedString.get("ai_take"), modifier = Modifier.size(UITokens.IconSmall))
                    Spacer(Modifier.width(UITokens.SpaceTiny))
                    Text(LocalizedString.get("ai_take_short"), fontSize = UITokens.FontCaption)
                }

                TextButton(
                    onClick = { onCreateDebts(split) },
                    enabled = !debtsCreated,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = extra.subtitleGray
                    )
                ) {
                    Icon(Icons.Default.LibraryAdd, LocalizedString.get("create_debts"), modifier = Modifier.size(UITokens.IconSmall))
                    Spacer(Modifier.width(UITokens.SpaceTiny))
                    Text(LocalizedString.get("create_debts"), fontSize = UITokens.FontCaption)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerBottomSheet(
    onDismiss: () -> Unit,
    onContactSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val extra = LocalExtraColors.current

    // P0-2 (2026-07-03): runtime READ_CONTACTS gate. Some OEM ROMs (Xiaomi /
    // Vivo / Oppo) throw SecurityException when reading the URI returned by
    // ActivityResultContracts.PickContact if READ_CONTACTS has not been
    // declared AND granted — even though the AOSP contract intends to allow
    // one-shot reads with a temporary URI permission. We declare
    // READ_CONTACTS in the manifest AND request it at runtime before
    // launching the picker, so the cursor query below doesn't crash.
    var permissionDenied by remember { mutableStateOf(false) }
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            // Wrap the cursor read in a try/catch — even with the runtime
            // grant, ROM-level contacts providers can still throw if the
            // user revoked permission mid-flight or the picker returned a
            // cross-user URI. Better to silently abort than to crash the
            // SplitScreen.
            try {
                val name = runCatching {
                    context.contentResolver.query(
                        it, arrayOf(
                            android.provider.ContactsContract.Contacts.DISPLAY_NAME
                        ), null, null, null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIndex = c.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                            if (nameIndex >= 0) c.getString(nameIndex) else null
                        } else null
                    }
                }.getOrNull()
                if (!name.isNullOrBlank()) onContactSelected(name)
            } catch (_: SecurityException) {
            }
        }
        onDismiss()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPickerLauncher.launch(null)
        } else {
            permissionDenied = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(UITokens.SheetContentPadding).padding(bottom = UITokens.SheetBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium)
        ) {
            Icon(Icons.Default.ContactPage, LocalizedString.get("select_contact"), modifier = Modifier.size(UITokens.IconXXL), tint = MaterialTheme.colorScheme.primary)
            Text(LocalizedString.get("select_contact"), color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontTitle, fontWeight = FontWeight.Bold)
            Text(LocalizedString.get("pick_from_contacts_desc"), color = extra.subtitleGray, textAlign = TextAlign.Center)
            if (permissionDenied) {
                androidx.compose.material3.Text(
                    LocalizedString.get("contacts_access_denied_toast"),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontSize = UITokens.FontBody
                )
            }
            Button(
                onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.READ_CONTACTS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        contactPickerLauncher.launch(null)
                    } else {
                        permissionDenied = false
                        permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = UITokens.ShapeMedium
            ) {
                Icon(Icons.Default.Contacts, LocalizedString.get("pick_contact"), tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(LocalizedString.get("pick_contact"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
