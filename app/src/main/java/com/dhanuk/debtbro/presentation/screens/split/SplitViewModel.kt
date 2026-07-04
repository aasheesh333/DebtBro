package com.dhanuk.debtbro.presentation.screens.split

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.ads.AdManager
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.SplitEntity
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.AiRepository
import com.dhanuk.debtbro.data.repository.SplitRepository
import com.dhanuk.debtbro.util.LocalizedString
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.reflect.Type
import javax.inject.Inject

private val LIST_STRING_TYPE: Type =
    TypeToken.getParameterized(List::class.java, String::class.java).type

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
    private val prefs: AppPreferences,
    private val adManager: AdManager
) : ViewModel() {

    private val _state = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = _state.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private val _showAuthPrompt = MutableStateFlow(false)
    val showAuthPrompt: StateFlow<Boolean> = _showAuthPrompt.asStateFlow()

    // Track splits that have already had debts created this session — prevents
    // duplicate-debt spam on rapid CREATE_DEBTS button taps (Wave 3 Issue 1).
    private val _splitsWithDebtsCreated = MutableStateFlow<Set<Int>>(emptySet())
    val splitsWithDebtsCreated = _splitsWithDebtsCreated.asStateFlow()

    // Reward-ad gate for AI Take (Wave 3 Issue 2). Mirror AnalyticsViewModel:
    // 5 free regenerations/day; once exhausted, gate behind a rewarded ad.
    private val _showRewardAd = MutableStateFlow(false)
    val showRewardAd: StateFlow<Boolean> = _showRewardAd.asStateFlow()
    private val _remainingFree = MutableStateFlow(AiRepository.MAX_FREE_REGENERATIONS)
    val remainingFree: StateFlow<Int> = _remainingFree.asStateFlow()
    var pendingRewardSplit: SplitEntity? = null

    // Interstitial trigger — emitted after createDebtsFromSplit so the
    // screen can call adManager.showInterstitialIfReady(activity) on a
    // natural task-complete transition. See DebtDetailViewModel for the
    // rationale and 5-min cooldown behavior.
    private val _showInterstitial = MutableSharedFlow<Unit>()
    val showInterstitial: SharedFlow<Unit> = _showInterstitial.asSharedFlow()

    fun showInterstitialIfReady(activity: android.app.Activity): Boolean =
        adManager.showInterstitialIfReady(activity, onDismissed = { /* AdManager pre-loads next */ })

    fun dismissRewardAd() { _showRewardAd.value = false }
    fun preloadRewardedAd(context: Context) { adManager.loadRewardedAd(context) }

    fun dismissAuthPrompt() { _showAuthPrompt.value = false }

    val pastSplits: StateFlow<List<SplitEntity>> = splits.getAllSplits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _remainingFree.value = ai.remainingFreeRegenerations()
        }
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
        // Local-first: persist unconditionally, sync only if a Firebase user
        // actually exists. Previously gated on prefs.isGoogleSignedIn which
        // blocked offline/"Skip for now" users from any primary action —
        // see offline-mode audit 2026-07-03.
        val s = _state.value
        val total = s.totalAmount.toDoubleOrNull() ?: return@launch
        runCatching {
            _state.value = s.copy(isLoading = true)

            val split = SplitEntity(
                title = s.title.ifBlank { "Untitled split" },
                totalAmount = total,
                participants = Gson().toJson(s.participants),
                perPersonAmount = s.perPerson
            )
            val id = splits.insertSplit(split).toInt()
            val createdSplit = split.copy(id = id)

            // Push immediately to Firestore only if actually signed in — failure
            // must not roll back the local insert.
            authManager.getCurrentUser()?.uid?.let { uid ->
                pushSplitImmediately(uid, createdSplit)
            }

            _state.value = _state.value.copy(isLoading = false)
            onCreated(createdSplit)
            syncIfSignedIn()
            // Clear the Create Bill form so the Create Bill button re-disables
            // (participants.size > 1 → false) and the user can't accidentally
            // re-submit the same bill twice. ListView pastSplits re-renders
            // from the splits Flow. (Wave 3 Issue 1.)
            // Preserve currencySymbol — the init { prefs.defaultCurrency }
            // collector above only updates _state when the *DataStore* value
            // changes, not on every reset; so wiping it here would briefly
            // lose the user's currency selection until the next prefs emit.
            _state.value = SplitUiState(currencySymbol = _state.value.currencySymbol)
        }.onFailure { _snackbar.tryEmit("Couldn't create split: ${it.message ?: "unknown error"}") }
    }

    fun createDebtsFromSplit(split: SplitEntity) = viewModelScope.launch {
        // Idempotency guard: one session, one tap. Prevents the in-session
        // "tap-spam creates duplicate debt rows" bug (Wave 3 Issue 1).
        // In-memory only — app restart resets the set, which is acceptable
        // per scope; a Room boolean column would require a migration.
        if (split.id in _splitsWithDebtsCreated.value) {
            _snackbar.tryEmit(LocalizedString.get("debts_already_created_for_split"))
            return@launch
        }
        runCatching {
            val names: List<String> = Gson().fromJson(
                split.participants,
                LIST_STRING_TYPE
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
            _splitsWithDebtsCreated.value = _splitsWithDebtsCreated.value + split.id
            // Interstitial at the "create debts from split — task complete"
            // moment. AdMob's canonical natural-transition timing. The 5-min
            // cooldown in AdManager self-throttles across all triggers.
            _showInterstitial.tryEmit(Unit)
            syncIfSignedIn()
        }.onFailure { _snackbar.tryEmit("Couldn't create debts from split: ${it.message ?: "unknown error"}") }
    }

    fun getAiSummary(split: SplitEntity, activity: android.app.Activity? = null) = viewModelScope.launch {
        // Reward-ad gate (Wave 3 Issue 2). Mirrors AnalyticsViewModel.loadAiInsight:
        // 5 free regenerations/day; once exhausted, force the user to watch a
        // rewarded ad before we'll regenerate again. Offline → silent no-op.
        if (!ai.canRegenerate()) {
            val connectivityManager = activity?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val isOffline = connectivityManager?.activeNetwork == null
            if (isOffline) return@launch
            if (activity != null) {
                adManager.showRewardedAd(activity, onRewarded = {
                    viewModelScope.launch {
                        ai.resetRegenerationCount()
                        _remainingFree.value = ai.remainingFreeRegenerations()
                        fetchAiSummaryInternal(split)
                    }
                }, onFailed = {
                    adManager.loadRewardedAd(activity)
                    _showRewardAd.value = false
                })
            } else {
                pendingRewardSplit = split
                _showRewardAd.value = true
            }
            return@launch
        }
        fetchAiSummaryInternal(split)
    }

    private fun fetchAiSummaryInternal(split: SplitEntity) = viewModelScope.launch {
        runCatching {
            val names: List<String> = Gson().fromJson(
                split.participants,
                LIST_STRING_TYPE
            )
            val summary = ai.generateSplitSummary(
                split.title,
                split.totalAmount,
                split.perPersonAmount,
                names.size
            ).getOrElse { LocalizedString.get("everyone_owes_each_receipts_dont_lie").replace("{currency}", _state.value.currencySymbol).replace("{amount}", String.format("%.2f", split.perPersonAmount)) }
            ai.incrementRegenerationCount()
            _remainingFree.value = ai.remainingFreeRegenerations()
            splits.updateAiSummary(split.id, summary)
            if (_state.value.title == split.title) {
                _state.value = _state.value.copy(aiSummary = summary)
            }
            syncIfSignedIn()
        }.onFailure { _snackbar.tryEmit("Couldn't fetch AI summary: ${it.message ?: "unknown error"}") }
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
