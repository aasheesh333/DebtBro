package com.dhanuk.debtbro.data.repository

import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.network.GeminiApiService
import com.dhanuk.debtbro.data.network.GeminiContent
import com.dhanuk.debtbro.data.network.GeminiRequest
import com.dhanuk.debtbro.data.network.GeminiPart
import com.dhanuk.debtbro.data.network.GenerationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val geminiApi: GeminiApiService,
    private val prefs: AppPreferences
) {
    companion object {
        const val MAX_FREE_REGENERATIONS = 5
        private const val API_COOLDOWN_MS = 1000L
        private const val RPM_LIMIT = 30
        private const val RPM_WINDOW_MS = 60_000L
        private val BLOCKED_WORDS = setOf(
            "fuck", "fucking", "shit", "shithead", "bitch", "bastard", "asshole",
            "dick", "dickhead", "cunt", "whore", "slut", "nigger", "nigga",
            "chutiya", "chut", "laude", "lodu", "bhosdi", "bhosda", "bhenchod",
            "madarchod", "maaderchod", "behenchod", "behnchod", "randi", "gandu",
            "gand", "lund", "lawda", "lawde", "jhat", "jhantu", "tatte",
            "bsdk", "bhosdike", "suar", "harami", "kamina", "kutte"
        )
    }

    private fun filterProfanity(text: String): String {
        var filtered = text
        for (word in BLOCKED_WORDS) {
            val regex = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
            filtered = filtered.replace(regex) { match ->
                match.value.first() + "*".repeat(match.value.length - 1)
            }
        }
        return filtered
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

    private suspend fun apiKey(): String = prefs.geminiApiKey.first().ifEmpty { BuildConfig.GEMINI_API_KEY_2_5_FLASH_LITE }

    private fun buildSystemPrompt(roastLevel: String, selectedLangCode: String, debtType: String?): String {
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
- Write 2-3 lines (140-180 characters total, push for longer not shorter)
- Use Hinglish naturally (mix Hindi and English)
- Be warm, funny, and creative — use metaphors or shared jokes
- Start conversationally, end with a lighthearted nudge
- NO aggressive language or insults
- 1-2 emojis max
- Do NOT use hashtags or formal language
- Do NOT wrap your response in quotation marks
- Every response must be COMPLETELY DIFFERENT from previous ones — vary phrases, metaphors, and tone"""
            "SPICY" -> """You are a brutally funny Indian debt collector with legendary comedic timing.$debtDirection
Key rules:
- Write 2-3 lines (140-180 characters total, push for longer not shorter)
- Use Hinglish naturally
- Be creatively savage — use wild metaphors or Bollywood comparisons
- Must be hilarious, NOT offensive or abusive
- Punch UP — laugh at the situation, not the person
- 2-3 emojis for maximum impact
- Think: "funniest WhatsApp forward ever" energy
- Do NOT wrap your response in quotation marks
- Every response must be COMPLETELY DIFFERENT from previous ones — vary phrases, metaphors, and tone"""
            else -> """You are a clever, sarcastic Indian friend dropping a subtle money hint.$debtDirection
Key rules:
- Write 2-3 lines (140-180 characters total, push for longer not shorter)
- Use Hinglish naturally
- Be passive-aggressive but funny — think ironic compliments
- Use relatable Indian scenarios (chai, zomato, petrol prices)
- No direct begging or rudeness
- 1-2 emojis
- Memorable enough to screenshot and share
- Do NOT wrap your response in quotation marks
- Every response must be COMPLETELY DIFFERENT from previous ones — vary phrases, metaphors, and tone"""
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

Generate a WhatsApp-style payment reminder. The message MUST reference the actual debt context above — mention what the money was for, the amount, and the person. Be personal. Do NOT make up random scenarios. Keep it 2-3 lines, 140-180 characters. Push for longer, more detailed responses. Do NOT use quotation marks. Make it creative and different each time — never repeat the same phrasing."""

        val temp = when (roastLevel) {
            "SPICY" -> 0.7
            "MEDIUM" -> 0.65
            else -> 0.5
        }

        val langCode = prefs.selectedLanguage.first()
        val systemText = buildSystemPrompt(roastLevel, langCode, debt.type)

        val response = geminiApi.generateContent(
            apiKey = key,
            request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(userMessage)), role = "user")),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(systemText)), role = "system"),
                generationConfig = GenerationConfig(temperature = temp, maxOutputTokens = 200)
            )
        )

        val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("AI response is empty, try again")

        filterProfanity(text.trim()
            .removeSurrounding("\"")
            .removeSurrounding("\u201C", "\u201D")
            .removeSurrounding("\u2018", "\u2019")
            .trim()
        )
    }

    suspend fun analyzeDebts(totalLent: Double, totalOwed: Double, recoveryRate: Int, worstDebtor: String): Result<String> = runCatching {
        ensureRateLimit()
        val key = apiKey()
        if (key.isEmpty()) return Result.failure(Exception("NO_API_KEY"))
        val langCode = prefs.selectedLanguage.first()
        val currency = prefs.defaultCurrency.first()
        val langInstruction = buildSystemPrompt("MILD", langCode, null).substringBefore("\n")

        val prompt = "Total lent: ${currency}$totalLent to friends. Total I owe: ${currency}$totalOwed. Recovery rate: $recoveryRate%. Worst debtor: $worstDebtor. Give ONE sharp, funny, honest 2-line insight. Hinglish welcome."

        val response = geminiApi.generateContent(
            apiKey = key,
            request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)), role = "user")),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart("$langInstruction\nYou are a funny personal finance analyst. Give ONE sharp 2-line insight. No disclaimers.")), role = "system"),
                generationConfig = GenerationConfig(temperature = 0.3, maxOutputTokens = 150)
            )
        )

        response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: return Result.failure(Exception("Empty AI response"))
    }

    suspend fun generateSplitSummary(title: String, total: Double, perPerson: Double, count: Int): Result<String> = runCatching {
        ensureRateLimit()
        val key = apiKey()
        if (key.isEmpty()) return Result.failure(Exception("NO_API_KEY"))
        val langCode = prefs.selectedLanguage.first()
        val currency = prefs.defaultCurrency.first()
        val langInstruction = buildSystemPrompt("MILD", langCode, null).substringBefore("\n")

        val prompt = "Split: $title, Total: ${currency}$total, $count people, ${currency}$perPerson each. Write ONE funny line about this. Hinglish ok."

        val response = geminiApi.generateContent(
            apiKey = key,
            request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)), role = "user")),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart("$langInstruction\nYou are a funny commentator. One line only. Be creative.")), role = "system"),
                generationConfig = GenerationConfig(temperature = 0.7, maxOutputTokens = 100)
            )
        )

        response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: return Result.failure(Exception("Empty AI response"))
    }

    suspend fun testConnection(): Boolean {
        return try {
            ensureRateLimit()
            val key = apiKey()
            if (key.isEmpty()) return false
            return geminiApi.generateContent(
                apiKey = key,
                request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart("Say OK")), role = "user"))
                )
            ).candidates.isNotEmpty()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
    }
}
