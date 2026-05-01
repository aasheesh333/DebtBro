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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            item {
                Text(
                    "Settings",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ACCOUNT SECTION
            item { SectionHeader("Account", Icons.Default.AccountCircle) }
            item {
                GoogleSignInCard(
                    isSignedIn = state.isSignedIn,
                    name = state.googleName,
                    email = state.email,
                    lastSynced = state.lastSynced,
                    onSignIn = { viewModel.signInWithGoogle(context as Activity) },
                    onSignOut = { viewModel.signOut() },
                    onSync = { viewModel.syncNow() }
                )
            }

            // PREFERENCES SECTION
            item { SectionHeader("Preferences", Icons.Default.Tune) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = state.userName,
                            onValueChange = viewModel::saveUserName,
                            label = { Text("Your Display Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryGreen,
                                unfocusedBorderColor = Color(0xFF333333)
                            )
                        )
                        
                        Text("Default Currency", color = SubtitleGray, fontSize = 13.sp)
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

                        Text("Nudge Roast Level", color = SubtitleGray, fontSize = 13.sp)
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
                            Text("Language", color = Color.White, fontWeight = FontWeight.Bold)
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
            item { SectionHeader("Data & Export", Icons.Default.Storage) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsActionItem(
                            title = "Export to CSV",
                            subtitle = "Download all debts as a spreadsheet",
                            icon = Icons.Default.FileUpload,
                            onClick = { viewModel.exportCsv(context) }
                        )
                        Divider(color = Color(0xFF2A2A2A))
                        SettingsActionItem(
                            title = "Clear Settled Debts",
                            subtitle = "Permanently delete fully paid debts",
                            icon = Icons.Default.DeleteSweep,
                            color = Color(0xFFFF4757),
                            onClick = { viewModel.clearSettledDebts() }
                        )
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
            title = { Text("Select Language", color = Color.White) },
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
