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
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.presentation.theme.SubtitleGray
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtBottomSheet(
    onDismiss: () -> Unit,
    onDebtAdded: () -> Unit,
    viewModel: AddDebtViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

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

    // Different emojis for each debt type
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

    // Reset selected emoji when debt type changes
    LaunchedEffect(debtType) {
        selectedEmoji = currentEmojis[0]
    }

    LaunchedEffect(Unit) {
        selectedCurrency = viewModel.getDefaultCurrency()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF444444))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add Debt", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }

            // FIELD 1 — Person Name
            OutlinedTextField(
                value = personName,
                onValueChange = { 
                    if (it.length <= 30 && it.all { c -> c.isLetter() || c.isWhitespace() }) 
                        personName = it 
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
                    Text("${personName.length}/30", color = SubtitleGray, fontSize = 11.sp)
                },
                isError = personName.isEmpty() && triedToSave,
                supportingText = {
                    if (personName.isEmpty() && triedToSave) {
                        Text("Name is required", color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            // FIELD 2 — Amount
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
                    Text(selectedCurrency, color = PrimaryGreen, fontWeight = FontWeight.Bold)
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

            // FIELD 3 — Debt Direction (Toggle)
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { debtType = "THEY_OWE_ME" },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (debtType == "THEY_OWE_ME") PrimaryGreen else Color(0xFF2A2A2A)
                    ),
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                ) {
                    Text(
                        "💰 They Owe Me",
                        color = if (debtType == "THEY_OWE_ME") Color.Black else SubtitleGray,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = { debtType = "I_OWE_THEM" },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (debtType == "I_OWE_THEM") DangerRed else Color(0xFF2A2A2A)
                    ),
                    shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                ) {
                    Text(
                        "😅 I Owe Them",
                        color = if (debtType == "I_OWE_THEM") Color.White else SubtitleGray,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            // FIELD 4 — Emoji Avatar Picker
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Avatar", color = SubtitleGray, fontSize = 13.sp)
                    TextButton(onClick = { showEmojiPicker = true }) {
                        Icon(Icons.Default.EmojiEmotions, null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("From device", color = PrimaryGreen, fontSize = 12.sp)
                    }
                }
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(currentEmojis) { emoji ->
                        val isSelected = emoji == selectedEmoji
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) PrimaryGreen.copy(alpha = 0.25f)
                                    else Color(0xFF2A2A2A)
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) PrimaryGreen else Color.Transparent,
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
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // FIELD 5 — Currency Selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("₹", "$", "€", "£", "¥", "₩").forEach { curr ->
                    FilterChip(
                        selected = selectedCurrency == curr,
                        onClick = { selectedCurrency = curr },
                        label = { Text(curr, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF2A2A2A),
                            labelColor = SubtitleGray
                        )
                    )
                }
            }

            // FIELD 6 — Description
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 100) description = it },
                label = { Text("Description (optional)") },
                placeholder = { Text("Lunch, rent, trip...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                trailingIcon = {
                    Text("${description.length}/100", color = SubtitleGray, fontSize = 11.sp)
                }
            )

            // FIELD 7 — Due Date
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
                                Icon(Icons.Default.Clear, null, tint = DangerRed)
                            }
                        }
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, null, tint = PrimaryGreen)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            )

            // FIELD 8 — Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 200) notes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Any extra context...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                trailingIcon = {
                    Text("${notes.length}/200", color = SubtitleGray, fontSize = 11.sp)
                }
            )

            Spacer(Modifier.height(8.dp))

            // SAVE BUTTON
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
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Add, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Add Debt",
                        color = Color.Black,
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
            title = { Text("Type or paste an emoji", color = Color.White) },
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
                    Text("Done", color = PrimaryGreen)
                }
            },
            containerColor = Color(0xFF1E1E1E)
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
                }) { Text("OK", color = PrimaryGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { 
                    Text("Cancel", color = SubtitleGray) 
                }
            },
            colors = DatePickerDefaults.colors(containerColor = Color(0xFF1E1E1E))
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
