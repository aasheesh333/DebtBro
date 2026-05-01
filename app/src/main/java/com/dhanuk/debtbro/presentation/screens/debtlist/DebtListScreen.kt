package com.dhanuk.debtbro.presentation.screens.debtlist

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.presentation.components.DebtCard
import com.dhanuk.debtbro.presentation.components.EmptyStateView
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.presentation.theme.SubtitleGray

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DebtListScreen(
    onAddDebtClick: () -> Unit,
    onDebtClick: (Int) -> Unit,
    viewModel: DebtListViewModel = hiltViewModel()
) {
    val debts by viewModel.filteredDebts.collectAsStateWithLifecycle()
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterStatus by viewModel.filterStatus.collectAsStateWithLifecycle()
    
    val hapticFeedback = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "All Debts",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onAddDebtClick,
                    modifier = Modifier.clip(CircleShape).background(PrimaryGreen)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.Black)
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search by name or reason...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = SubtitleGray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, null, tint = SubtitleGray)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = Color(0xFF333333),
                    containerColor = Color(0xFF1E1E1E)
                )
            )

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = PrimaryGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = PrimaryGreen
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectedTab.value = 0 },
                    text = { Text("💰 They Owe Me", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectedTab.value = 1 },
                    text = { Text("😅 I Owe Them", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL", "PENDING", "SETTLED").forEach { status ->
                    FilterChip(
                        selected = filterStatus == status,
                        onClick = { viewModel.filterStatus.value = status },
                        label = { Text(status.lowercase().replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E1E1E),
                            labelColor = SubtitleGray
                        ),
                        border = null
                    )
                }
            }

            // Debt List
            if (debts.isEmpty()) {
                EmptyStateView(
                    emoji = if (searchQuery.isNotEmpty()) "🔍" else "🏜️",
                    title = if (searchQuery.isNotEmpty()) "No results found" else "No debts here!",
                    subtitle = if (searchQuery.isNotEmpty()) "Try a different name or keyword" else "You're either rich or lonely. Add a debt to start."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(debts, key = { it.id }) { debt ->
                        SwipeableDebtCard(
                            debt = debt,
                            isSignedIn = signedIn,
                            onClick = { onDebtClick(debt.id) },
                            onSettle = { 
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.markSettled(it) 
                            },
                            onDelete = { viewModel.deleteDebt(it) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableDebtCard(
    debt: DebtEntity,
    isSignedIn: Boolean,
    onClick: () -> Unit,
    onSettle: (DebtEntity) -> Unit,
    onDelete: (DebtEntity) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { showMenu = true }
        )
    ) {
        DebtCard(debt, isSignedIn, onClick)
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color(0xFF2A2A2A))
        ) {
            if (debt.status != "SETTLED") {
                DropdownMenuItem(
                    text = { Text("Mark Settled ✅", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onSettle(debt)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete 🗑️", color = Color(0xFFFF4757)) },
                onClick = {
                    showMenu = false
                    onDelete(debt)
                }
            )
        }
    }
}
