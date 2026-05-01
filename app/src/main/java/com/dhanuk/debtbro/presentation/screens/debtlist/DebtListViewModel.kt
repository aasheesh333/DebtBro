package com.dhanuk.debtbro.presentation.screens.debtlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebtListViewModel @Inject constructor(private val repository: DebtRepository, prefs: AppPreferences) : ViewModel() {
    val allDebts: StateFlow<List<DebtEntity>> = repository.getAllDebts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val searchQuery = MutableStateFlow("")
    val selectedTab = MutableStateFlow(0)
    val filterStatus = MutableStateFlow("ALL")
    val isSignedIn = prefs.isGoogleSignedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val filteredDebts: StateFlow<List<DebtEntity>> = combine(allDebts, searchQuery, selectedTab, filterStatus) { debts, query, tab, filter ->
        debts.filter { it.type == if (tab == 0) "THEY_OWE_ME" else "I_OWE_THEM" }
            .filter { filter == "ALL" || it.status == filter }
            .filter { query.isBlank() || it.personName.contains(query, true) || it.description.contains(query, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun deleteDebt(debt: DebtEntity) = viewModelScope.launch { repository.deleteDebt(debt) }
    fun markSettled(debt: DebtEntity) = viewModelScope.launch { repository.updateDebt(debt.copy(amountPaid = debt.amount, status = "SETTLED")) }
}
