package com.dhanuk.debtbro.presentation.screens.debtlist

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.LocalizedString

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
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current
    val hapticFeedback = LocalHapticFeedback.current
    var showSortMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = UITokens.ScreenHorizontalPadding, vertical = UITokens.ScreenTopPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(LocalizedString.get("all_debts"), color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontDisplay, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = LocalizedString.get("sort_debts"), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            listOf(
                                "CREATED_DESC" to LocalizedString.get("sort_newest"),
                                "AMOUNT_DESC" to LocalizedString.get("sort_amount_high"),
                                "AMOUNT_ASC" to LocalizedString.get("sort_amount_low"),
                                "NAME_ASC" to LocalizedString.get("sort_name"),
                                "DUE_DATE_ASC" to LocalizedString.get("sort_due_date"),
                                "STATUS" to LocalizedString.get("sort_status")
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (sortOrder == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (sortOrder == key) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { viewModel.setSortOrder(key); showSortMenu = false },
                                    trailingIcon = { if (sortOrder == key) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onAddDebtClick,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = LocalizedString.get("add_debt"), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text(LocalizedString.get("search_debts")) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = UITokens.ScreenHorizontalPadding),
                leadingIcon = { Icon(Icons.Default.Search, LocalizedString.get("search_debts"), tint = extra.subtitleGray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = LocalizedString.get("cancel"), tint = extra.subtitleGray)
                        }
                    }
                },
                shape = UITokens.ShapeMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {}
            ) {
                Tab(selected = selectedTab == 0, onClick = { viewModel.selectedTab.value = 0 },
                    text = { Text(LocalizedString.get("they_owe_me"), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) })
                Tab(selected = selectedTab == 1, onClick = { viewModel.selectedTab.value = 1 },
                    text = { Text(LocalizedString.get("i_owe_them"), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) })
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = UITokens.ScreenHorizontalPadding, vertical = UITokens.SpaceSmall),
                horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS)
            ) {
                listOf("ALL", "PENDING", "SETTLED").forEach { status ->
                    FilterChip(
                        selected = filterStatus == status,
                        onClick = { viewModel.filterStatus.value = status },
                        label = { Text(status.lowercase().replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = extra.subtitleGray
                        ),
                        border = null
                    )
                }
            }

            if (debts.isEmpty()) {
                EmptyStateView(
                    icon = if (searchQuery.isNotEmpty()) Icons.Default.Search else Icons.Default.AccountBalanceWallet,
                    title = if (searchQuery.isNotEmpty()) LocalizedString.get("no_results") else LocalizedString.get("no_debts"),
                    subtitle = if (searchQuery.isNotEmpty()) LocalizedString.get("try_different") else LocalizedString.get("empty_owed")
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(UITokens.CardInnerPadding),
                    verticalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)
                ) {
                    items(debts, key = { it.id }) { debt ->
                        SwipeableDebtCard(debt, signedIn, { onDebtClick(debt.id) },
                            onSettle = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.markSettled(it) },
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
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (debt.status != "SETTLED") {
                DropdownMenuItem(
                    text = { Text(LocalizedString.get("mark_settled"), color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { showMenu = false; onSettle(debt) }
                )
            }
            DropdownMenuItem(
                text = { Text(LocalizedString.get("delete_trash"), color = MaterialTheme.colorScheme.error) },
                onClick = { showMenu = false; onDelete(debt) }
            )
        }
    }
}
