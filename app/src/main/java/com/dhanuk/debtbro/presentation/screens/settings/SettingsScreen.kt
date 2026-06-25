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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.presentation.components.GoogleSignInCard
import com.dhanuk.debtbro.presentation.components.LanguageSelectorGrid
import com.dhanuk.debtbro.presentation.components.SUPPORTED_LANGUAGES
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.presentation.theme.SubtitleGray
import com.dhanuk.debtbro.util.LocalizedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            item {
                Text(
                    LocalizedString.get("settings"),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ACCOUNT SECTION
            item { SectionHeader(LocalizedString.get("account"), Icons.Default.AccountCircle) }
            item {
                GoogleSignInCard(
                    isSignedIn = state.isSignedIn,
                    name = state.googleName,
                    email = state.email,
                    lastSynced = state.lastSynced,
                isSyncing = state.isSyncing,
                syncMessage = state.syncMessage,
                    onSignIn = { viewModel.signInWithGoogle(context as Activity) },
                    onSignOut = { viewModel.signOut() },
                    onSync = { viewModel.syncNow() }
                )
            }

            // PREFERENCES SECTION
            item { SectionHeader(LocalizedString.get("preferences"), Icons.Default.Tune) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
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
                                unfocusedBorderColor = Color(0xFF333333)
                            )
                        )
                        
                        Text(LocalizedString.get("default_currency"), color = SubtitleGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("₹", "$", "€", "£", "¥").forEach { c ->
                                FilterChip(
                                    selected = state.currency == c,
                                    onClick = { viewModel.setCurrency(c) },
                                    label = { Text(c) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryGreen,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        Text(LocalizedString.get("nudge_roast_level"), color = SubtitleGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("MILD", "MEDIUM", "SAVAGE").forEach { r ->
                                FilterChip(
                                    selected = state.roastLevel == r,
                                    onClick = { viewModel.setRoastLevel(r) },
                                    label = { Text(r) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryGreen,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        Divider(color = Color(0xFF2A2A2A))

                        Text(LocalizedString.get("card_display_options"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

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

            // LANGUAGE SECTION
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = true },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2A2A2A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Language, null, tint = PrimaryGreen)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(LocalizedString.get("language"), color = Color.White, fontWeight = FontWeight.Bold)
                            val currentLang = SUPPORTED_LANGUAGES.find { it.code == state.selectedLanguage }
                            Text(
                                currentLang?.nativeName ?: "English",
                                color = SubtitleGray,
                                fontSize = 13.sp
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = SubtitleGray)
                    }
                }
            }

            // DATA SECTION
            item { SectionHeader(LocalizedString.get("data_export"), Icons.Default.Storage) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsActionItem(
                            title = LocalizedString.get("export_csv"),
                            subtitle = LocalizedString.get("export_csv_subtitle"),
                            icon = Icons.Default.FileUpload,
                            onClick = { viewModel.exportCsv(context) }
                        )
                        Divider(color = Color(0xFF2A2A2A))
                        var showClearConfirm by remember { mutableStateOf(false) }

                        SettingsActionItem(
                            title = LocalizedString.get("clear_settled"),
                            subtitle = LocalizedString.get("clear_settled_subtitle"),
                            icon = Icons.Default.DeleteSweep,
                            color = Color(0xFFFF4757),
                            onClick = { showClearConfirm = true }
                        )

                        if (showClearConfirm) {
                            AlertDialog(
                                onDismissRequest = { showClearConfirm = false },
                                containerColor = Color(0xFF1A1A1A),
                                title = { Text(LocalizedString.get("clear_settled_dialog"), color = Color.White) },
                                text = { Text(LocalizedString.get("clear_settled_dialog_desc"), color = Color(0xFFCCCCCC)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.clearSettledDebts()
                                        showClearConfirm = false
                                    }) {
                                        Text(LocalizedString.get("delete_all"), color = Color(0xFFFF4757))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearConfirm = false }) {
                                        Text(LocalizedString.get("cancel"), color = SubtitleGray)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ABOUT SECTION
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("DebtBro v${BuildConfig.VERSION_NAME}", color = SubtitleGray, fontSize = 13.sp)
                    Text("Made with ❤️ for financial chaos", color = SubtitleGray.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text(LocalizedString.get("select_language"), color = Color.White) },
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = SubtitleGray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = PrimaryGreen,
                uncheckedThumbColor = SubtitleGray,
                uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}

@Composable
fun SettingsActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = if (color == Color.White) SubtitleGray else color)
        Column {
            Text(title, color = color, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = SubtitleGray, fontSize = 12.sp)
        }
    }
}
