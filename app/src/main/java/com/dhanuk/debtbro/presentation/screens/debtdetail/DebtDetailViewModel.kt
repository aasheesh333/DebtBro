package com.dhanuk.debtbro.presentation.screens.debtdetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.PaymentEntity
import com.dhanuk.debtbro.data.ads.AdManager
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import com.dhanuk.debtbro.data.repository.AiRepository
import com.dhanuk.debtbro.data.repository.PaymentRepository
import com.dhanuk.debtbro.util.CanvasExporter
import com.dhanuk.debtbro.util.HtmlExporter
import com.dhanuk.debtbro.util.LocalizedString
import com.dhanuk.debtbro.util.shareTextToWhatsApp
import com.dhanuk.debtbro.util.toReadableDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debtRepository: DebtRepository,
    private val paymentRepository: PaymentRepository,
    private val aiRepository: AiRepository,
    private val prefs: AppPreferences,
    private val authManager: AuthManager,
    private val syncManager: SyncManager,
    private val adManager: AdManager
) : ViewModel() {

    companion object {
        /**
         * P1-5: how long we wait for a deep-link firebaseId to resolve
         * against the local Room cache before declaring the link
         * unopenable. 5s is long enough for the typical full-sync path
         * (Firestore pull → Room insert → flatMapLatest emits) but short
         * enough that a user opening a stale / foreign share card isn't
         * stuck on the "not found" screen silently.
         */
        private const val DEEP_LINK_TIMEOUT_MS = 5_000L
    }

    /**
     * Resolves the `debtId` nav argument to a local Room Int id.
     *
     * Fix history (2026-07-03, offline-mode audit):
     *  Previously this ViewModel hard-required an Int and crashed the navigation
     *  graph if the deep-link `debtbro://debt/{debtId}` carried anything else
     *  (e.g. a Firestore firebaseId from a shared card, or a malformed path
     *  like `debtbro://debt/abc`). Now the route is `NavType.StringType` and we
     *  resolve in three steps inside an init coroutine:
     *    1. If the string is an integer, set `_resolvedDebtId` directly.
     *    2. Else try a Firestore firebaseId lookup against the DAO.
     *    3. Else leave `_resolvedDebtId = null` — the UI shows a "debt not
     *       found" state rather than crashing the nav graph.
     *
     * `debt.value` is exposed as a StateFlow that flatMapLatest over the
     * resolved id, so a deferred firebaseId lookup updates the UI once the
     * local row is found.
     */
    private val _resolvedDebtId = MutableStateFlow<Int?>(null)
    val debt: StateFlow<DebtEntity?> = _resolvedDebtId.flatMapLatest { id ->
        if (id == null) flowOf(null) else debtRepository.observeDebtById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * P1-5 (2026-07-03): resolution-timeout flag for deep-link debtIds.
     *
     * `_resolvedDebtId` starts null and is set in [init] either synchronously
     * (when the nav arg is an Int) or after a Firestore firebaseId lookup.
     * If the firebaseId isn't found locally — e.g. the user opened a
     * `debtbro://debt/<id>` link from a share card but the linked row isn't
     * in Room yet — the lookup returns null and `_resolvedDebtId` stays null
     * forever, leaving the user staring at the "Debt not found" screen with
     * no feedback that we actually tried and gave up.
     *
     * This flag flips to `true` 5 seconds after init if the id hasn't
     * resolved by then, and emits a toast so the user knows we timed out
     * rather than silently failed. The screen still shows the "not found"
     * view either way; this just adds feedback + a pop hint.
     */
    private val _resolutionTimedOut = MutableStateFlow(false)
    val resolutionTimedOut: StateFlow<Boolean> = _resolutionTimedOut.asStateFlow()
    var pendingDeepLinkToast: String? = null
        private set

    init {
        viewModelScope.launch {
            val raw = savedStateHandle.get<String>("debtId")
                ?: return@launch
            _resolvedDebtId.value = raw.toIntOrNull()
                ?: debtRepository.getDebtByFirebaseId(raw)?.id
            // If still unresolved, start a 5s timer so the user gets
            // feedback rather than a silent "not found" screen.
            if (_resolvedDebtId.value == null) {
                kotlinx.coroutines.delay(DEEP_LINK_TIMEOUT_MS)
                if (_resolvedDebtId.value == null) {
                    _resolutionTimedOut.value = true
                    pendingDeepLinkToast = LocalizedString.get("debt_link_not_opened")
                    _toastEvent.emit(pendingDeepLinkToast!!)
                }
            }
        }
    }

    private val debtId: Int
        get() = _resolvedDebtId.value ?: throw IllegalStateException("debtId not yet resolved")

    val payments: StateFlow<List<PaymentEntity>> = debt.flatMapLatest { d ->
        if (d == null) flowOf(emptyList()) else paymentRepository.getPaymentsForDebt(d.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiMessage = MutableStateFlow("")
    val isGeneratingAi = MutableStateFlow(false)
    val roastLevel: StateFlow<String> = prefs.roastLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MEDIUM")

    val showAddPaymentSheet = MutableStateFlow(false)
    val showEditDebtSheet = MutableStateFlow(false)
    private val _showConfetti = MutableStateFlow(false)
    val showConfetti: StateFlow<Boolean> = _showConfetti.asStateFlow()
    private val _isExportingImage = MutableStateFlow(false)
    val isExportingImage: StateFlow<Boolean> = _isExportingImage.asStateFlow()
    private val _exportElapsed = MutableStateFlow(0L)
    val exportElapsed: StateFlow<Long> = _exportElapsed.asStateFlow()

    private val _showAuthPrompt = MutableStateFlow(false)
    val showAuthPrompt: StateFlow<Boolean> = _showAuthPrompt.asStateFlow()

    fun dismissAuthPrompt() {
        _showAuthPrompt.value = false
    }

    fun addPayment(amount: Double, note: String) = viewModelScope.launch {
        // Local-first: always record payment locally; sync only if signed in.
        // (Previously gated on prefs.isGoogleSignedIn which blocked offline /
        // email-password / "Skip for now" users — see audit 2026-07-03.)
        paymentRepository.recordPayment(debtId, amount, note)
        val after = debtRepository.getDebtById(debtId)
        if (after?.status == "SETTLED") {
            _showConfetti.value = true
        }
        showAddPaymentSheet.value = false
        syncIfSignedIn()
    }

    fun deletePayment(paymentId: Int) = viewModelScope.launch {
        paymentRepository.deletePayment(paymentId)
        syncIfSignedIn()
    }

    private val _showRewardAd = MutableStateFlow(false)
    val showRewardAd: StateFlow<Boolean> = _showRewardAd.asStateFlow()

    private val _remainingFree = MutableStateFlow(5)
    val remainingFree: StateFlow<Int> = _remainingFree.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            _remainingFree.value = aiRepository.remainingFreeRegenerations()
        }
    }

    fun preloadRewardedAd(context: Context) {
        adManager.loadRewardedAd(context)
    }

    fun generateRoast(activity: android.app.Activity? = null) = viewModelScope.launch {
        // P1-4 (2026-07-03): gate the button BEFORE any early-return path.
        // Previously `isGeneratingAi` only flipped to true inside
        // `generateRoastInternal`, which meant the offline-cached branch,
        // the show-reward-ad branch, and the no-debt branch all returned
        // without ever engaging the spinner — letting the user spam-tap
        // the button and queue duplicate rewarded-ad callbacks.
        if (isGeneratingAi.value) return@launch
        isGeneratingAi.value = true

        val d = debt.value
        if (d == null) {
            isGeneratingAi.value = false
            _toastEvent.emit(LocalizedString.get("debt_still_loading_toast"))
            return@launch
        }

        if (!aiRepository.canRegenerate()) {
            // If offline, skip ad — show cached message instead
            val cached = d.aiRoastGenerated
            val connectivityManager = activity?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val isOffline = connectivityManager?.activeNetwork == null

            if (isOffline) {
                isGeneratingAi.value = false
                if (!cached.isNullOrBlank()) {
                    aiMessage.value = cached
                } else {
                    _toastEvent.emit("No internet connection")
                }
                return@launch
            }

            if (activity != null) {
                // Hand off to the ad path. The ad callbacks resume
                // `generateRoastInternal` which manages its own
                // `isGeneratingAi` lifecycle; release our entry-point
                // hold so the ad-flow can re-engage it cleanly.
                isGeneratingAi.value = false
                adManager.showRewardedAd(activity, onRewarded = {
                    viewModelScope.launch {
                        aiRepository.resetRegenerationCount()
                        _remainingFree.value = aiRepository.remainingFreeRegenerations()
                        generateRoastInternal(d)
                    }
                }, onFailed = {
                    adManager.loadRewardedAd(activity)
                    viewModelScope.launch {
                        _toastEvent.emit("Ad loading, please try again in a moment")
                    }
                    _showRewardAd.value = false
                })
            } else {
                // Stays true until user taps dismiss / dialog closes — the
                // reward dialog is itself the loading state, so we keep
                // the spinner on behind it.
                _showRewardAd.value = true
            }
            return@launch
        }

        generateRoastInternal(d)
    }

    fun dismissRewardAd() {
        _showRewardAd.value = false
    }

    fun dismissConfetti() {
        _showConfetti.value = false
    }

    private suspend fun generateRoastInternal(d: DebtEntity) {
        isGeneratingAi.value = true
        val result = aiRepository.generateRoast(d, roastLevel.value)
        result.onSuccess { message ->
            aiRepository.incrementRegenerationCount()
            _remainingFree.value = aiRepository.remainingFreeRegenerations()
            aiMessage.value = message
            debtRepository.updateRoast(d.id, message, System.currentTimeMillis())
        }.onFailure { error ->
            val cached = d.aiRoastGenerated
            val isNetworkError = error is java.net.UnknownHostException ||
                    error is java.net.ConnectException ||
                    error is java.net.SocketTimeoutException ||
                    error.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                    error.message?.contains("Network is unreachable", ignoreCase = true) == true ||
                    error.message?.contains("timeout", ignoreCase = true) == true

            if (isNetworkError) {
                if (!cached.isNullOrBlank()) {
                    aiMessage.value = cached
                } else {
                    aiMessage.value = LocalizedString.get("no_internet_connection_toast")
                }
            } else {
                // Self-diagnostic on-device message — the user may have NO PC /
                // adb logcat access, so the actual Gemini response body has to
                // be surfaced HERE. For Retrofit HttpExceptions, pull errorBody()
                // so the user sees Gemini's own reason (e.g.
                // "INVALID_ARGUMENT: ...", "FAILED_PRECONDITION: ...", or
                // "PERMISSION_DENIED") instead of the bare "HTTP 400 " line.
                val httpDetail = (error as? HttpException)?.let { ex ->
                    val body = runCatching { ex.response()?.errorBody()?.string() }
                        .getOrNull()?.take(280)
                    if (body != null) "[HTTP ${ex.code()}] $body"
                    else "[HTTP ${ex.code()}] ${ex.message ?: "(no body)"}"
                }
                val detail = httpDetail ?: error.message ?: "Unknown error (try again in a moment)"
                aiMessage.value = LocalizedString.get("could_not_generate_roast") + ". " + detail
            }
        }
        isGeneratingAi.value = false
        syncIfSignedIn()
    }

    fun setRoastLevel(level: String) = viewModelScope.launch {
        prefs.setRoastLevel(level)
    }

    fun markSettled() = viewModelScope.launch {
        // Local-first — see addPayment for the why.
        debt.value?.let { d ->
            val remaining = d.amount - d.amountPaid
            if (remaining > 0) {
                paymentRepository.recordPayment(d.id, remaining, "Full settlement")
                _showConfetti.value = true
            }
        }
        syncIfSignedIn()
    }

    fun updateDebt(name: String, amount: Double, description: String, emoji: String) = viewModelScope.launch {
        debt.value?.let { d ->
            debtRepository.updateDebt(d.copy(
                personName = name,
                amount = amount,
                description = description,
                personEmoji = emoji
            ))
            showEditDebtSheet.value = false
        }
        syncIfSignedIn()
    }

    fun deleteDebt() = viewModelScope.launch {
        // Local-first — see addPayment for the why.
        val d = debt.value ?: return@launch
        debtRepository.deleteDebt(d)
        syncIfSignedIn()
        authManager.getCurrentUser()?.uid?.let { uid ->
            runCatching { syncManager.deleteDebtFromCloud(uid, d) }
        }
    }

    fun shareCard(context: Context, debt: DebtEntity, message: String) = viewModelScope.launch(Dispatchers.IO) {
        _isExportingImage.value = true
        _exportElapsed.value = 0L
        val startTime = System.currentTimeMillis()

        val elapsedJob = viewModelScope.launch {
            while (coroutineContext[Job]?.isActive == true) {
                delay(100)
                _exportElapsed.value = System.currentTimeMillis() - startTime
            }
        }

        val actualMessage = message.ifBlank {
            val defaultQuote = when (debt.type) {
                "THEY_OWE_ME" -> "Hey! Still waiting for that money 😅"
                else -> "Don't worry, I'll pay you soon 🤞"
            }
            debt.aiRoastGenerated ?: defaultQuote
        }
        try {
            val userName = prefs.userName.first().ifBlank { "Your Friend" }
            val showDesc = prefs.showDescription.first()
            val showDate = prefs.showDueDate.first()
            val showEmojiPref = prefs.showEmoji.first()
            val bitmap = try {
                HtmlExporter.generateShareableImage(context, debt, userName, actualMessage, showDesc, showDate, showEmojiPref)
            } catch (e: Exception) {
                android.util.Log.e("DebtDetailVM", "HTML export failed, falling back to Canvas: ${e.message}")
                CanvasExporter.createDebtCard(context, debt, actualMessage, roastLevel.value, showDesc, showDate, showEmojiPref)
            }
            HtmlExporter.shareImage(context, bitmap)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, LocalizedString.get("new_design_generated_toast"), android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("DebtDetailVM", "shareCard failed: ${e.message}", e)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, LocalizedString.get("failed_to_create_image") + ": " + e.message, android.widget.Toast.LENGTH_LONG).show()
            }
        } finally {
            elapsedJob.cancel()
            _isExportingImage.value = false
        }
    }

    fun shareDebtHistoryToWhatsApp(context: Context, debt: DebtEntity, paymentList: List<PaymentEntity>) = viewModelScope.launch(Dispatchers.IO) {
        val remaining = debt.amount - debt.amountPaid
        val dueDate = debt.dueDate?.let { 
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) 
        } ?: LocalizedString.get("no_due_date")

        val totalPaid = paymentList.sumOf { it.amount }
        val historyLines = paymentList.mapIndexed { i, p ->
            "${i + 1}. ${com.dhanuk.debtbro.util.formatCurrency(p.amount, debt.currency)} on ${p.paidAt.toReadableDate()}${if (!p.note.isNullOrBlank()) " - ${p.note}" else ""}"
        }.joinToString("\n")

        val totalStr = com.dhanuk.debtbro.util.formatCurrency(debt.amount, debt.currency)
        val paidStr = com.dhanuk.debtbro.util.formatCurrency(totalPaid, debt.currency)
        val remainingStr = com.dhanuk.debtbro.util.formatCurrency(remaining, debt.currency)

        val textMessage = when (debt.type) {
            "THEY_OWE_ME" -> buildString {
                append("💰 *DebtBro - Payment Reminder*\n\n")
                append("*To:* ${debt.personName}\n")
                append("*Total:* $totalStr\n")
                append("*Paid:* $paidStr\n")
                append("*Remaining:* $remainingStr\n")
                append("*Due Date:* $dueDate\n")
                if (debt.description.isNotBlank()) append("*Reason:* ${debt.description}\n")
                append("\n")
                if (paymentList.isNotEmpty()) {
                    append("*Payment History:*\n$historyLines\n\n")
                }
                append("— Sent via DebtBro")
            }
            else -> buildString {
                append("🙏 *DebtBro - I Owe You*\n\n")
                append("*From:* ${debt.personName}\n")
                append("*Total:* $totalStr\n")
                append("*Paid:* $paidStr\n")
                append("*Remaining:* $remainingStr\n")
                append("*Due Date:* $dueDate\n")
                if (debt.description.isNotBlank()) append("*Reason:* ${debt.description}\n")
                append("\n")
                if (paymentList.isNotEmpty()) {
                    append("*Payment History:*\n$historyLines\n\n")
                }
                append("— Sent via DebtBro")
            }
        }

        withContext(Dispatchers.Main) {
            shareTextToWhatsApp(context, textMessage)
        }
    }

    fun shareQuoteToWhatsApp(context: Context, quote: String) = viewModelScope.launch(Dispatchers.Main) {
        shareTextToWhatsApp(context, quote)
    }

    fun shareCardToWhatsApp(context: Context, debt: DebtEntity, message: String) = viewModelScope.launch(Dispatchers.IO) {
        _isExportingImage.value = true
        _exportElapsed.value = 0L
        val startTime = System.currentTimeMillis()

        val elapsedJob = viewModelScope.launch {
            while (coroutineContext[Job]?.isActive == true) {
                delay(100)
                _exportElapsed.value = System.currentTimeMillis() - startTime
            }
        }

        val actualMessage = message.ifBlank {
            val defaultQuote = when (debt.type) {
                "THEY_OWE_ME" -> "Hey! Still waiting for that money 😅"
                else -> "Don't worry, I'll pay you soon 🤞"
            }
            debt.aiRoastGenerated ?: defaultQuote
        }
        try {
            val userName = prefs.userName.first().ifBlank { "Your Friend" }
            val showDesc = prefs.showDescription.first()
            val showDate = prefs.showDueDate.first()
            val showEmojiPref = prefs.showEmoji.first()
            val (bitmap, uri) = try {
                val bmp = HtmlExporter.generateShareableImage(context, debt, userName, actualMessage, showDesc, showDate, showEmojiPref)
                Pair(bmp, HtmlExporter.getShareableUri(context, bmp))
            } catch (e: Exception) {
                android.util.Log.e("DebtDetailVM", "HTML export failed, falling back to Canvas: ${e.message}")
                val bmp = CanvasExporter.createDebtCard(context, debt, actualMessage, roastLevel.value, showDesc, showDate, showEmojiPref)
                Pair(bmp, CanvasExporter.saveDebtCard(context, bmp))
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_TEXT, actualMessage)
                    setPackage("com.whatsapp")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, LocalizedString.get("new_design_generated_toast"), android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("DebtDetailVM", "shareCard failed: ${e.message}", e)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                com.dhanuk.debtbro.util.shareTextToWhatsApp(context, actualMessage)
            }
        } finally {
            elapsedJob.cancel()
            _isExportingImage.value = false
        }
    }

    fun reportAiMessage() = viewModelScope.launch {
        _toastEvent.emit("Message reported. Thank you for your feedback.")
    }

    private fun syncIfSignedIn() = viewModelScope.launch {
        authManager.getCurrentUser()?.uid?.let { uid ->
            runCatching { syncManager.mergePendingUnsynced(uid) }
        }
    }

    // Preload cached AI message on init
    init {
        viewModelScope.launch {
            debt.first()?.let { d ->
                if (!d.aiRoastGenerated.isNullOrBlank()) {
                    aiMessage.value = d.aiRoastGenerated
                }
            }
        }
    }
}
