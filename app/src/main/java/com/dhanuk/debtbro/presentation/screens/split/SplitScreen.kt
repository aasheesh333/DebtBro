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
    val extra = LocalExtraColors.current
    var showContactPicker by remember { mutableStateOf(false) }
    val onAuthRequiredCopy = onAuthRequired // explicit lambda capture
    val viewModelCopy = viewModel

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(UITokens.CardInnerPadding)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium)
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
                                formatCurrency(state.perPerson),
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
                        onClick = { viewModel.createSplit { viewModel.getAiSummary(it) } },
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
            SplitItemCard(split, onGetAi = { viewModel.getAiSummary(it) }, onCreateDebts = { viewModel.createDebtsFromSplit(it) })
        }
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
fun SplitItemCard(split: com.dhanuk.debtbro.data.db.entity.SplitEntity, onGetAi: (com.dhanuk.debtbro.data.db.entity.SplitEntity) -> Unit, onCreateDebts: (com.dhanuk.debtbro.data.db.entity.SplitEntity) -> Unit) {
    val extra = LocalExtraColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = UITokens.ShapeMedium
    ) {
        Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(split.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(formatCurrency(split.totalAmount), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(
                "${formatCurrency(split.perPersonAmount)} ${LocalizedString.get("each")}",
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
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
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

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(
                it, arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME
                ), null, null, null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = c.getString(nameIndex)
                        if (name.isNotBlank()) onContactSelected(name)
                    }
                }
            }
        }
        onDismiss()
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
            Button(
                onClick = { contactPickerLauncher.launch(null) },
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

    if (showAuthPrompt) {
        AlertDialog(
            onDismissRequest = { viewModelCopy.dismissAuthPrompt() },
            title = { Text(LocalizedString.get("sign_in_to_sync")) },
            text = { Text(LocalizedString.get("sign_in_to_sync_desc")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModelCopy.dismissAuthPrompt()
                    onAuthRequiredCopy()
                }) {
                    Text(LocalizedString.get("sign_in"), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModelCopy.dismissAuthPrompt() }) {
                    Text(LocalizedString.get("cancel"), color = extra.subtitleGray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
