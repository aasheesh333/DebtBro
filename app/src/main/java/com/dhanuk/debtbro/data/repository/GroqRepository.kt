package com.dhanuk.debtbro.data.repository

import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.network.GroqApiService
import com.dhanuk.debtbro.data.network.GroqMessage
import com.dhanuk.debtbro.data.network.GroqRequest
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqRepository @Inject constructor(private val api: GroqApiService, private val prefs: AppPreferences) {
    companion object {
        const val MAX_FREE_REGENERATIONS = 5
    }

    private var regenerationCount = 0

    fun canRegenerate(): Boolean = regenerationCount < MAX_FREE_REGENERATIONS
    fun resetRegenerationCount() { regenerationCount = 0 }
    fun incrementRegenerationCount() { regenerationCount++ }
    fun remainingFreeRegenerations(): Int = (MAX_FREE_REGENERATIONS - regenerationCount).coerceAtLeast(0)

    private suspend fun apiKey(): String = prefs.groqApiKey.first().ifEmpty { BuildConfig.GROQ_API_KEY }
    private fun systemPrompt(roastLevel: String, selectedLangCode: String): String {
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
        val prompt = when (roastLevel) {
            "MILD" -> """You are a witty Indian friend writing a WhatsApp message to remind someone about money they owe.
Key rules:
- Write EXACTLY 2-3 short lines that feel like a real WhatsApp text from a friend
- Use Hinglish naturally (mix Hindi and English) — it should feel authentic
- Be warm, funny, and creative — use metaphors, Bollywood references, or shared jokes
- Start conversationally (don't just state the debt bluntly)
- End with a lighthearted nudge
- NO aggressive language or insults
- 2-3 emojis max
- Do NOT use hashtags or formal language"""
            "SAVAGE" -> """You are a brutally funny Indian debt collector with legendary comedic timing.
Key rules:
- Write EXACTLY 2-3 short lines
- Use Hinglish naturally
- Be creatively savage — use wild metaphors, Bollywood drama comparisons, or over-the-top scenarios
- Must be hilarious, NOT offensive or abusive
- Punch UP — laugh at the situation, not the person
- Make it shareable (the friend should laugh and pay)
- 2-4 emojis for maximum impact
- Think: "funniest WhatsApp forward ever" energy"""
            else -> """You are a clever, sarcastic Indian friend dropping a subtle money hint.
Key rules:
- Write EXACTLY 2-3 short lines
- Use Hinglish naturally
- Be passive-aggressive but funny — think ironic compliments and witty remarks
- Use relatable Indian scenarios (chai, zomato, petrol prices, etc.)
- No direct begging or rudeness
- 1-3 emojis
- Memorable enough to screenshot and share"""
        }
        return "$langInstruction\n$prompt"
    }
    suspend fun generateRoast(debt: DebtEntity, roastLevel: String): Result<String> = runCatching {
        val key = apiKey()
        if (key.isEmpty()) return Result.failure(Exception("NO_API_KEY"))
        val now = System.currentTimeMillis()
        val daysOverdue = if (debt.dueDate != null && debt.dueDate < now) ((now - debt.dueDate) / 86400000).toInt() else 0
        val amountStr = "${debt.currency}${debt.amount - debt.amountPaid}"
        val personContext = when {
            debt.description.isNotBlank() -> "$debt.description (₹${debt.amount} total)"
            else -> "a debt of ₹${debt.amount}"
        }
        val response = api.chat(
            auth = "Bearer $key",
            request = GroqRequest(
                model = "llama-3.3-70b-versatile",
                messages = listOf(
                    GroqMessage("system", systemPrompt(roastLevel, prefs.selectedLanguage.first())),
                    GroqMessage("user", """Friend name: ${debt.personName}
Amount they owe: $amountStr
Context: $personContext
Days overdue: $daysOverdue
Their payment status: Paid ${debt.currency}${debt.amountPaid} out of ${debt.currency}${debt.amount}

Write a creative WhatsApp reminder message. Make it 3 lines max, personal, and funny.""")
                ),
                temperature = 0.85,
                max_tokens = 250
            )
        )
        response.choices.first().message.content.trim()
    }
    suspend fun analyzeDebts(totalLent: Double, totalOwed: Double, recoveryRate: Int, worstDebtor: String): Result<String> = runCatching {
        val key = apiKey()
        if (key.isEmpty()) return Result.failure(Exception("NO_API_KEY"))
        val langCode = prefs.selectedLanguage.first()
        val langInstruction = systemPrompt("MILD", langCode).substringBefore("\n")
        val response = api.chat("Bearer $key", GroqRequest(messages = listOf(
            GroqMessage("system", "$langInstruction\nYou are a funny personal finance analyst. Give ONE sharp 2-line insight. No disclaimers."),
            GroqMessage("user", "Total lent: ₹$totalLent to friends. Total I owe: ₹$totalOwed. Recovery rate: $recoveryRate%. Worst debtor: $worstDebtor. Give ONE sharp, funny, honest 2-line insight. Hinglish welcome.")
        ), model = "llama-3.3-70b-versatile", temperature = 0.3, max_tokens = 150))
        response.choices.first().message.content.trim()
    }
    suspend fun generateSplitSummary(title: String, total: Double, perPerson: Double, count: Int): Result<String> = runCatching {
        val key = apiKey()
        if (key.isEmpty()) return Result.failure(Exception("NO_API_KEY"))
        val langCode = prefs.selectedLanguage.first()
        val langInstruction = systemPrompt("MILD", langCode).substringBefore("\n")
        val response = api.chat("Bearer $key", GroqRequest(messages = listOf(
            GroqMessage("system", "$langInstruction\nYou are a funny commentator. One line only. Be creative."),
            GroqMessage("user", "Split: $title, Total: ₹$total, $count people, ₹$perPerson each. Write ONE funny line about this. Hinglish ok.")
        ), model = "llama-3.3-70b-versatile", temperature = 1.0, max_tokens = 100))
        response.choices.first().message.content.trim()
    }
    suspend fun testConnection(): Boolean = runCatching {
        val key = apiKey()
        if (key.isEmpty()) return@runCatching false
        api.chat("Bearer $key", GroqRequest(messages = listOf(GroqMessage("user", "Say OK")), max_tokens = 10)).choices.isNotEmpty()
    }.getOrDefault(false)
}
