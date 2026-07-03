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
import com.dhanuk.debtbro.data.repository.AiRepository.Companion.MAX_FREE_REGENERATIONS
import com.dhanuk.debtbro.presentation.components.ConfettiOverlay
import com.dhanuk.debtbro.presentation.components.EmptyStateView
import com.dhanuk.debtbro.presentation.components.LoadingDotsIndicator
import com.dhanuk.debtbro.presentation.theme.DangerRed
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
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
fun DebtDetailScreen(onBack: () -> Unit, onAuthRequired: () -> Unit = {}, viewModel: DebtDetailViewModel = hiltViewModel()) {
    val extra = LocalExtraColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val debt by viewModel.debt.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val aiMessage by viewModel.aiMessage.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingAi.collectAsStateWithLifecycle()
    val roastLevel by viewModel.roastLevel.collectAsStateWithLifecycle()
    val showPaymentSheet by viewModel.showAddPaymentSheet.collectAsStateWithLifecycle()
    val showEditSheet by viewModel.showEditDebtSheet.collectAsStateWithLifecycle()
    val showConfetti by viewModel.showConfetti.collectAsStateWithLifecycle()
    val showAuthPrompt by viewModel.showAuthPrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val showRewardAd by viewModel.showRewardAd.collectAsStateWithLifecycle()
    val remainingFree by viewModel.remainingFree.collectAsStateWithLifecycle()
    val isExportingImage by viewModel.isExportingImage.collectAsStateWithLifecycle()
    val exportElapsed by viewModel.exportElapsed.collectAsStateWithLifecycle()
    var showQuoteEditDialog by remember { mutableStateOf(false) }
    var pendingExportAction by remember { mutableStateOf("") }
    var editQuoteText by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        viewModel.preloadRewardedAd(context)
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(debt?.personName ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = LocalizedString.get("nav_back"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (debt != null) {
                        IconButton(onClick = { viewModel.showEditDebtSheet.value = true }) {
                            Icon(Icons.Default.Edit, contentDescription = LocalizedString.get("edit_debt"), tint = MaterialTheme.colorScheme.onSurface)
                        }

                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = LocalizedString.get("delete_debt"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val d = debt
        val timedOut by viewModel.resolutionTimedOut.collectAsStateWithLifecycle()
        if (d == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = UITokens.FontEmojiLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(LocalizedString.get("debt_not_found"), color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontSubhead, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (timedOut) LocalizedString.get("debt_link_error")
                        else "It might have been deleted.",
                        color = extra.subtitleGray,
                        fontSize = UITokens.FontBody,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    if (timedOut) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = UITokens.ShapeMedium
                        ) {
                            Text(LocalizedString.get("go_back"))
                        }
                    }
                }
            }
            return@Scaffold
        }

        val remaining = (d.amount - d.amountPaid).coerceAtLeast(0.0)
        val progress = if (d.amount > 0) (d.amountPaid / d.amount).toFloat().coerceIn(0f, 1f) else 1f

        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = UITokens.ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(d.personEmoji, fontSize = 40.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (remaining > 0) LocalizedString.get("remaining_balance") else LocalizedString.get("debt_settled"),
                            color = extra.subtitleGray,
                            fontSize = 14.sp
                        )
                        Text(
                            formatCurrency(remaining, d.currency),
                            color = if (remaining > 0) (if (d.type == "THEY_OWE_ME") MaterialTheme.colorScheme.primary else DangerRed) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${LocalizedString.get("paid")}: ${formatCurrency(d.amountPaid, d.currency)}", color = extra.subtitleGray, fontSize = 12.sp)
                            Text("${LocalizedString.get("total")}: ${formatCurrency(d.amount, d.currency)}", color = extra.subtitleGray, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${LocalizedString.get("created")}: ${d.createdAt.toReadableDateTime()}",
                            color = extra.subtitleGray,
                            fontSize = 11.sp
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)) {
                        Button(
                            onClick = { viewModel.showAddPaymentSheet.value = true },
                            modifier = Modifier.weight(1f).height(UITokens.ButtonHeight),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = UITokens.ShapeMedium,
                            enabled = remaining > 0
                        ) {
                            Icon(Icons.Default.Add, contentDescription = LocalizedString.get("add_payment"), tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(UITokens.SpaceXS))
                            Text(LocalizedString.get("add_payment"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                        
                        OutlinedButton(
                            onClick = { viewModel.markSettled() },
                            modifier = Modifier.weight(1f).height(UITokens.ButtonHeight),
                            shape = UITokens.ShapeMedium,
                            border = BorderStroke(1.dp, if (remaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            enabled = remaining > 0
                        ) {
                            Text(LocalizedString.get("settle_all"), color = if (remaining > 0) MaterialTheme.colorScheme.primary else extra.subtitleGray)
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = UITokens.ShapeLarge
                    ) {
                        Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(LocalizedString.get("nudge"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceTiny)) {
                                    listOf("MILD", "MEDIUM", "SPICY").forEach { level ->
                                        FilterChip(
                                            selected = roastLevel == level,
                                            onClick = { viewModel.setRoastLevel(level) },
                                            label = { Text(level, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
        }
    }
}

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(UITokens.ShapeMedium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(UITokens.CardInnerPadding)
                            ) {
                                if (isGenerating) {
                                    LoadingDotsIndicator(color = MaterialTheme.colorScheme.primary)
                                } else {
                                    val msg = aiMessage.ifBlank { d.aiRoastGenerated ?: LocalizedString.get("tap_refresh") }
                                    Text(
                                        msg,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                }
                            }

 Row(horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)) {
                                  Button(
                                      onClick = {
                                          val activity = context.findActivity()
                                          viewModel.generateRoast(activity)
                                      },
                                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                                          val quote = aiMessage.ifBlank { d.aiRoastGenerated.orEmpty() }
                                          viewModel.shareQuoteToWhatsApp(context, quote)
                                      },
                                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                      modifier = Modifier.weight(1f)
                                  ) {
                                       Icon(Icons.Default.Send, contentDescription = LocalizedString.get("share_whatsapp"), modifier = Modifier.size(UITokens.IconSmall))
                                      Spacer(Modifier.width(UITokens.SpaceXS))
                                      Text(LocalizedString.get("whatsapp_button"))
                                  }
                              }

                              TextButton(
                                  onClick = { viewModel.reportAiMessage() },
                                  contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                              ) {
                                Icon(
                                       Icons.Default.Flag,
                                       contentDescription = LocalizedString.get("report_message"),
                                       modifier = Modifier.size(UITokens.IconSmall),
                                      tint = extra.subtitleGray
                                  )
                                   Spacer(Modifier.width(UITokens.SpaceTiny))
                                  Text(
                                      LocalizedString.get("report_message"),
                                      color = extra.subtitleGray,
                                      fontSize = 11.sp
                                  )
                              }
}
                    }
                }

                item {
                    Text(LocalizedString.get("payment_history"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontSubhead)
                }
                
                if (payments.isEmpty()) {
                    item {
                        Text(LocalizedString.get("no_payments"), color = extra.subtitleGray, modifier = Modifier.padding(vertical = 16.dp))
                    }
                } else {
                    items(payments, key = { it.id }) { payment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(UITokens.ShapeMedium)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(UITokens.CardInnerPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(formatCurrency(payment.amount, d.currency), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(payment.paidAt.toReadableDate(), color = extra.subtitleGray, fontSize = UITokens.FontCaption)
                                if (!payment.note.isNullOrBlank()) {
                                    Text(payment.note, color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontSmall, modifier = Modifier.padding(top = UITokens.SpaceTiny))
                                }
                            }
                            IconButton(onClick = { viewModel.deletePayment(payment.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = LocalizedString.get("delete"), tint = extra.subtitleGray.copy(alpha = 0.5f), modifier = Modifier.size(UITokens.IconMedium))
                            }
                        }
                    }
                }
                
                item {
                    Button(
                        onClick = {
                            editQuoteText = aiMessage.ifBlank { d.aiRoastGenerated.orEmpty() }
                            pendingExportAction = "share"
                            showQuoteEditDialog = true
                        },
                        enabled = !isExportingImage,
                        modifier = Modifier.fillMaxWidth().height(UITokens.ButtonHeight),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = UITokens.ShapeMedium
                    ) {
                        Icon(Icons.Default.Image, contentDescription = LocalizedString.get("export_image"), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(UITokens.SpaceXS))
                        Text(LocalizedString.get("export_image"), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                
                item {
                    OutlinedButton(
                        onClick = { 
                            viewModel.shareDebtHistoryToWhatsApp(context, d, payments)
                        },
                        enabled = !isExportingImage,
                        modifier = Modifier.fillMaxWidth().height(UITokens.ButtonHeight),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF25D366)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF25D366)),
                        shape = UITokens.ShapeMedium
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Send, contentDescription = LocalizedString.get("share_whatsapp"), tint = Color(0xFF25D366))
                        Spacer(Modifier.width(UITokens.SpaceXS))
                        Text(LocalizedString.get("share_whatsapp"), color = Color(0xFF25D366))
                    }
                }
            }
            
            ConfettiOverlay(showConfetti) {
                viewModel.dismissConfetti()
            }

            if (showPaymentSheet) {
                AddPaymentDialog(
                    remaining = remaining,
                    currency = d.currency,
                    onDismiss = { viewModel.showAddPaymentSheet.value = false },
                    onSave = { amount, note -> viewModel.addPayment(amount, note) }
                )
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(LocalizedString.get("delete_debt"), color = MaterialTheme.colorScheme.onSurface) },
                    text = { Text(LocalizedString.get("delete_confirm"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteDebt()
                            showDeleteConfirm = false
                            onBack()
                        }) {
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

            if (showEditSheet) {
                EditDebtDialog(
                    debt = d,
                    onDismiss = { viewModel.showEditDebtSheet.value = false },
                    onSave = { name, amount, desc, emoji -> viewModel.updateDebt(name, amount, desc, emoji) }
                )
            }

            if (showAuthPrompt) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissAuthPrompt() },
                    title = { Text(com.dhanuk.debtbro.util.LocalizedString.get("sign_in_to_sync")) },
                    text = { Text(com.dhanuk.debtbro.util.LocalizedString.get("sign_in_to_sync_desc")) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.dismissAuthPrompt()
                            onAuthRequired()
                        }) {
                            Text(com.dhanuk.debtbro.util.LocalizedString.get("sign_in"), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissAuthPrompt() }) {
                            Text(com.dhanuk.debtbro.util.LocalizedString.get("cancel"), color = extra.subtitleGray)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            if (showRewardAd) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissRewardAd() },
                    title = { Text(LocalizedString.get("free_regenerations_used"), color = MaterialTheme.colorScheme.onSurface) },
                    text = { Text(LocalizedString.get("free_regenerations_desc").replace("{count}", MAX_FREE_REGENERATIONS.toString()), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    confirmButton = {
                        TextButton(onClick = {
                            val activity = context.findActivity()
                            if (activity != null) {
                                viewModel.preloadRewardedAd(activity)
                            }
                            viewModel.dismissRewardAd()
                            viewModel.generateRoast(context.findActivity())
                        }) {
                            Text(LocalizedString.get("watch_ad"), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissRewardAd() }) {
                            Text(LocalizedString.get("later"), color = extra.subtitleGray)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            if (isExportingImage) {
                val seconds = exportElapsed / 1000
                AlertDialog(
             onDismissRequest = {},
             title = { Text(LocalizedString.get("preparing_image"), color = MaterialTheme.colorScheme.onSurface) },
             text = {
                     Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                         LinearProgressIndicator(
                             modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                             color = MaterialTheme.colorScheme.primary,
                             trackColor = MaterialTheme.colorScheme.outline
                     )
                     Text("${seconds}s elapsed — rendering your card", color = MaterialTheme.colorScheme.onSurfaceVariant)
                 }
             },
             confirmButton = {},
       containerColor = MaterialTheme.colorScheme.surface
           )
       }

    if (showQuoteEditDialog) {
        QuoteEditDialog(
            initialText = editQuoteText,
            onDismiss = {
                showQuoteEditDialog = false
                pendingExportAction = ""
            },
            onConfirm = { editedText ->
                showQuoteEditDialog = false
                when (pendingExportAction) {
                    "share" -> viewModel.shareCard(context, d, editedText)
                }
                pendingExportAction = ""
            }
        )
    }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentDialog(remaining: Double, currency: String, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    val extra = LocalExtraColors.current
    var amount by remember { mutableStateOf("%.2f".format(remaining)) }
    var note by remember { mutableStateOf("") }
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(UITokens.SheetContentPadding).padding(bottom = UITokens.SheetBottomPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium)) {
            Text(LocalizedString.get("add_payment"), fontSize = UITokens.FontTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                OutlinedTextField(
                value = amount,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amount = it },
                label = { Text("${LocalizedString.get("amount")} ($currency)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = (amount.toDoubleOrNull() ?: 0.0) > remaining,
                supportingText = {
                    if ((amount.toDoubleOrNull() ?: 0.0) > remaining) {
                        Text(LocalizedString.get("exceeds_remaining_balance"), color = MaterialTheme.colorScheme.error)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(LocalizedString.get("note_optional")) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && amt <= remaining) onSave(amt, note)
                    else if (amt > remaining) {
                        Toast.makeText(context, LocalizedString.get("exceeds_remaining_balance"), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(UITokens.ButtonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(LocalizedString.get("save_payment"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
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

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(UITokens.SheetContentPadding).padding(bottom = UITokens.SheetBottomPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium)) {
            Text(LocalizedString.get("edit_debt"), fontSize = UITokens.FontTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(LocalizedString.get("person_name")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(LocalizedString.get("total_amount")) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(LocalizedString.get("description")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text(LocalizedString.get("emoji")) }, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: debt.amount
                    onSave(name, amt, desc, emoji)
                },
                modifier = Modifier.fillMaxWidth().height(UITokens.ButtonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(LocalizedString.get("update_debt"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteEditDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val extra = LocalExtraColors.current
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(LocalizedString.get("edit_ai_quote"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)) {
                Text(
                    LocalizedString.get("edit_ai_quote_desc"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = UITokens.FontSmall
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = UITokens.ShapeMedium,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = UITokens.ShapeMedium
            ) {
                Text(LocalizedString.get("generate_image"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LocalizedString.get("cancel"), color = extra.subtitleGray)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtNotFoundScreen(onBack: () -> Unit) {
    val extra = LocalExtraColors.current
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = LocalizedString.get("nav_back"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📭", fontSize = UITokens.FontEmojiLarge)
                Spacer(Modifier.height(16.dp))
                Text(LocalizedString.get("debt_not_found"), color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("It might have been deleted.", color = extra.subtitleGray, fontSize = 14.sp)
            }
        }
    }
}
