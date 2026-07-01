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
import com.dhanuk.debtbro.data.repository.GroqRepository
import com.dhanuk.debtbro.data.repository.PaymentRepository
import com.dhanuk.debtbro.util.CanvasExporter
import com.dhanuk.debtbro.util.HtmlExporter
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
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debtRepository: DebtRepository,
    private val paymentRepository: PaymentRepository,
    private val groqRepository: GroqRepository,
    private val prefs: AppPreferences,
    private val authManager: AuthManager,
    private val syncManager: SyncManager,
    private val adManager: AdManager
) : ViewModel() {

    private val debtId: Int = savedStateHandle.get<Int>("debtId")
        ?: savedStateHandle.get<String>("debtId")?.toIntOrNull()
        ?: error("debtId is required for DebtDetailViewModel")

    val debt: StateFlow<DebtEntity?> = debtRepository.observeDebtById(debtId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
        if (!prefs.isGoogleSignedIn.first()) {
            _showAuthPrompt.value = true
            return@launch
        }
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
            _remainingFree.value = groqRepository.remainingFreeRegenerations()
        }
    }

    fun preloadRewardedAd(context: Context) {
        adManager.loadRewardedAd(context)
    }

    fun generateRoast(activity: android.app.Activity? = null) = viewModelScope.launch {
        val d = debt.value ?: return@launch

        if (!groqRepository.canRegenerate()) {
            // If offline, skip ad — show cached message instead
            val cached = d.aiRoastGenerated
            val connectivityManager = activity?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val isOffline = connectivityManager?.activeNetwork == null

            if (isOffline) {
                if (!cached.isNullOrBlank()) {
                    aiMessage.value = cached
                } else {
                    _toastEvent.emit("No internet connection")
                }
                return@launch
            }

            if (activity != null) {
                adManager.showRewardedAd(activity, onRewarded = {
                    viewModelScope.launch {
                        groqRepository.resetRegenerationCount()
                        _remainingFree.value = groqRepository.remainingFreeRegenerations()
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
        val result = groqRepository.generateRoast(d, roastLevel.value)
        result.onSuccess { message ->
            groqRepository.incrementRegenerationCount()
            _remainingFree.value = groqRepository.remainingFreeRegenerations()
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
                    aiMessage.value = "No internet connection. Tap refresh when online."
                }
            } else {
                aiMessage.value = "Could not generate roast. ${error.message ?: "Try again."}"
            }
        }
        isGeneratingAi.value = false
        syncIfSignedIn()
    }

    fun setRoastLevel(level: String) = viewModelScope.launch {
        prefs.setRoastLevel(level)
    }

    fun markSettled() = viewModelScope.launch {
        if (!prefs.isGoogleSignedIn.first()) {
            _showAuthPrompt.value = true
            return@launch
        }
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
        if (!prefs.isGoogleSignedIn.first()) {
            _showAuthPrompt.value = true
            return@launch
        }
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
                android.widget.Toast.makeText(context, "New design generated!", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("DebtDetailVM", "shareCard failed: ${e.message}", e)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Failed to create image: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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
        } ?: "No due date"

        val totalPaid = paymentList.sumOf { it.amount }
        val historyLines = paymentList.mapIndexed { i, p ->
            "${i + 1}. ${debt.currency}${p.amount.toLong()} on ${p.paidAt.toReadableDate()}${if (!p.note.isNullOrBlank()) " - ${p.note}" else ""}"
        }.joinToString("\n")

        val textMessage = when (debt.type) {
            "THEY_OWE_ME" -> buildString {
                append("💰 *DebtBro - Payment Reminder*\n\n")
                append("*To:* ${debt.personName}\n")
                append("*Total:* ${debt.currency}${debt.amount.toLong()}\n")
                append("*Paid:* ${debt.currency}${totalPaid.toLong()}\n")
                append("*Remaining:* ${debt.currency}${remaining.toLong()}\n")
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
                append("*Total:* ${debt.currency}${debt.amount.toLong()}\n")
                append("*Paid:* ${debt.currency}${totalPaid.toLong()}\n")
                append("*Remaining:* ${debt.currency}${remaining.toLong()}\n")
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
                android.widget.Toast.makeText(context, "New design generated!", android.widget.Toast.LENGTH_SHORT).show()
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
