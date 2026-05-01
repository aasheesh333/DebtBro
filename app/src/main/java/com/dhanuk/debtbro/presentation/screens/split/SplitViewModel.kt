package com.dhanuk.debtbro.presentation.screens.split

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.SplitEntity
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.GroqRepository
import com.dhanuk.debtbro.data.repository.SplitRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplitUiState(val title: String = "", val totalAmount: String = "", val participantName: String = "", val participants: List<String> = listOf("Me"), val perPerson: Double = 0.0, val aiSummary: String = "")

@HiltViewModel
class SplitViewModel @Inject constructor(private val splits: SplitRepository, private val debts: DebtRepository, private val groq: GroqRepository) : ViewModel() {
    private val form = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = form.combine(form) { a, _ -> a.copy(perPerson = (a.totalAmount.toDoubleOrNull() ?: 0.0) / a.participants.size.coerceAtLeast(1)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SplitUiState())
    val pastSplits: StateFlow<List<SplitEntity>> = splits.getAllSplits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun updateTitle(value: String) { form.value = form.value.copy(title = value) }
    fun updateTotal(value: String) { form.value = form.value.copy(totalAmount = value) }
    fun updateParticipant(value: String) { form.value = form.value.copy(participantName = value) }
    fun addParticipant(name: String = form.value.participantName) { if (name.isNotBlank()) form.value = form.value.copy(participants = form.value.participants + name.trim(), participantName = "") }
    fun removeParticipant(name: String) { form.value = form.value.copy(participants = form.value.participants.filterNot { it == name }.ifEmpty { listOf("Me") }) }
    fun createSplit(onCreated: (SplitEntity) -> Unit) = viewModelScope.launch {
        val s = state.value
        val total = s.totalAmount.toDoubleOrNull() ?: return@launch
        val split = SplitEntity(title = s.title.ifBlank { "Untitled split" }, totalAmount = total, participants = Gson().toJson(s.participants), perPersonAmount = s.perPerson)
        val id = splits.insertSplit(split).toInt()
        onCreated(split.copy(id = id))
    }
    fun createDebtsFromSplit(split: SplitEntity) = viewModelScope.launch {
        val names: List<String> = Gson().fromJson(split.participants, object : TypeToken<List<String>>() {}.type)
        names.filterNot { it.equals("me", true) }.forEach { name -> debts.insertDebt(DebtEntity(personName = name, personEmoji = "🍽️", amount = split.perPersonAmount, description = split.title, type = "THEY_OWE_ME")) }
    }
    fun getAiSummary(split: SplitEntity) = viewModelScope.launch {
        val names: List<String> = Gson().fromJson(split.participants, object : TypeToken<List<String>>() {}.type)
        val summary = groq.generateSplitSummary(split.title, split.totalAmount, split.perPersonAmount, names.size).getOrElse { "Everyone owes ${split.perPersonAmount.toInt()} each. Democracy, but with receipts." }
        splits.updateAiSummary(split.id, summary)
        form.value = form.value.copy(aiSummary = summary)
    }
}
