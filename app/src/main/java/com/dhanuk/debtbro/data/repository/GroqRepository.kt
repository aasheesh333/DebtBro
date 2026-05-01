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
            "MILD" -> "You are a friendly financial reminder bot. Write a short, warm and funny WhatsApp message reminding someone to pay back a debt. 2 lines max. 1-2 emojis. Hinglish is fine."
            "SAVAGE" -> "You are a ruthless debt collector comedian. Write a brutally funny roast WhatsApp reminder. 3 lines max. No abuse. Pure comedy. Hinglish is great."
            else -> "You are a passive-aggressive money reminder bot. Write a clever, sarcastic but funny WhatsApp reminder. 2 lines max. Hinglish ok."
        }
        return "$langInstruction\n$prompt"
    }
    suspend fun generateRoast(debt: DebtEntity, roastLevel: String): Result<String> = runCatching {
        val key = apiKey()
        if (key.isEmpty()) error("NO_API_KEY")
        val now = System.currentTimeMillis()
        val daysOverdue = if (debt.dueDate != null && debt.dueDate < now) ((now - debt.dueDate) / 86400000).toInt() else 0
        val response = api.chat(
            auth = "Bearer $key",
            request = GroqRequest(messages = listOf(
                GroqMessage("system", systemPrompt(roastLevel, prefs.selectedLanguage.first())),
                GroqMessage("user", "Name: ${debt.personName}, Amount: ${debt.currency}${debt.amount - debt.amountPaid}, Reason: ${debt.description.ifEmpty { "a debt" }}, Days overdue: $daysOverdue. Make it personal and WhatsApp-forwardable.")
            ))
        )
        response.choices.first().message.content.trim()
    }
    suspend fun analyzeDebts(totalLent: Double, totalOwed: Double, recoveryRate: Int, worstDebtor: String): Result<String> = runCatching {
        val key = apiKey()
        if (key.isEmpty()) error("NO_API_KEY")
        val langCode = prefs.selectedLanguage.first()
        val langInstruction = systemPrompt("MILD", langCode).substringBefore("\n")
        val response = api.chat("Bearer $key", GroqRequest(messages = listOf(
            GroqMessage("system", "$langInstruction\nYou are a funny personal finance analyst. Give ONE sharp 2-line insight. No disclaimers."),
            GroqMessage("user", "Total lent: ₹$totalLent to friends. Total I owe: ₹$totalOwed. Recovery rate: $recoveryRate%. Worst debtor: $worstDebtor. Give ONE sharp, funny, honest 2-line insight. Hinglish welcome.")
        ), temperature = 0.3, max_tokens = 150))
        response.choices.first().message.content.trim()
    }
    suspend fun generateSplitSummary(title: String, total: Double, perPerson: Double, count: Int): Result<String> = runCatching {
        val key = apiKey()
        if (key.isEmpty()) error("NO_API_KEY")
        val langCode = prefs.selectedLanguage.first()
        val langInstruction = systemPrompt("MILD", langCode).substringBefore("\n")
        val response = api.chat("Bearer $key", GroqRequest(messages = listOf(
            GroqMessage("system", "$langInstruction\nYou are a funny commentator. One line only. Be creative."),
            GroqMessage("user", "Split: $title, Total: ₹$total, $count people, ₹$perPerson each. Write ONE funny line about this. Hinglish ok.")
        ), temperature = 1.0, max_tokens = 100))
        response.choices.first().message.content.trim()
    }
    suspend fun testConnection(): Boolean = runCatching {
        val key = apiKey()
        if (key.isEmpty()) return@runCatching false
        api.chat("Bearer $key", GroqRequest(messages = listOf(GroqMessage("user", "Say OK")), max_tokens = 10)).choices.isNotEmpty()
    }.getOrDefault(false)
}
