package com.dhanuk.debtbro.presentation.screens.dashboard

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.components.DebtCard
import com.dhanuk.debtbro.presentation.theme.DangerRed
import com.dhanuk.debtbro.presentation.theme.GoldColor
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.formatCurrency
import com.dhanuk.debtbro.util.LocalizedString
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
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
    val extra = LocalExtraColors.current
    var showPrompt by remember { mutableStateOf(false) }
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refresh() }
    )

    LaunchedEffect(Unit) {
        // First-load sync if signed in
        viewModel.refresh()
    }

    LaunchedEffect(state.hasShownSignInPrompt, state.isSignedIn) {
        if (!state.hasShownSignInPrompt && !state.isSignedIn) {
            delay(3000)
            showPrompt = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = UITokens.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium),
            contentPadding = PaddingValues(top = UITokens.ScreenTopPadding, bottom = UITokens.ScreenBottomPadding)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "${LocalizedString.get("greeting")} ${state.userName}! \uD83D\uDC4B",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = UITokens.FontHeadline,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            LocalizedString.get("money_memory_subtitle"),
                            color = extra.subtitleGray,
                            fontSize = UITokens.FontSmall
                        )
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = LocalizedString.get("settings"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            item {
                StatsCard(
                    totalOwedToMe = state.totalOwedToMe,
                    totalIOwe = state.totalIOwe,
                    recoveryRate = state.recoveryRate
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)
                ) {
                    ActionButton(
                        LocalizedString.get("add_debt"),
                        Icons.Default.Add,
                        MaterialTheme.colorScheme.primary,
                        Modifier.weight(1f),
                        onAddDebtClick
                    )
                    ActionButton(
                        LocalizedString.get("split_bill"),
                        Icons.Default.CallSplit,
                        MaterialTheme.colorScheme.onSurface,
                        Modifier.weight(1f),
                        onSplitClick
                    )
                }
            }

            if (state.overdueDebts.isNotEmpty()) {
                item { SectionHeader(LocalizedString.get("overdue"), null) }
                items(state.overdueDebts, key = { "overdue_${it.id}" }) { debt ->
                    DebtCard(debt, state.isSignedIn, { onDebtClick(debt.id) })
                }
            }

            item {
                SectionHeader(LocalizedString.get("recent_debts"), LocalizedString.get("see_all")) { onSeeAllClick() }
            }
            if (state.recentDebts.isEmpty()) {
                item { EmptyState(LocalizedString.get("no_active")) }
            } else {
                items(state.recentDebts, key = { "recent_${it.id}" }) { debt ->
                    DebtCard(debt, state.isSignedIn, { onDebtClick(debt.id) })
                }
            }

            item { SectionHeader(LocalizedString.get("leaderboard"), null) }
            items(state.leaderboard, key = { "leaderboard_${it.id}" }) { debt ->
                LeaderboardItem(debt, state.leaderboard.indexOf(debt) + 1)
            }
        }
        }
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }

    if (showPrompt) {
        ModalBottomSheet(
            onDismissRequest = { showPrompt = false; viewModel.dismissPrompt() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(UITokens.SheetContentPadding).padding(bottom = UITokens.SheetBottomPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium)
            ) {
                Text(LocalizedString.get("cloud_sync"), fontSize = UITokens.FontHeadline, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(LocalizedString.get("cloud_sync_desc"), color = extra.subtitleGray, textAlign = TextAlign.Center)
                Button(
                    onClick = { showPrompt = false; viewModel.dismissPrompt(); onSettingsClick() },
                    modifier = Modifier.fillMaxWidth().height(UITokens.ButtonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text(LocalizedString.get("go_to_settings"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { showPrompt = false; viewModel.dismissPrompt() }) {
                    Text(LocalizedString.get("maybe_later"), color = extra.subtitleGray)
                }
            }
        }
    }
}

@Composable
fun StatsCard(totalOwedToMe: Double, totalIOwe: Double, recoveryRate: Int) {
    val extra = LocalExtraColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UITokens.SpaceXL),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(UITokens.SpaceLarge)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(LocalizedString.get("total_owed_to_me"), color = extra.subtitleGray, fontSize = UITokens.FontSmall)
                    Text(formatCurrency(totalOwedToMe), color = MaterialTheme.colorScheme.primary, fontSize = UITokens.FontDisplay, fontWeight = FontWeight.ExtraBold)
                }
                    Box(
                    modifier = Modifier.size(UITokens.AvatarLarge).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.TrendingUp, contentDescription = LocalizedString.get("total_owed_to_me"), tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = extra.divider)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(LocalizedString.get("i_owe"), color = extra.subtitleGray, fontSize = UITokens.FontCaption)
                    Text(formatCurrency(totalIOwe), color = DangerRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(LocalizedString.get("recovery_rate"), color = extra.subtitleGray, fontSize = 12.sp)
                    Text("$recoveryRate%", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { recoveryRate / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = extra.subtitleGray.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun ActionButton(text: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(56.dp).clickable { onClick() },
        shape = UITokens.ShapeLarge,
        color = MaterialTheme.colorScheme.surface,
        border = if (color == MaterialTheme.colorScheme.primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(UITokens.IconMedium))
            Spacer(Modifier.width(UITokens.SpaceXS))
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = UITokens.FontBody)
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
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontSubhead, fontWeight = FontWeight.Bold)
        if (action != null) {
            Text(action, color = MaterialTheme.colorScheme.primary, fontSize = UITokens.FontSmall, modifier = Modifier.clickable { onActionClick() })
        }
    }
}

@Composable
fun LeaderboardItem(debt: com.dhanuk.debtbro.data.db.entity.DebtEntity, rank: Int) {
    val extra = LocalExtraColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = UITokens.ShapeMedium
    ) {
        Row(modifier = Modifier.padding(UITokens.SpaceSmall), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#$rank",
                color = if (rank == 1) GoldColor else extra.subtitleGray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp)
            )
            Text(debt.personEmoji, fontSize = 20.sp)
            Spacer(Modifier.width(UITokens.SpaceSmall))
            Text(debt.personName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(formatCurrency(debt.amount - debt.amountPaid, debt.currency), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    val extra = LocalExtraColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("\uD83D\uDCED", fontSize = UITokens.FontEmojiLarge)
        Spacer(Modifier.height(UITokens.SpaceMedium))
        Text(message, color = extra.subtitleGray, textAlign = TextAlign.Center)
    }
}
