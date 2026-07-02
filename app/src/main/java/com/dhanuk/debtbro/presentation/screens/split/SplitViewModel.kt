package com.dhanuk.debtbro.presentation.screens.split

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.SplitEntity
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.AiRepository
import com.dhanuk.debtbro.data.repository.SplitRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplitUiState(
    val title: String = "",
    val totalAmount: String = "",
    val participantName: String = "",
    val participants: List<String> = listOf("Me"),
    val perPerson: Double = 0.0,
    val aiSummary: String = "",
    val isLoading: Boolean = false,
    val currencySymbol: String = "₹"
)

@HiltViewModel
class SplitViewModel @Inject constructor(
    private val splits: SplitRepository,
    private val debts: DebtRepository,
    private val ai: AiRepository,
    private val authManager: AuthManager,
    private val syncManager: SyncManager,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = _state.asStateFlow()

    private val _showAuthPrompt = MutableStateFlow(false)
    val showAuthPrompt: StateFlow<Boolean> = _showAuthPrompt.asStateFlow()

    fun dismissAuthPrompt() { _showAuthPrompt.value = false }

    val pastSplits: StateFlow<List<SplitEntity>> = splits.getAllSplits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            prefs.defaultCurrency.collect { symbol ->
                _state.value = _state.value.copy(currencySymbol = symbol)
            }
        }
        viewModelScope.launch {
            _state.collect { s ->
                val total = s.totalAmount.toDoubleOrNull() ?: 0.0
                val perPerson = total / s.participants.size.coerceAtLeast(1)
                if (perPerson != s.perPerson) {
                    _state.value = s.copy(perPerson = perPerson)
                }
            }
        }
    }

    fun updateTitle(value: String) { _state.value = _state.value.copy(title = value) }
    fun updateTotal(value: String) { _state.value = _state.value.copy(totalAmount = value) }
    fun updateParticipant(value: String) { _state.value = _state.value.copy(participantName = value) }

    fun addParticipant(name: String = _state.value.participantName) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank() && _state.value.participants.size < 50) {
            if (!_state.value.participants.contains(trimmed)) {
                _state.value = _state.value.copy(
                    participants = _state.value.participants + trimmed,
                    participantName = ""
                )
            }
        }
    }

    fun removeParticipant(name: String) {
        if (name != "Me") {
            _state.value = _state.value.copy(
                participants = _state.value.participants.filterNot { it == name }
            )
        }
    }

    fun createSplit(onCreated: (SplitEntity) -> Unit) = viewModelScope.launch {
        if (!prefs.isGoogleSignedIn.first()) {
            _showAuthPrompt.value = true
            return@launch
        }
        val s = _state.value
        val total = s.totalAmount.toDoubleOrNull() ?: return@launch
        _state.value = s.copy(isLoading = true)

        val split = SplitEntity(
            title = s.title.ifBlank { "Untitled split" },
            totalAmount = total,
            participants = Gson().toJson(s.participants),
            perPersonAmount = s.perPerson
        )
        val id = splits.insertSplit(split).toInt()
        val createdSplit = split.copy(id = id)

        // Push immediately to Firestore if signed in
        authManager.getCurrentUser()?.uid?.let { uid ->
            pushSplitImmediately(uid, createdSplit)
        }

        _state.value = _state.value.copy(isLoading = false)
        onCreated(createdSplit)
        syncIfSignedIn()
    }

    fun createDebtsFromSplit(split: SplitEntity) = viewModelScope.launch {
        val names: List<String> = Gson().fromJson(
            split.participants,
            object : TypeToken<List<String>>() {}.type
        )
        names.filterNot { it.equals("Me", true) }.forEach { name ->
            debts.insertDebt(
                DebtEntity(
                    personName = name,
                    personEmoji = "🍽️",
                    amount = split.perPersonAmount,
                    description = split.title,
                    type = "THEY_OWE_ME"
                )
            )
        }
        syncIfSignedIn()
    }

    fun getAiSummary(split: SplitEntity) = viewModelScope.launch {
        val names: List<String> = Gson().fromJson(
            split.participants,
            object : TypeToken<List<String>>() {}.type
        )
        val summary = ai.generateSplitSummary(
            split.title,
            split.totalAmount,
            split.perPersonAmount,
            names.size
        ).getOrElse { "Everyone owes ${split.perPersonAmount.toInt()} each. Receipts don't lie." }

        splits.updateAiSummary(split.id, summary)
        if (_state.value.title == split.title) {
            _state.value = _state.value.copy(aiSummary = summary)
        }
        syncIfSignedIn()
    }

    private fun syncIfSignedIn() = viewModelScope.launch {
        authManager.getCurrentUser()?.uid?.let { uid ->
            runCatching { syncManager.mergePendingUnsynced(uid) }
        }
    }

    private suspend fun pushSplitImmediately(userId: String, split: SplitEntity) {
        runCatching { syncManager.pushNewSplit(userId, split) }
    }
}
