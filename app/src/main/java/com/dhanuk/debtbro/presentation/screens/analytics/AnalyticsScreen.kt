package com.dhanuk.debtbro.presentation.screens.analytics

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.components.LoadingDotsIndicator
import com.dhanuk.debtbro.presentation.theme.DangerRed
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.formatCurrency
import com.dhanuk.debtbro.util.LocalizedString
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.shape.toVicoShape
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import kotlinx.coroutines.Dispatchers

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val insight by viewModel.aiInsight.collectAsStateWithLifecycle()
    val loading by viewModel.isLoadingInsight.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current

    val modelProducer = remember { CartesianChartModelProducer.build() }
    
    LaunchedEffect(state.monthlyData) {
        if (state.monthlyData.isNotEmpty()) {
            modelProducer.tryRunTransaction {
                columnSeries {
                    series(state.monthlyData.map { it.second })
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.totalOwedToMe == 0.0 && state.totalIOwe == 0.0 && state.totalSettled == 0.0) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📊", fontSize = UITokens.FontEmojiLarge)
                Spacer(Modifier.height(16.dp))
                Text(LocalizedString.get("no_data_yet"), color = MaterialTheme.colorScheme.onBackground, fontSize = UITokens.FontSubhead, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(LocalizedString.get("add_debts_to_see_stats"), color = extra.subtitleGray, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = UITokens.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(UITokens.SpaceLarge),
            contentPadding = PaddingValues(top = UITokens.ScreenTopPadding, bottom = UITokens.ScreenBottomPadding)
        ) {
            item {
                Text(
                    LocalizedString.get("financial_insights"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = UITokens.FontDisplay,
                    fontWeight = FontWeight.Bold
                )
            }

            // Stat Cards Grid
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)) {
                    AnalyticsStatCard(
                        title = LocalizedString.get("owed_to_me"),
                        amount = state.totalOwedToMe,
                        color = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.ArrowDownward,
                        currency = state.currency,
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsStatCard(
                        title = LocalizedString.get("i_owe"),
                        amount = state.totalIOwe,
                        color = DangerRed,
                        icon = Icons.Default.ArrowUpward,
                        currency = state.currency,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)) {
                    AnalyticsStatCard(
                        title = LocalizedString.get("all_time_settled"),
                        amount = state.totalSettled,
                        color = MaterialTheme.colorScheme.onSurface,
                        icon = Icons.Default.DoneAll,
                        currency = state.currency,
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsStatCard(
                        title = LocalizedString.get("net_balance"),
                        amount = state.netBalance,
                        color = if (state.netBalance >= 0) MaterialTheme.colorScheme.primary else DangerRed,
                        icon = Icons.Default.AccountBalanceWallet,
                        currency = state.currency,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recovery Rate Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = UITokens.ShapeLarge
                ) {
                    Column(Modifier.padding(UITokens.SpaceLarge)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Recycling, contentDescription = LocalizedString.get("debt_recovery_rate"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(LocalizedString.get("debt_recovery_rate"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${state.recoveryRate}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = state.recoveryRate / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = extra.divider
                        )
                    }
                }
            }

            // Chart Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = UITokens.ShapeLarge
                ) {
                    Column(Modifier.padding(UITokens.SpaceLarge)) {
                        Text(LocalizedString.get("monthly_trend"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                rememberColumnCartesianLayer(
                                    columnProvider = com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer.ColumnProvider.series(
                                        rememberLineComponent(
                                            color = MaterialTheme.colorScheme.primary,
                                            thickness = 12.dp,
                                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp).toVicoShape()
                                        )
                                    )
                                ),
                                startAxis = rememberStartAxis(
                                    label = com.patrykandpatrick.vico.compose.common.component.rememberTextComponent(color = extra.subtitleGray)
                                ),
                                bottomAxis = rememberBottomAxis(
                                    label = com.patrykandpatrick.vico.compose.common.component.rememberTextComponent(color = extra.subtitleGray),
                                    valueFormatter = { value, _, _ ->
                                        state.monthlyData.getOrNull(value.toInt())?.first ?: ""
                                    }
                                )
                            ),
                            modelProducer = modelProducer,
                            modifier = Modifier.height(200.dp)
                        )
                    }
                }
            }

            // Fun Stats Section
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FunStatItem(LocalizedString.get("most_trusted"), state.mostTrustedFriend, Modifier.weight(1f))
                    FunStatItem(LocalizedString.get("worst_offender"), state.worstOffender, Modifier.weight(1f))
                }
            }

            // AI Insights Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = UITokens.ShapeLarge
                ) {
                    Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(LocalizedString.get("ai_take"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = UITokens.FontSubhead)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { viewModel.loadAiInsight() }) {
                                Icon(Icons.Default.Refresh, contentDescription = LocalizedString.get("regenerate"), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(UITokens.ShapeMedium)
                                .background(extra.cardInner)
                                .padding(UITokens.CardInnerPadding)
                        ) {
                            if (loading) {
                                LoadingDotsIndicator(color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text(
                                    insight.ifBlank { LocalizedString.get("tap_refresh") },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun AnalyticsStatCard(title: String, amount: Double, color: Color, icon: ImageVector, currency: String = "₹", modifier: Modifier) {
    val extra = LocalExtraColors.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = UITokens.ShapeLarge
    ) {
        Column(Modifier.padding(UITokens.CardInnerPadding), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(UITokens.IconMedium))
            Text(title, color = extra.subtitleGray, fontSize = UITokens.FontCaption)
            Text(
                formatCurrency(amount, currency),
                color = color,
                fontSize = UITokens.FontSubhead,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FunStatItem(label: String, value: String, modifier: Modifier) {
    val extra = LocalExtraColors.current
    Column(
        modifier = modifier
            .clip(UITokens.ShapeMedium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(UITokens.SpaceSmall)
    ) {
        Text(label, color = extra.subtitleGray, fontSize = UITokens.FontCaption)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontBody, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
