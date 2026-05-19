package com.dhanuk.debtbro.data.repository

import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.network.GroqApiService
import com.dhanuk.debtbro.data.network.GroqMessage
import com.dhanuk.debtbro.data.network.GroqRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqRepository @Inject constructor(private val api: GroqApiService, private val prefs: AppPreferences) {
    companion object {
        const val MAX_FREE_REGENERATIONS = 5
        private const val API_COOLDOWN_MS = 1000L
    }

    private val rateLimitMutex = Mutex()
    private var lastApiCallTime = 0L

    private suspend fun ensureRateLimit() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastApiCallTime
            if (elapsed < API_COOLDOWN_MS) {
                kotlinx.coroutines.delay(API_COOLDOWN_MS - elapsed)
            }
            lastApiCallTime = System.currentTimeMillis()
        }
    }

    suspend fun canRegenerate(): Boolean = getRegenerationCount() < MAX_FREE_REGENERATIONS
    suspend fun resetRegenerationCount() { prefs.saveAiRegenerationCount(0) }
    suspend fun incrementRegenerationCount() {
        val current = getRegenerationCount()
        prefs.saveAiRegenerationCount(current + 1)
    }
    suspend fun remainingFreeRegenerations(): Int = (MAX_FREE_REGENERATIONS - getRegenerationCount()).coerceAtLeast(0)

    private suspend fun getRegenerationCount(): Int = prefs.getAiRegenerationCount()

    private suspend fun apiKey(): String = prefs.groqApiKey.first().ifEmpty { BuildConfig.GROQ_API_KEY }
    private fun systemPrompt(roastLevel: String, selectedLangCode: String, debtType: String?): String {
        val langInstruction = when(selectedLangCode) {
            "hi" -> "Respond ONLY in Hindi (Devanagari script). Use Hinglish if needed."
            "es" -> "Respond ONLY in Spanish."
            "fr" -> "Respond ONLY in French."
            "ar" -> "Respond ONLY in Arabic."
            "bn" -> "Respond ONLY in Bengali."
            "ta" -> "Respond ONLY in Tamil."
            "te" -> "Respond ONLY in Telugu."
            "pt" -> "Respond ONLY in Portuguese."
            "de" -> "Respond ONLY in German."
            "zh" -> "Respond ONLY in Mandarin Chinese."
            "ja" -> "Respond ONLY in Japanese."
            "ko" -> "Respond ONLY in Korean."
            "ru" -> "Respond ONLY in Russian."
            "tr" -> "Respond ONLY in Turkish."
            "id" -> "Respond ONLY in Bahasa Indonesia."
            "mr" -> "Respond ONLY in Marathi."
            "gu" -> "Respond ONLY in Gujarati."
            "pa" -> "Respond ONLY in Punjabi."
            "ur" -> "Respond ONLY in Urdu."
            else -> "Respond in English."
        }
        val debtDirection = if (debtType == "I_OWE_THEM") {
            "\nDIRECTION: This person OWES money to the user. Write an APOLOGETIC message from the borrower's perspective — promise to pay soon, show gratitude, feel slightly embarrassed but warm."
        } else {
            "\nDIRECTION: This person OWES money to the user. Write a reminder message FROM the lender's perspective — remind them to pay, be creative and funny but not aggressive."
        }
        val prompt = when (roastLevel) {
            "MILD" -> """You are a witty Indian friend writing a WhatsApp message about money.$debtDirection
Key rules:
- Write 1-2 lines (100-140 characters total, not too short not too long)
- Use Hinglish naturally (mix Hindi and English)
- Be warm, funny, and creative — use metaphors or shared jokes
- Start conversationally, end with a lighthearted nudge
- NO aggressive language or insults
- 1-2 emojis max
- Do NOT use hashtags or formal language"""
            "SAVAGE" -> """You are a brutally funny Indian debt collector with legendary comedic timing.$debtDirection
Key rules:
- Write 1-2 lines (100-140 characters total, not too short not too long)
- Use Hinglish naturally
- Be creatively savage — use wild metaphors or Bollywood comparisons
- Must be hilarious, NOT offensive or abusive
- Punch UP — laugh at the situation, not the person
- 2-3 emojis for maximum impact
- Think: "funniest WhatsApp forward ever" energy"""
            else -> """You are a clever, sarcastic Indian friend dropping a subtle money hint.$debtDirection
Key rules:
- Write 1-2 lines (100-140 characters total, not too short not too long)
- Use Hinglish naturally
- Be passive-aggressive but funny — think ironic compliments
- Use relatable Indian scenarios (chai, zomato, petrol prices)
- No direct begging or rudeness
- 1-2 emojis
- Memorable enough to screenshot and share"""
        }
        return "$langInstruction\n$prompt"
    }
    suspend fun generateRoast(debt: DebtEntity, roastLevel: String): Result<String> = runCatching {
        ensureRateLimit()
        val key = apiKey()
        if (key.isEmpty()) return Result.failure(Exception("NO_API_KEY"))
        val now = System.currentTimeMillis()
        val daysOverdue = if (debt.dueDate != null && debt.dueDate < now) ((now - debt.dueDate) / 86400000).toInt() else 0
        val currency = prefs.defaultCurrency.first()
        val amountStr = "${currency}${debt.amount - debt.amountPaid}"
        val personContext = when {
            debt.description.isNotBlank() -> debt.description
            else -> "payment"
        }
        val userMessage = """Debt details:
- Person: ${debt.personName}
- Amount: ${debt.currency}${debt.amount} (remaining: $amountStr)
- What it's for: $personContext
- Due date: ${debt.dueDate?.let { SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(it)) } ?: "No due date"}
- Days overdue: $daysOverdue
- Type: ${if (debt.type == "I_OWE_THEM") "${debt.personName} lent you money, you owe them" else "You lent ${debt.personName} money, they owe you"}

Generate a WhatsApp-style payment reminder. The message MUST reference the actual debt context above — mention what the money was for, the amount, and the person. Be personal. Do NOT make up random scenarios. Keep it 1-2 lines, 100-140 characters."""

        val response = api.chat(
            auth = "Bearer $key",
            request = GroqRequest(
                model = "llama-3.3-70b-versatile",
                messages = listOf(
                    GroqMessage("system", systemPrompt(roastLevel, prefs.selectedLanguage.first(), debt.type)),
                    GroqMessage("user", userMessage)
                ),
                temperature = 0.85,
                max_tokens = 100
            )
        )
        response.choices.first().message.content.trim()
    }
    suspend fun analyzeDebts(totalLent: Double, totalOwed: Double, recoveryRate: Int, worstDebtor: String): Result<String> = runCatching {
        ensureRateLimit()
        val key = apiKey()
        if (key.isEmpty()) return Result.failure(Exception("NO_API_KEY"))
        val langCode = prefs.selectedLanguage.first()
        val currency = prefs.defaultCurrency.first()
        val langInstruction = systemPrompt("MILD", langCode, null).substringBefore("\n")
        val response = api.chat("Bearer $key", GroqRequest(messages = listOf(
            GroqMessage("system", "$langInstruction\nYou are a funny personal finance analyst. Give ONE sharp 2-line insight. No disclaimers."),
            GroqMessage("user", "Total lent: ${currency}$totalLent to friends. Total I owe: ${currency}$totalOwed. Recovery rate: $recoveryRate%. Worst debtor: $worstDebtor. Give ONE sharp, funny, honest 2-line insight. Hinglish welcome.")
        ), model = "llama-3.3-70b-versatile", temperature = 0.3, max_tokens = 150))
        response.choices.first().message.content.trim()
    }
    suspend fun generateSplitSummary(title: String, total: Double, perPerson: Double, count: Int): Result<String> = runCatching {
        ensureRateLimit()
        val key = apiKey()
        if (key.isEmpty()) return Result.failure(Exception("NO_API_KEY"))
        val langCode = prefs.selectedLanguage.first()
        val currency = prefs.defaultCurrency.first()
        val langInstruction = systemPrompt("MILD", langCode, null).substringBefore("\n")
        val response = api.chat("Bearer $key", GroqRequest(messages = listOf(
            GroqMessage("system", "$langInstruction\nYou are a funny commentator. One line only. Be creative."),
            GroqMessage("user", "Split: $title, Total: ${currency}$total, $count people, ${currency}$perPerson each. Write ONE funny line about this. Hinglish ok.")
        ), model = "llama-3.3-70b-versatile", temperature = 1.0, max_tokens = 100))
        response.choices.first().message.content.trim()
    }
    suspend fun testConnection(): Boolean {
        try {
            ensureRateLimit()
            val key = apiKey()
            if (key.isEmpty()) return false
            return api.chat("Bearer $key", GroqRequest(messages = listOf(GroqMessage("user", "Say OK")), max_tokens = 10)).choices.isNotEmpty()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
    }
}
