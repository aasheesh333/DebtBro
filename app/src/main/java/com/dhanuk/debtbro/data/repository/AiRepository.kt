package com.dhanuk.debtbro.data.repository

import android.util.Log
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.network.GeminiApiService
import com.dhanuk.debtbro.data.network.GeminiContent
import com.dhanuk.debtbro.data.network.GeminiRequest
import com.dhanuk.debtbro.data.network.GeminiPart
import com.dhanuk.debtbro.data.network.GenerationConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Distinct exception type so callers (e.g. AnalyticsViewModel) can branch on
 * "the user hasn't supplied a key" vs "the API call genuinely failed".
 *
 * Before this distinction, every HTTP 400 (e.g. an invalid model name) was
 * being shown as "Add a Gemini API key in Settings…" — even when the user's
 * BuildConfig key WAS set but incompatible with the model name we tried.
 * Detecting the missing-key case separately lets the UI show a friendly
 * "Add a key" hint for one failure mode, and a generic "AI is busy" hint
 * for everything else.
 */
class NoApiKeyException(message: String = "NO_API_KEY") : Exception(message)

/**
 * Result of an in-app self-diagnose attempt (called via Settings → AI Setup →
 * "Test Connection"). Every field is intended for direct display in the UI:
 *
 *  - [keySource] / [keyPrefix]   → "Which key is being sent?" answer
 *  - [winningModel]              → First candidate that returned a non-empty 2xx
 *  - [failureCode] / [failureReason] → What Gemini actually rejected (and why)
 *  - [userFacingHint]            → 1-line actionable guidance for the user
 *
 * The 4-char [keyPrefix] is enough to distinguish an `AIza…` AI Studio key
 * from the newer `AQ.…` OAuth-style token, and never grows beyond that to
 * avoid secret leakage into screen recordings / bug reports with screenshots.
 *
 * Also logged at INFO/WARN level via the caller, so logcat-only debugging (when
 * a PC + adb is available) sees the same data shape.
 */
data class ConnectionTestResult(
    val success: Boolean,
    val keySource: String,
    val keyPrefix: String,
    val winningModel: String?,
    val failureCode: Int?,
    val failureReason: String?,
    val userFacingHint: String
) {
    /** Human-readable form of [keySource] for direct on-screen display. */
    val sourceLabel: String
        get() = when (keySource) {
            "USER_PASTED_IN_DATASTORE" -> "Your AI Setup save (overrides the bundled key)"
            "BUNDLED_FROM_CI_SECRET_GEMINI_API_KEY_2_5_FLASH_LITE" -> "Bundled GitHub Actions secret"
            "LEGACY_BUILD_CONFIG_GEMINI_API_KEY" -> "Legacy local.properties key"
            else -> "No key resolved — set GEMINI_API_KEY_2_5_FLASH_LITE in repo Settings → Secrets, or paste one here"
        }
}

@Singleton
class AiRepository @Inject constructor(
    private val geminiApi: GeminiApiService,
    private val prefs: AppPreferences
) {
    companion object {
        const val MAX_FREE_REGENERATIONS = 5
        private const val API_COOLDOWN_MS = 1000L
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

    /**
     * Resolution order for the API key:
     *  1. User-set key in DataStore (Settings → "AI Setup"). Empty by default.
     *  2. BuildConfig.GEMINI_API_KEY_2_5_FLASH_LITE — the bundled CI key whose
     *     name matches the user-added GH secret `GEMINI_API_KEY_2_5_FLASH_LITE`.
     *  3. BuildConfig.GEMINI_API_KEY — legacy slot kept for backwards compat
     *     with older local.properties configurations.
     *
     * Returns null if all three are empty so callers can throw [NoApiKeyException].
     *
     * Logs the resolution source + length + first-4-char prefix on every
     * call so `adb logcat -d | grep AiRepository` answers "which key is
     * actually being sent to Gemini?" in 5 seconds after the next roast.
     * The 4-char prefix is intentionally tiny — enough to distinguish an
     * AIzaSy-... AI Studio key from an AQ.-... newer bearer-style token
     * without leaking the secret value into logs.
     */
    private suspend fun apiKey(): String? {
        val userKey = prefs.geminiApiKey.first()
        val bundled = BuildConfig.GEMINI_API_KEY_2_5_FLASH_LITE
        val legacy = BuildConfig.GEMINI_API_KEY
        val source = when {
            userKey.isNotBlank() -> "USER_PASTED_IN_DATASTORE"
            bundled.isNotBlank() -> "BUNDLED_FROM_CI_SECRET_GEMINI_API_KEY_2_5_FLASH_LITE"
            legacy.isNotBlank() -> "LEGACY_BUILD_CONFIG_GEMINI_API_KEY"
            else -> "EMPTY_NO_KEY_RESOLVED"
        }
        val resolved = userKey.ifBlank { bundled }.ifBlank { legacy }
        if (resolved.isNotBlank()) {
            // 4-char prefix is enough to distinguish AI Studio "AIza" from
            // the newer "AQ." OAuth-style token — never log more than the
            // first 4 chars to avoid leakage in adb logcat / Crashlytics.
            //
            // Gated behind BuildConfig.DEBUG so this routine info-line doesn't
            // bury real faults in Crashlytics release-mode aggregates — the
            // WARN line on the no-key path below still always fires for the
            // genuinely-broken case.
            val safePrefix = if (resolved.length >= 4) resolved.substring(0, 4) else "(too short)"
            if (BuildConfig.DEBUG) {
                Log.i("AiRepository", "Resolved Gemini key: source=$source length=${resolved.length} prefix=$safePrefix")
            }
        } else {
            Log.w("AiRepository", "Resolved Gemini key: source=$source (callers will see NoApiKeyException)")
        }
        return resolved.takeIf { it.isNotBlank() }
    }

    /**
     * Calls Gemini with each model from [GeminiApiService.MODEL_CANDIDATES]
     * until one returns 2xx.
     *
     * Retry policy (updated 2026-07-03):
     *  - HTTP 400 (bad-request / FAILED_PRECONDITION / safety-block) — try
     *    next candidate. The previous code already did this.
     *  - HTTP 404 (model-not-found, region-retired, alias renamed) — ALSO try
     *    next candidate. Previously this short-circuited the whole chain,
     *    which is why AI Studio's 2026 mid-year 2.x-flash-lite retirement
     *    surfaced as a guaranteed failure even when the user's key was
     *    perfectly healthy and an unretired model was further down the list.
     *  - HTTP 401 / 403 (key invalid / restricted / region-locked) — bubble
     *    up unchanged so the user sees the right error.
     *  - HTTP 429 (rate limited) — bubble up; retrying wastes quota.
     *  - Network failures / Cancellation — bubble up unchanged.
     */
    private suspend fun callGeminiWithFallback(
        apiKey: String,
        request: GeminiRequest
    ): Result<com.dhanuk.debtbro.data.network.GeminiResponse> {
        var lastError: Throwable? = null
        for ((index, model) in GeminiApiService.MODEL_CANDIDATES.withIndex()) {
            try {
                val response = geminiApi.generateContent(
                    url = GeminiApiService.buildUrl(model),
                    apiKey = apiKey,
                    request = request
                )
                if (index > 0) {
                    Log.i("AiRepository", "Gemini model '$model' succeeded after " +
                        "'${GeminiApiService.MODEL_CANDIDATES.take(index).joinToString(",")}' " +
                        "were rejected with HTTP 400")
                }
                return Result.success(response)
            } catch (e: HttpException) {
                lastError = e
                // Both 400 (bad-request / FAILED_PRECONDITION) and 404
                // (retired model / renamed alias) are treated as "try the next
                // candidate" — re-aligned with the docstring above. Anything
                // else (401/403/429/network) bubbles up unchanged.
                if (e.code() != 400 && e.code() != 404) {
                    // Wrong key, forbidden, rate limited, etc. — don't retry.
                    return Result.failure(e)
                }
                // Parse the response body so the logcat warning shows Gemini's
                // OWN explanation rather than just Retrofit's "HTTP 400 " status
                // line. errorBody().string() consumes the buffer, so we read
                // exactly once and re-stringify through JsonParser for cleaner
                // formatting. Falls back to raw body bytes if not valid JSON,
                // falls back to e.message if the response had no body at all.
                val rawBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                val parsedBody = rawBody?.takeIf { it.isNotBlank() }?.let { body ->
                    runCatching {
                        JsonParser.parseString(body).toString()
                    }.getOrElse { body }
                }
                val reason = parsedBody?.take(360)
                    ?: e.message?.take(120)
                    ?: "(no body, no message)"
                Log.w("AiRepository", "Gemini ${e.code()} for model '$model': $reason")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
        Log.w("AiRepository", "All Gemini model candidates failed with HTTP 400. " +
            "API key may be valid but incompatible with this model family — " +
            "the user can paste a different key in Settings → AI Setup.")
        return Result.failure(lastError ?: Exception("All Gemini model candidates failed"))
    }

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
        val key = apiKey() ?: throw NoApiKeyException()
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

        val response = callGeminiWithFallback(
            apiKey = key,
            request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(userMessage)), role = "user")),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(systemText)), role = "system"),
                generationConfig = GenerationConfig(temperature = temp, maxOutputTokens = 200)
            )
        ).getOrThrow()

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
        val key = apiKey() ?: throw NoApiKeyException()
        val langCode = prefs.selectedLanguage.first()
        val currency = prefs.defaultCurrency.first()
        val langInstruction = buildSystemPrompt("MILD", langCode, null).substringBefore("\n")

        val prompt = "Total lent: ${currency}$totalLent to friends. Total I owe: ${currency}$totalOwed. Recovery rate: $recoveryRate%. Worst debtor: $worstDebtor. Give ONE sharp, funny, honest 2-line insight. Hinglish welcome."

        val response = callGeminiWithFallback(
            apiKey = key,
            request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)), role = "user")),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart("$langInstruction\nYou are a funny personal finance analyst. Give ONE sharp 2-line insight. No disclaimers.")), role = "system"),
                generationConfig = GenerationConfig(temperature = 0.3, maxOutputTokens = 150)
            )
        ).getOrThrow()

        response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw Exception("Empty AI response")
    }

    suspend fun generateSplitSummary(title: String, total: Double, perPerson: Double, count: Int): Result<String> = runCatching {
        ensureRateLimit()
        val key = apiKey() ?: throw NoApiKeyException()
        val langCode = prefs.selectedLanguage.first()
        val currency = prefs.defaultCurrency.first()
        val langInstruction = buildSystemPrompt("MILD", langCode, null).substringBefore("\n")

        val prompt = "Split: $title, Total: ${currency}$total, $count people, ${currency}$perPerson each. Write ONE funny line about this. Hinglish ok."

        val response = callGeminiWithFallback(
            apiKey = key,
            request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)), role = "user")),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart("$langInstruction\nYou are a funny commentator. One line only. Be creative.")), role = "system"),
                generationConfig = GenerationConfig(temperature = 0.7, maxOutputTokens = 100)
            )
        ).getOrThrow()

        response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw Exception("Empty AI response")
    }

    suspend fun testConnection(): Boolean {
        return try {
            ensureRateLimit()
            val key = apiKey() ?: return false
            callGeminiWithFallback(
                apiKey = key,
                request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart("Say OK")), role = "user"))
                )
            ).fold(
                onSuccess = { true },
                onFailure = { false }
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        }
    }

    /**
     * Structured self-diagnose call. Returns a [ConnectionTestResult] with
     * enough detail for the user to read the result on-device WITHOUT a PC —
     * each MODEL_CANDIDATES entry is exercised with a tiny 5-token "Say OK"
     * prompt and the result collects:
     *
     *  - The actual key source + 4-char prefix (proves which slot is winning
     *    the DataStore-vs-BuildConfig chain, without leaking the secret).
     *  - The first model that returned a non-empty 2xx body, if any.
     *  - For failure paths, the parsed GeminiError JSON (or raw body if the
     *    response wasn't JSON), so the user can read Gemini's own reason.
     *
     * The retry policy mirrors [callGeminiWithFallback]: 400/404 retried; 401,
     * 403, 429 (and network errors) bubble up immediately with an actionable
     * one-liner.
     *
     * Safe to call from the UI: 1 request per candidate, max 5 small ones,
     * capped at ~6 seconds even on the worst-case all-candidates-fail path.
     */
    suspend fun runAiConnectionTest(): ConnectionTestResult {
        ensureRateLimit()
        val userKey = prefs.geminiApiKey.first()
        val bundled = BuildConfig.GEMINI_API_KEY_2_5_FLASH_LITE
        val legacy = BuildConfig.GEMINI_API_KEY
        val (packaged, source) = when {
            userKey.isNotBlank() -> userKey to "USER_PASTED_IN_DATASTORE"
            bundled.isNotBlank() -> bundled to "BUNDLED_FROM_CI_SECRET_GEMINI_API_KEY_2_5_FLASH_LITE"
            legacy.isNotBlank() -> legacy to "LEGACY_BUILD_CONFIG_GEMINI_API_KEY"
            else -> null to "EMPTY_NO_KEY_RESOLVED"
        }
        val prefix = packaged?.let { if (it.length >= 4) it.substring(0, 4) else "(too short)" } ?: "(empty)"

        if (packaged.isNullOrBlank()) {
            return ConnectionTestResult(
                success = false,
                keySource = source,
                keyPrefix = prefix,
                winningModel = null,
                failureCode = null,
                failureReason = null,
                userFacingHint = "No API key found. Set GEMINI_API_KEY_2_5_FLASH_LITE in repo Settings → Secrets " +
                    "(and re-push main), OR paste a key here in Settings → AI Setup."
            )
        }

        var lastHttpCode: Int? = null
        var lastErrorBody: String? = null

        for (model in GeminiApiService.MODEL_CANDIDATES) {
            try {
                val response = geminiApi.generateContent(
                    url = GeminiApiService.buildUrl(model),
                    apiKey = packaged,
                    request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart("Say OK")), role = "user")),
                        generationConfig = GenerationConfig(temperature = 0.0, maxOutputTokens = 5)
                    )
                )
                val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!text.isNullOrBlank()) {
                    Log.i("AiRepository", "Connection test: model '$model' succeeded (key source=$source)")
                    return ConnectionTestResult(
                        success = true,
                        keySource = source,
                        keyPrefix = prefix,
                        winningModel = model,
                        failureCode = null,
                        failureReason = null,
                        userFacingHint = "AI is working. First live model: $model."
                    )
                }
                // 200 but empty body — treat as candidate-failed, try next.
                lastHttpCode = 200
                lastErrorBody = "(empty 200 response body — unusual)"
            } catch (e: HttpException) {
                lastHttpCode = e.code()
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                val parsed = body?.takeIf { it.isNotBlank() }?.let { b ->
                    runCatching { JsonParser.parseString(b).toString() }.getOrElse { b }
                }
                lastErrorBody = parsed?.take(360) ?: e.message?.take(120) ?: "(no body)"

                if (e.code() != 400 && e.code() != 404) {
                    val hint = when (e.code()) {
                        401, 403 -> "Key invalid or restricted (HTTP ${e.code()}). " +
                            "Try a different Gemini API key from a different AI Studio project."
                        429 -> "Rate-limited (HTTP 429). Wait a minute and try again."
                        else -> "Transient error (HTTP ${e.code()}). Retry shortly."
                    }
                    Log.w("AiRepository", "Connection test: model '$model' returned HTTP ${e.code()}; aborting: $lastErrorBody")
                    return ConnectionTestResult(
                        success = false,
                        keySource = source,
                        keyPrefix = prefix,
                        winningModel = null,
                        failureCode = e.code(),
                        failureReason = lastErrorBody,
                        userFacingHint = hint
                    )
                }
                // Else (400/404) — retry on next candidate.
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AiRepository", "Connection test: network error on model '$model': ${e.message?.take(120)}")
                return ConnectionTestResult(
                    success = false,
                    keySource = source,
                    keyPrefix = prefix,
                    winningModel = null,
                    failureCode = null,
                    failureReason = e.message?.take(280) ?: "Unknown error",
                    userFacingHint = "Network error. Check your internet connection and retry."
                )
            }
        }

        // All MODEL_CANDIDATES returned 400/404 — the key may be valid for Gemini
        // broadly but the project doesn't have any of these specific models
        // enabled. Surface that clearly so the user knows to try a different key.
        Log.w("AiRepository", "Connection test: all ${GeminiApiService.MODEL_CANDIDATES.size} models returned 4xx for key source=$source")
        return ConnectionTestResult(
            success = false,
            keySource = source,
            keyPrefix = prefix,
            winningModel = null,
            failureCode = lastHttpCode,
            failureReason = lastErrorBody ?: "(no captured error body)",
            userFacingHint = "All ${GeminiApiService.MODEL_CANDIDATES.size} Gemini Flash models returned 4xx for this key. " +
                "The key may be valid for non-Flash models on this project. Try pasting a different " +
                "Gemini API key from a fresh AI Studio project (aistudio.google.com/app/apikey)."
        )
    }
}
