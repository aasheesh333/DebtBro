package com.dhanuk.debtbro.presentation.screens.debtdetail

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ContextWrapper
import android.app.Activity
import android.widget.Toast
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.repository.GroqRepository.Companion.MAX_FREE_REGENERATIONS
import com.dhanuk.debtbro.presentation.components.ConfettiOverlay
import com.dhanuk.debtbro.presentation.components.EmptyStateView
import com.dhanuk.debtbro.presentation.components.LoadingDotsIndicator
import com.dhanuk.debtbro.presentation.theme.DangerRed
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.presentation.theme.SubtitleGray
import com.dhanuk.debtbro.util.copyToClipboard
import com.dhanuk.debtbro.util.formatCurrency
import com.dhanuk.debtbro.util.toReadableDate
import com.dhanuk.debtbro.util.toReadableDateTime
import com.dhanuk.debtbro.util.LocalizedString

fun android.content.Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(onBack: () -> Unit, viewModel: DebtDetailViewModel = hiltViewModel()) {
    val debt by viewModel.debt.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val aiMessage by viewModel.aiMessage.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingAi.collectAsStateWithLifecycle()
    val roastLevel by viewModel.roastLevel.collectAsStateWithLifecycle()
    val showPaymentSheet by viewModel.showAddPaymentSheet.collectAsStateWithLifecycle()
    val showEditSheet by viewModel.showEditDebtSheet.collectAsStateWithLifecycle()
    val showConfetti by viewModel.showConfetti.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val showRewardAd by viewModel.showRewardAd.collectAsStateWithLifecycle()
    val remainingFree by viewModel.remainingFree.collectAsStateWithLifecycle()
    val isExportingImage by viewModel.isExportingImage.collectAsStateWithLifecycle()
    val exportElapsed by viewModel.exportElapsed.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        viewModel.preloadRewardedAd(context)
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val d = debt ?: return DebtNotFoundScreen(onBack = onBack)

    val remaining = (d.amount - d.amountPaid).coerceAtLeast(0.0)
    val progress = if (d.amount > 0) (d.amountPaid / d.amount).toFloat().coerceIn(0f, 1f) else 1f

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(d.personName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showEditDebtSheet.value = true }) {
                        Icon(Icons.Default.Edit, null, tint = Color.White)
                    }
                    var showDeleteConfirm by remember { mutableStateOf(false) }

                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFFF4757))
                    }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            containerColor = Color(0xFF1A1A1A),
                            title = { Text(LocalizedString.get("delete_debt"), color = Color.White) },
                            text = { Text(LocalizedString.get("delete_confirm"), color = Color(0xFFCCCCCC)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.deleteDebt()
                                    showDeleteConfirm = false
                                    onBack()
                                }) {
                                    Text(LocalizedString.get("delete"), color = Color(0xFFFF4757))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text(LocalizedString.get("cancel"), color = SubtitleGray)
                                }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF0D0D0D),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0D0D0D)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Main Info Section
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E1E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(d.personEmoji, fontSize = 40.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (remaining > 0) LocalizedString.get("remaining_balance") else LocalizedString.get("debt_settled"),
                            color = SubtitleGray,
                            fontSize = 14.sp
                        )
                        Text(
                            formatCurrency(remaining, d.currency),
                            color = if (remaining > 0) (if (d.type == "THEY_OWE_ME") PrimaryGreen else DangerRed) else Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = PrimaryGreen,
                            trackColor = Color(0xFF1E1E1E)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${LocalizedString.get("paid")}: ${formatCurrency(d.amountPaid, d.currency)}", color = SubtitleGray, fontSize = 12.sp)
                            Text("${LocalizedString.get("total")}: ${formatCurrency(d.amount, d.currency)}", color = SubtitleGray, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${LocalizedString.get("created")}: ${d.createdAt.toReadableDateTime()}",
                            color = SubtitleGray,
                            fontSize = 11.sp
                        )
                    }
                }

                // Actions Section
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.showAddPaymentSheet.value = true },
                            modifier = Modifier.weight(1f).height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                            shape = RoundedCornerShape(12.dp),
                            enabled = remaining > 0
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color.Black)
                            Spacer(Modifier.width(8.dp))
                            Text(LocalizedString.get("add_payment"), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        
                        OutlinedButton(
                            onClick = { viewModel.markSettled() },
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (remaining > 0) PrimaryGreen else Color(0xFF333333)),
                            enabled = remaining > 0
                        ) {
                            Text(LocalizedString.get("settle_all"), color = if (remaining > 0) PrimaryGreen else SubtitleGray)
                        }
                    }
                }

                // Nudge Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(LocalizedString.get("nudge"), fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("MILD", "MEDIUM", "SAVAGE").forEach { level ->
                                        FilterChip(
                                            selected = roastLevel == level,
                                            onClick = { viewModel.setRoastLevel(level) },
                                            label = { Text(level, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryGreen,
                                                selectedLabelColor = Color.Black
                                            )
                                        )
        }
    }
}

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2A2A2A))
                                    .padding(16.dp)
                            ) {
                                if (isGenerating) {
                                    LoadingDotsIndicator(color = PrimaryGreen)
                                } else {
                                    val msg = aiMessage.ifBlank { d.aiRoastGenerated ?: LocalizedString.get("tap_refresh") }
                                    Text(
                                        msg,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val activity = context.findActivity()
                                        viewModel.generateRoast(activity)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (remainingFree > 0) {
                                        Text("${LocalizedString.get("regenerate")} ($remainingFree)")
                                    } else {
                                        Text("🎯 ${LocalizedString.get("regenerate")}")
                                    }
                                }
                                Button(
                                    onClick = { 
                                        Toast.makeText(context, "Generating image...", Toast.LENGTH_SHORT).show()
                                        viewModel.shareCardToWhatsApp(context, d, aiMessage.ifBlank { d.aiRoastGenerated.orEmpty() })
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("WhatsApp")
        }
    }
}
                    }
                }

                // Payment History Section
                item {
                    Text(LocalizedString.get("payment_history"), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                }
                
                if (payments.isEmpty()) {
                    item {
                        Text(LocalizedString.get("no_payments"), color = SubtitleGray, modifier = Modifier.padding(vertical = 16.dp))
                    }
                } else {
                    items(payments, key = { it.id }) { payment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF161616))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(formatCurrency(payment.amount, d.currency), color = PrimaryGreen, fontWeight = FontWeight.Bold)
                                Text(payment.paidAt.toReadableDate(), color = SubtitleGray, fontSize = 12.sp)
                                if (!payment.note.isNullOrBlank()) {
                                    Text(payment.note, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            IconButton(onClick = { viewModel.deletePayment(payment.id) }) {
                                Icon(Icons.Default.Delete, null, tint = SubtitleGray.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                
                item {
                    Button(
                        onClick = { 
                            viewModel.shareCard(context, d, aiMessage.ifBlank { d.aiRoastGenerated.orEmpty() })
                        },
                        enabled = !isExportingImage,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Image, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(LocalizedString.get("export_image"), color = Color.White)
                    }
                }
                
                item {
                    OutlinedButton(
                        onClick = { 
                            viewModel.shareCardToWhatsApp(context, d, aiMessage.ifBlank { d.aiRoastGenerated.orEmpty() })
                        },
                        enabled = !isExportingImage,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF25D366)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF25D366)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Send, null, tint = Color(0xFF25D366))
                        Spacer(Modifier.width(8.dp))
                        Text(LocalizedString.get("share_whatsapp"), color = Color(0xFF25D366))
                    }
                }
            }
            
            ConfettiOverlay(showConfetti) {
                viewModel.dismissConfetti()
            }
        }
    }

    if (showPaymentSheet) {
        AddPaymentDialog(
            remaining = remaining,
            currency = d.currency,
            onDismiss = { viewModel.showAddPaymentSheet.value = false },
            onSave = { amount, note -> viewModel.addPayment(amount, note) }
        )
    }

    if (showEditSheet) {
        EditDebtDialog(
            debt = d,
            onDismiss = { viewModel.showEditDebtSheet.value = false },
            onSave = { name, amount, desc, emoji -> viewModel.updateDebt(name, amount, desc, emoji) }
        )
    }

    if (showRewardAd) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRewardAd() },
            title = { Text(LocalizedString.get("free_regenerations_used"), color = Color.White) },
            text = { Text(LocalizedString.get("free_regenerations_desc").replace("{count}", MAX_FREE_REGENERATIONS.toString()), color = Color(0xFFCCCCCC)) },
            confirmButton = {
                TextButton(onClick = {
                    val activity = context.findActivity()
                    if (activity != null) {
                        viewModel.preloadRewardedAd(activity)
                    }
                    viewModel.dismissRewardAd()
                    viewModel.generateRoast(context.findActivity())
                }) {
                    Text(LocalizedString.get("watch_ad"), color = PrimaryGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRewardAd() }) {
                    Text(LocalizedString.get("later"), color = SubtitleGray)
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    if (isExportingImage) {
        val seconds = exportElapsed / 1000
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Preparing image...", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = PrimaryGreen,
                        trackColor = Color(0xFF333333)
                    )
                    Text("${seconds}s elapsed — rendering your card", color = Color(0xFFCCCCCC))
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF1A1A1A)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentDialog(remaining: Double, currency: String, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf("%.2f".format(remaining)) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1A1A1A)) {
        Column(Modifier.padding(24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(LocalizedString.get("add_payment"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amount = it },
                label = { Text("${LocalizedString.get("amount")} ($currency)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen)
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(LocalizedString.get("note_optional")) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen)
            )
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0) onSave(amt, note)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text(LocalizedString.get("save_payment"), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDebtDialog(debt: DebtEntity, onDismiss: () -> Unit, onSave: (String, Double, String, String) -> Unit) {
    var name by remember { mutableStateOf(debt.personName) }
    var amount by remember { mutableStateOf(debt.amount.toString()) }
    var desc by remember { mutableStateOf(debt.description) }
    var emoji by remember { mutableStateOf(debt.personEmoji) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1A1A1A)) {
        Column(Modifier.padding(24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(LocalizedString.get("edit_debt"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(LocalizedString.get("person_name")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(LocalizedString.get("total_amount")) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(LocalizedString.get("description")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text(LocalizedString.get("emoji")) }, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: debt.amount
                    onSave(name, amt, desc, emoji)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text(LocalizedString.get("update_debt"), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtNotFoundScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = LocalizedString.get("nav_back"), tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📭", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Debt not found", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("It might have been deleted.", color = SubtitleGray, fontSize = 14.sp)
            }
        }
    }
}
