package com.dhanuk.debtbro.presentation.screens.split

import android.Manifest
import android.provider.ContactsContract
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.presentation.theme.SubtitleGray
import com.dhanuk.debtbro.util.formatCurrency
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitScreen(viewModel: SplitViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pastSplits by viewModel.pastSplits.collectAsStateWithLifecycle()
    var showContactPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Split Bill",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { viewModel.updateTitle(it) },
                        label = { Text("What's this for?") },
                        placeholder = { Text("e.g. Goa Trip, Pizza Party") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = Color(0xFF333333)
                        )
                    )

                    OutlinedTextField(
                        value = state.totalAmount,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) viewModel.updateTotal(it) },
                        label = { Text("Total Amount") },
                        placeholder = { Text("0.00") },
                        prefix = { Text("₹", color = PrimaryGreen, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = Color(0xFF333333)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.participants.forEach { name ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.removeParticipant(name) },
                                label = { Text(name) },
                                trailingIcon = {
                                    if (name != "Me") {
                                        Icon(
                                            Icons.Default.Close,
                                            null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = Color(0xFF2A2A2A),
                                    labelColor = Color.White
                                ),
                                border = null
                            )
                        }
                    }

                    // Result
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Split Share", color = SubtitleGray, fontSize = 12.sp)
                            Text(
                                formatCurrency(state.perPerson),
                                color = PrimaryGreen,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "among ${state.participants.size} people",
                                color = SubtitleGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.createSplit { viewModel.getAiSummary(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                        shape = RoundedCornerShape(12.dp),
                        enabled = state.totalAmount.isNotEmpty() && state.participants.size > 1
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Create Split", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    AnimatedVisibility(visible = state.aiSummary.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🤖", fontSize = 24.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(state.aiSummary, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Past Splits",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(pastSplits) { split ->
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
            label = { Text("Add Person") },
            placeholder = { Text("Name") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onPickContact) {
                    Icon(Icons.Default.ContactPage, null, tint = PrimaryGreen)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryGreen,
                unfocusedBorderColor = Color(0xFF333333)
            )
        )
        IconButton(
            onClick = onAdd,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PrimaryGreen)
        ) {
            Icon(Icons.Default.Add, null, tint = Color.Black)
        }
    }
}

@Composable
fun SplitItemCard(split: com.dhanuk.debtbro.data.db.entity.SplitEntity, onGetAi: (com.dhanuk.debtbro.data.db.entity.SplitEntity) -> Unit, onCreateDebts: (com.dhanuk.debtbro.data.db.entity.SplitEntity) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(split.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(formatCurrency(split.totalAmount), color = PrimaryGreen, fontWeight = FontWeight.Bold)
            }
            Text(
                "${formatCurrency(split.perPersonAmount)} each",
                color = SubtitleGray,
                fontSize = 13.sp
            )
            
            if (split.aiSummary != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2A))
                        .padding(8.dp)
                ) {
                    Text("🤖 ${split.aiSummary}", color = Color.LightGray, fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                TextButton(
                    onClick = { onGetAi(split) },
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryGreen)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI Take", fontSize = 12.sp)
                }
                
                TextButton(
                    onClick = { onCreateDebts(split) },
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryGreen)
                ) {
                    Icon(Icons.Default.LibraryAdd, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Create Debts", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerBottomSheet(
    onDismiss: () -> Unit,
    onContactSelected: (String) -> Unit
) {
    val permissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    val contactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Direct picking from native UI if preferred, but we'll show a list
    }

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            val list = mutableListOf<String>()
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex)
                    if (name != null) list.add(name)
                }
            }
            contacts = list
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 400.dp)
        ) {
            Text(
                "Select Contact",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (permissionState.status.isGranted) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contacts...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = Color(0xFF333333)
                    )
                )

                Spacer(Modifier.height(16.dp))

                val filtered = contacts.filter { it.contains(searchQuery, ignoreCase = true) }
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onContactSelected(name) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A2A2A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name.take(1), color = PrimaryGreen, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(name, color = Color.White)
                        }
                        Divider(color = Color(0xFF2A2A2A))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Contacts, null, modifier = Modifier.size(48.dp), tint = SubtitleGray)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Contact permission is needed to pick from your contacts list.",
                        color = SubtitleGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { permissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("Grant Permission", color = Color.Black)
                    }
                }
            }
        }
    }
}
