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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.components.LoadingDotsIndicator
import com.dhanuk.debtbro.presentation.theme.DangerRed
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.presentation.theme.SubtitleGray
import com.dhanuk.debtbro.util.formatCurrency
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import kotlinx.coroutines.Dispatchers

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val insight by viewModel.aiInsight.collectAsStateWithLifecycle()
    val loading by viewModel.isLoadingInsight.collectAsStateWithLifecycle()

    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(state.monthlyData) {
        if (state.monthlyData.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    series(state.monthlyData.map { it.second })
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            item {
                Text(
                    "Financial Insights",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Stat Cards Grid
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnalyticsStatCard(
                        title = "Owed to Me",
                        amount = state.totalOwedToMe,
                        color = PrimaryGreen,
                        icon = Icons.Default.ArrowDownward,
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsStatCard(
                        title = "I Owe",
                        amount = state.totalIOwe,
                        color = DangerRed,
                        icon = Icons.Default.ArrowUpward,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnalyticsStatCard(
                        title = "All-time Settled",
                        amount = state.totalSettled,
                        color = Color.White,
                        icon = Icons.Default.DoneAll,
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsStatCard(
                        title = "Net Balance",
                        amount = state.netBalance,
                        color = if (state.netBalance >= 0) PrimaryGreen else DangerRed,
                        icon = Icons.Default.AccountBalanceWallet,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recovery Rate Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Recycling, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Debt Recovery Rate", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${state.recoveryRate}%", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = state.recoveryRate / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = PrimaryGreen,
                            trackColor = Color(0xFF2A2A2A)
                        )
                    }
                }
            }

            // Chart Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Monthly Lending Trend", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                rememberColumnCartesianLayer(
                                    columnProvider = { _ ->
                                        listOf(
                                            rememberLineComponent(
                                                color = PrimaryGreen,
                                                thickness = 12.dp,
                                                shape = com.patrykandpatrick.vico.core.common.shape.Shape.Rounded(4)
                                            )
                                        )
                                    }
                                ),
                                startAxis = rememberStartAxis(
                                    label = com.patrykandpatrick.vico.compose.common.component.rememberTextComponent(color = SubtitleGray)
                                ),
                                bottomAxis = rememberBottomAxis(
                                    label = com.patrykandpatrick.vico.compose.common.component.rememberTextComponent(color = SubtitleGray),
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
                    FunStatItem("🏅 Most Trusted", state.mostTrustedFriend, Modifier.weight(1f))
                    FunStatItem("💀 Worst Offender", state.worstOffender, Modifier.weight(1f))
                }
            }

            // AI Insights Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖 AI Financial Take", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { viewModel.loadAiInsight() }) {
                                Icon(Icons.Default.Refresh, null, tint = PrimaryGreen)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A2A2A))
                                .padding(16.dp)
                        ) {
                            if (loading) {
                                LoadingDotsIndicator(color = PrimaryGreen)
                            } else {
                                Text(
                                    insight.ifBlank { "Tap refresh to get a brutally honest analysis of your finances." },
                                    color = Color.White,
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

@Composable
fun AnalyticsStatCard(title: String, amount: Double, color: Color, icon: ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(title, color = SubtitleGray, fontSize = 12.sp)
            Text(
                formatCurrency(amount),
                color = color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FunStatItem(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .padding(12.dp)
    ) {
        Text(label, color = SubtitleGray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
