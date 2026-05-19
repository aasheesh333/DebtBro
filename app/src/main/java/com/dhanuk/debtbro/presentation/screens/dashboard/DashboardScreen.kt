package com.dhanuk.debtbro.presentation.screens.dashboard

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.components.DebtCard
import com.dhanuk.debtbro.presentation.theme.DangerRed
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.presentation.theme.SubtitleGray
import com.dhanuk.debtbro.util.formatCurrency
import com.dhanuk.debtbro.util.LocalizedString
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddDebtClick: () -> Unit,
    onSeeAllClick: () -> Unit,
    onSplitClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDebtClick: (Int) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPrompt by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(2000)
            isRefreshing = false
        }
    }
    
    LaunchedEffect(state.hasShownSignInPrompt, state.isSignedIn) {
        if (!state.hasShownSignInPrompt && !state.isSignedIn) {
            delay(3000)
            showPrompt = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "${LocalizedString.get("greeting")} ${state.userName}! 👋",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            LocalizedString.get("money_memory_subtitle"),
                            color = SubtitleGray,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.clip(CircleShape).background(Color(0xFF1E1E1E))
                    ) {
                        Icon(Icons.Default.Settings, null, tint = Color.White)
                    }
                }
            }

            // Stats Card
            item {
                StatsCard(
                    totalOwedToMe = state.totalOwedToMe,
                    totalIOwe = state.totalIOwe,
                    recoveryRate = state.recoveryRate
                )
            }

            // Action Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        LocalizedString.get("add_debt"),
                        Icons.Default.Add,
                        PrimaryGreen,
                        Modifier.weight(1f),
                        onAddDebtClick
                    )
                    ActionButton(
                        LocalizedString.get("split_bill"),
                        Icons.Default.CallSplit,
                        Color.White,
                        Modifier.weight(1f),
                        onSplitClick
                    )
                }
            }

            // Overdue Section
            if (state.overdueDebts.isNotEmpty()) {
                item {
                    SectionHeader(LocalizedString.get("overdue"), null)
                }
                items(state.overdueDebts, key = { "overdue_${it.id}" }) { debt ->
                    DebtCard(debt, state.isSignedIn, { onDebtClick(debt.id) })
                }
            }

            // Recent Debts
            item {
                SectionHeader(LocalizedString.get("recent_debts"), LocalizedString.get("see_all")) { onSeeAllClick() }
            }
            if (state.recentDebts.isEmpty()) {
                item {
                    EmptyState(LocalizedString.get("no_active"))
                }
            } else {
                items(state.recentDebts, key = { "recent_${it.id}" }) { debt ->
                    DebtCard(debt, state.isSignedIn, { onDebtClick(debt.id) })
                }
            }

            // Leaderboard
            item {
                SectionHeader(LocalizedString.get("leaderboard"), null)
            }
            items(state.leaderboard, key = { "leaderboard_${it.id}" }) { debt ->
                LeaderboardItem(debt, state.leaderboard.indexOf(debt) + 1)
            }
        }
        } // PullToRefreshBox
    }

    if (showPrompt) {
        ModalBottomSheet(
            onDismissRequest = { 
                showPrompt = false
                viewModel.dismissPrompt()
            },
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(LocalizedString.get("cloud_sync"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    LocalizedString.get("cloud_sync_desc"),
                    color = SubtitleGray,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        showPrompt = false
                        viewModel.dismissPrompt()
                        onSettingsClick()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text(LocalizedString.get("go_to_settings"), color = Color.Black, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = {
                    showPrompt = false
                    viewModel.dismissPrompt()
                }) {
                    Text(LocalizedString.get("maybe_later"), color = SubtitleGray)
                }
            }
        }
    }
}

@Composable
fun StatsCard(totalOwedToMe: Double, totalIOwe: Double, recoveryRate: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(LocalizedString.get("total_owed_to_me"), color = SubtitleGray, fontSize = 13.sp)
                    Text(
                        formatCurrency(totalOwedToMe),
                        color = PrimaryGreen,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(PrimaryGreen.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.TrendingUp, null, tint = PrimaryGreen)
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            Divider(color = Color(0xFF2A2A2A))
            
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(LocalizedString.get("i_owe"), color = SubtitleGray, fontSize = 12.sp)
                    Text(
                        formatCurrency(totalIOwe),
                        color = DangerRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(LocalizedString.get("recovery_rate"), color = SubtitleGray, fontSize = 12.sp)
                    Text(
                        "$recoveryRate%",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = recoveryRate / 100f,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = PrimaryGreen,
                trackColor = Color(0xFF2A2A2A)
            )
        }
    }
}

@Composable
fun ActionButton(text: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(56.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E),
        border = if (color == PrimaryGreen) null else BorderStroke(1.dp, Color(0xFF333333))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String?, onActionClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (action != null) {
            Text(
                action,
                color = PrimaryGreen,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onActionClick() }
            )
        }
    }
}

@Composable
fun LeaderboardItem(debt: com.dhanuk.debtbro.data.db.entity.DebtEntity, rank: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#$rank",
                color = if (rank == 1) Color(0xFFFFD700) else SubtitleGray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp)
            )
            Text(debt.personEmoji, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Text(debt.personName, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(formatCurrency(debt.amount - debt.amountPaid, debt.currency), color = PrimaryGreen, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📭", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(message, color = SubtitleGray, textAlign = TextAlign.Center)
    }
}
