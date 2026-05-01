package com.dhanuk.debtbro.presentation.screens.debtlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.presentation.components.BannerAdView
import com.dhanuk.debtbro.presentation.components.DebtCard
import com.dhanuk.debtbro.presentation.components.EmptyStateView

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DebtListScreen(onAddDebt: () -> Unit, onDebtClick: (Int) -> Unit, viewModel: DebtListViewModel = hiltViewModel()) {
    val debts by viewModel.filteredDebts.collectAsStateWithLifecycle()
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filterStatus.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Debts", style = MaterialTheme.typography.headlineMedium)
            TabRow(tab) {
                Tab(tab == 0, { viewModel.selectedTab.value = 0 }, text = { Text("💰 They Owe Me") })
                Tab(tab == 1, { viewModel.selectedTab.value = 1 }, text = { Text("😅 I Owe Them") })
            }
            OutlinedTextField(query, { viewModel.searchQuery.value = it }, label = { Text("Search debts") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("ALL", "PENDING", "PARTIAL", "SETTLED").forEach { f -> FilterChip(filter == f, { viewModel.filterStatus.value = f }, label = { Text(f.lowercase().replaceFirstChar { it.uppercase() }) }) } }
            if (debts.isEmpty()) EmptyStateView("🤔", "No debts here!", "You're either rich or lonely.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(debts, key = { it.id }) { debt -> MenuDebtCard(debt, signedIn, onDebtClick, viewModel::markSettled, viewModel::deleteDebt) }
                item { BannerAdView() }
            }
        }
        FloatingActionButton(onClick = onAddDebt, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) { Icon(Icons.Default.Add, null) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuDebtCard(debt: DebtEntity, signedIn: Boolean, onDebtClick: (Int) -> Unit, onSettle: (DebtEntity) -> Unit, onDelete: (DebtEntity) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.combinedClickable(onClick = { onDebtClick(debt.id) }, onLongClick = { menu = true })) {
        DebtCard(debt, signedIn, { onDebtClick(debt.id) })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Mark settled") }, onClick = { menu = false; onSettle(debt) })
            DropdownMenuItem(text = { Text("Nudge") }, onClick = { menu = false })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete(debt) })
        }
    }
}
