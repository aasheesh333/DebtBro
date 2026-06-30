package com.dhanuk.debtbro.presentation.screens.adddebt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.theme.DangerRed
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.LocalizedString
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtBottomSheet(
    onDismiss: () -> Unit,
    onDebtAdded: () -> Unit,
    onSignInRequired: () -> Unit = {},
    viewModel: AddDebtViewModel = hiltViewModel()
) {
    val extra = LocalExtraColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showAuthPrompt by viewModel.showAuthPrompt.collectAsStateWithLifecycle()

    var personName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var debtType by remember { mutableStateOf("THEY_OWE_ME") }
    var selectedCurrency by remember { mutableStateOf("₹") }
    var description by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Long?>(null) }

    var triedToSave by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customEmoji by remember { mutableStateOf("") }

    val THEY_OWE_EMOJIS = listOf(
        "😊","😎","🤝","🍕","🚕","☕","🏠","💼","🎁","🎮",
        "📚","✈️","🏖️","🎬","🍔","💪","🎉","🏋️","🚗","💊",
        "🎵","⚽","🍺","🎓","💻","🛒","🍱","🤑","😈","👑"
    )
    val I_OWE_EMOJIS = listOf(
        "😅","🙏","🤗","💳","😰","🫡","😬","🥺","😤","😇",
        "💸","📝","🫶","😶","🙈","😳","🤦","😌","🫠","🤞",
        "😔","🙇","🥹","🍽️","☕","🚗","🏠","📱","💼","🎯"
    )
    val currentEmojis = if (debtType == "THEY_OWE_ME") THEY_OWE_EMOJIS else I_OWE_EMOJIS
    var selectedEmoji by remember { mutableStateOf("") }

    LaunchedEffect(debtType) {
        selectedEmoji = currentEmojis[0]
    }

    LaunchedEffect(Unit) {
        selectedCurrency = viewModel.getDefaultCurrency()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UITokens.SpaceLarge)
                .padding(bottom = UITokens.SheetBottomPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add Debt", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = LocalizedString.get("cancel"), tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            OutlinedTextField(
                value = personName,
                onValueChange = {
                    if (it.length <= 30) personName = it.trimStart()
                },
                label = { Text("Person Name *") },
                placeholder = { Text("Rahul, Priya, John...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                trailingIcon = {
                    Text("${personName.length}/30", color = extra.subtitleGray, fontSize = 11.sp)
                },
                isError = personName.isBlank() && triedToSave,
                supportingText = {
                    if (personName.isBlank() && triedToSave) {
                        Text("Name is required", color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            OutlinedTextField(
                value = amount,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() || c == '.' }
                    val dots = filtered.count { c -> c == '.' }
                    if (dots <= 1 && (filtered.toDoubleOrNull() ?: 0.0 <= 9999999.0)) {
                        amount = filtered
                    }
                },
                label = { Text("Amount *") },
                placeholder = { Text("0") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Text(selectedCurrency, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                isError = (amount.isEmpty() || amount == "0") && triedToSave,
                supportingText = {
                    if ((amount.isEmpty() || amount == "0") && triedToSave) {
                        Text("Enter a valid amount", color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { debtType = "THEY_OWE_ME" },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (debtType == "THEY_OWE_ME") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(topStart = UITokens.SpaceSmall, bottomStart = UITokens.SpaceSmall)
                ) {
                    Text(
                        "💰 They Owe Me",
                        color = if (debtType == "THEY_OWE_ME") MaterialTheme.colorScheme.onPrimary else extra.subtitleGray,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = UITokens.FontSmall
                    )
                }
                Button(
                    onClick = { debtType = "I_OWE_THEM" },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (debtType == "I_OWE_THEM") DangerRed else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(topEnd = UITokens.SpaceSmall, bottomEnd = UITokens.SpaceSmall)
                ) {
                    Text(
                        "😅 I Owe Them",
                        color = if (debtType == "I_OWE_THEM") MaterialTheme.colorScheme.onError else extra.subtitleGray,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = UITokens.FontSmall
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Avatar", color = extra.subtitleGray, fontSize = UITokens.FontSmall)
                    TextButton(onClick = { showEmojiPicker = true }) {
                        Icon(Icons.Default.EmojiEmotions, contentDescription = LocalizedString.get("emoji"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(UITokens.IconSmall))
                        Spacer(Modifier.width(UITokens.SpaceTiny))
                        Text("From device", color = MaterialTheme.colorScheme.primary, fontSize = UITokens.FontCaption)
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS),
                    contentPadding = PaddingValues(vertical = UITokens.SpaceXS)
                ) {
                    items(currentEmojis) { emoji ->
                        val isSelected = emoji == selectedEmoji
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedEmoji = emoji
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 26.sp)
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(UITokens.IconSmall)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            LocalizedString.get("select_language"),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)) {
                listOf("₹", "$", "€", "£", "¥", "₩").forEach { curr ->
                    FilterChip(
                        selected = selectedCurrency == curr,
                        onClick = { selectedCurrency = curr },
                        label = { Text(curr, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = extra.subtitleGray
                        )
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 100) description = it },
                label = { Text("Description (optional)") },
                placeholder = { Text("Lunch, rent, trip...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                trailingIcon = {
                    Text("${description.length}/100", color = extra.subtitleGray, fontSize = 11.sp)
                }
            )

            OutlinedTextField(
                value = selectedDate?.let {
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
                } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Due Date (optional)") },
                placeholder = { Text("Select due date") },
                trailingIcon = {
                    Row {
                        if (selectedDate != null) {
                            IconButton(onClick = { selectedDate = null }) {
                                Icon(Icons.Default.Clear, contentDescription = LocalizedString.get("cancel"), tint = DangerRed)
                            }
                        }
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = LocalizedString.get("select_due_date"), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 200) notes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Any extra context...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                trailingIcon = {
                    Text("${notes.length}/200", color = extra.subtitleGray, fontSize = 11.sp)
                }
            )

            Spacer(Modifier.height(UITokens.SpaceXS))

            Button(
                onClick = {
                    triedToSave = true
                    val amtValue = amount.toDoubleOrNull() ?: 0.0
                    when {
                        personName.isBlank() -> {
                            Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                        }
                        amtValue <= 0 -> {
                            Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        }
                        amtValue > 1_000_000_000 -> {
                            Toast.makeText(context, "Amount seems too large. Please verify.", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            viewModel.saveDebt(
                                personName = personName.trim(),
                                personEmoji = selectedEmoji,
                                amount = amtValue,
                                currency = selectedCurrency,
                                type = debtType,
                                description = description.trim(),
                                dueDate = selectedDate,
                                notes = notes.trim(),
                                onSaved = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDebtAdded()
                                }
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = UITokens.ShapeLarge,
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(UITokens.IconLarge))
                } else {
                    Icon(Icons.Default.Add, LocalizedString.get("add_debt"), tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(UITokens.SpaceXS))
                    Text(
                        "Add Debt",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }

    if (showEmojiPicker) {
        AlertDialog(
            onDismissRequest = { showEmojiPicker = false },
            title = { Text("Type or paste an emoji", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = customEmoji,
                    onValueChange = {
                        if (it.isNotEmpty()) {
                            customEmoji = it
                            selectedEmoji = it.take(2).trim()
                        }
                    },
                    placeholder = { Text("Paste emoji here 😊") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { showEmojiPicker = false }) {
                    Text("Done", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = extra.subtitleGray)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showAuthPrompt) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissAuthPrompt()
            },
            title = { Text(LocalizedString.get("sign_in_to_sync")) },
            text = { Text(LocalizedString.get("sign_in_to_sync_desc")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissAuthPrompt()
                    onDismiss()
                    onSignInRequired()
                }) { Text(LocalizedString.get("sign_in"), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAuthPrompt() }) {
                    Text(LocalizedString.get("cancel"), color = extra.subtitleGray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
