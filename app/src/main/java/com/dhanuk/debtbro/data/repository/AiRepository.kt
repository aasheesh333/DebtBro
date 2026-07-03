package com.dhanuk.debtbro.data.repository

import android.util.Log
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.datastore.SecureStorage
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
            "USER_PASTED_IN_SECURE_STORAGE" -> "Your AI Setup save (encrypted, overrides the bundled key)"
            "USER_PASTED_IN_DATASTORE" -> "Your AI Setup save (legacy plaintext slot — migrating to encrypted)"
            "BUNDLED_FROM_CI_SECRET_GEMINI_API_KEY_2_5_FLASH_LITE" -> "Bundled GitHub Actions secret"
            "LEGACY_BUILD_CONFIG_GEMINI_API_KEY" -> "Legacy local.properties key"
            else -> "No key resolved — set GEMINI_API_KEY_2_5_FLASH_LITE in repo Settings → Secrets, or paste one here"
        }
}

@Singleton
class AiRepository @Inject constructor(
    private val geminiApi: GeminiApiService,
    private val prefs: AppPreferences,
    private val secureStorage: SecureStorage
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
        // P2-7 (2026-07-03): SecureStorage (EncryptedSharedPreferences) is
        // now the primary read path for the user-pasted Gemini key. The
        // previous DataStore slot (`prefs.geminiApiKey`) is plain on disk,
        // so we treat it as a migration source ONLY — one first-launch we
        // copy any pre-existing DataStore key into SecureStorage and then
        // clear it from DataStore, so existing users silently upgrade to
        // the encrypted path without having to re-paste their key.
        //
        // Trim every source — GitHub Actions secrets and user-pasted strings
        // routinely carry invisible trailing newlines/spaces. One un-trimmed
        // char becomes URL-encoded (e.g. %0A) in the ?key= query param and
        // Gemini rejects the whole request with HTTP 400.
        val secureKey = secureStorage.geminiApiKey.first().trim()
        val legacyDataStoreKey = prefs.geminiApiKey.first().trim()
        val userKey = when {
            secureKey.isNotBlank() -> secureKey
            legacyDataStoreKey.isNotBlank() -> {
                // One-time migration: lift the legacy plain-DataStore key
                // into SecureStorage, then best-effort clear the DataStore
                // entry so it doesn't sit around on disk forever. Failures
                // here are non-fatal — we still resolve `userKey` from the
                // legacy value below for the current call.
                runCatching {
                    secureStorage.saveGeminiApiKey(legacyDataStoreKey)
                    prefs.clearGeminiKey()
                }
                legacyDataStoreKey
            }
            else -> ""
        }
        val bundled = BuildConfig.GEMINI_API_KEY_2_5_FLASH_LITE.trim()
        val legacy = BuildConfig.GEMINI_API_KEY.trim()
        val source = when {
            userKey.isNotBlank() -> if (secureKey.isNotBlank()) "USER_PASTED_IN_SECURE_STORAGE" else "USER_PASTED_IN_DATASTORE"
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
     *  - HTTP 429 / 503 (rate-limited / transient overload) — RETRY the SAME
     *    model with a 1.2s backoff before falling through to the next
     *    candidate. Google's "high demand" 503s on Flash-Lite are typically
     *    sub-second and one retry is enough to clear them; previously these
     *    bubbled up immediately and surfaced as "AI failed" to the user even
     *    though their key and the model were both healthy.
     *  - Network failures / Cancellation — bubble up unchanged.
     */
    private suspend fun callGeminiWithFallback(
        apiKey: String,
        request: GeminiRequest
    ): Result<com.dhanuk.debtbro.data.network.GeminiResponse> {
        var lastError: Throwable? = null
        for ((index, model) in GeminiApiService.MODEL_CANDIDATES.withIndex()) {
            var attempt = 0
            while (true) {
                try {
                    val response = geminiApi.generateContent(
                        url = GeminiApiService.buildUrl(model),
                        apiKey = apiKey,
                        request = request
                    )
                    if (index > 0 || attempt > 0) {
                        Log.i("AiRepository", "Gemini model '$model' succeeded (attempt=${attempt + 1}) " +
                            "after earlier rejection(s)")
                    }
                    return Result.success(response)
                } catch (e: HttpException) {
                    lastError = e
                    val code = e.code()

                    // HTTP 429 / 503 are transient — retry the SAME model once
                    // with a short backoff before giving up on it.
                    if ((code == 429 || code == 503) && attempt == 0) {
                        val rawBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                        Log.w("AiRepository", "Gemini ${code} (transient) for model '$model': " +
                            "${rawBody?.take(200) ?: e.message?.take(120)}. Retrying in 1200ms.")
                        kotlinx.coroutines.delay(1200L)
                        attempt++
                        continue
                    }

                    // Bubble up auth / forbidden / persistent-rate-limit immediately.
                    if (code != 400 && code != 404 && code != 429 && code != 503) {
                        return Result.failure(e)
                    }

                    // 400/404 OR a second 429/503 on the same model — fall
                    // through to the next candidate. Parsing the error body
                    // so logcat shows Gemini's own explanation rather than
                    // Retrofit's bare status line.
                    val parsedBody = runCatching {
                        JsonParser.parseString(
                            e.response()?.errorBody()?.string() ?: ""
                        ).toString()
                    }.getOrNull()
                    val reason = parsedBody?.take(360)
                        ?: e.message?.take(120)
                        ?: "(no body, no message)"
                    Log.w("AiRepository", "Gemini ${code} for model '$model' (attempt=${attempt + 1}): $reason")
                    break
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return Result.failure(e)
                }
            }
        }
        Log.w("AiRepository", "All Gemini model candidates failed (last error: " +
            "${lastError?.message?.take(120) ?: "unknown"}). The user can paste a " +
            "different key in Settings → AI Setup or retry shortly for transient 503s.")
        return Result.failure(lastError ?: Exception("All Gemini model candidates failed"))
    }

    private fun isIndicFamily(langCode: String): Boolean =
        langCode in setOf("hi", "mr", "pa", "gu")

    private fun buildRoastPersona(roastLevel: String, langCode: String, debtDirection: String): String {
        val indic = isIndicFamily(langCode)
        val location = if (indic) "Indian " else ""
        val lingual = if (indic) "Use Hinglish naturally (mix Hindi and English)" else "Use your selected language naturally"
        val scenarios = if (indic) "relatable Indian scenarios (chai, zomato, petrol prices)" else "relatable everyday scenarios"
        val wildMetaphor = if (indic) "wild metaphors or Bollywood comparisons" else "wild metaphors or pop-culture references"
        return when (roastLevel) {
            "MILD" -> """You are a witty ${location}friend writing a WhatsApp message about money.$debtDirection
Key rules:
- Write 2-3 lines (140-180 characters total, push for longer not shorter)
- $lingual
- Be warm, funny, and creative — use metaphors or shared jokes
- Start conversationally, end with a lighthearted nudge
- NO aggressive language or insults
- 1-2 emojis max
- Do NOT use hashtags or formal language
- Do NOT wrap your response in quotation marks
- Every response must be COMPLETELY DIFFERENT from previous ones — vary phrases, metaphors, and tone"""
            "SPICY" -> """You are a brutally funny ${location}debt collector with legendary comedic timing.$debtDirection
Key rules:
- Write 2-3 lines (140-180 characters total, push for longer not shorter)
- $lingual
- Be creatively savage — use $wildMetaphor
- Must be hilarious, NOT offensive or abusive
- Punch UP — laugh at the situation, not the person
- 2-3 emojis for maximum impact
- Think: "funniest WhatsApp forward ever" energy
- Do NOT wrap your response in quotation marks
- Every response must be COMPLETELY DIFFERENT from previous ones — vary phrases, metaphors, and tone
- HARD GUARDRAIL (Play Store / OpenAI safety): no slurs, no hate speech, no
  casteist/sexist/religious attacks, no body-shaming, no threats of violence,
  and no real-people-names that would identify a non-public individual. If a
  metaphor would lean into any of those, drop it and pick another. Keep it
  playful — roast the lateness, never the person."""
            else -> """You are a clever, sarcastic ${location}friend dropping a subtle money hint.$debtDirection
Key rules:
- Write 2-3 lines (140-180 characters total, push for longer not shorter)
- $lingual
- Be passive-aggressive but funny — think ironic compliments
- Use $scenarios
- No direct begging or rudeness
- 1-2 emojis
- Memorable enough to screenshot and share
- Do NOT wrap your response in quotation marks
- Every response must be COMPLETELY DIFFERENT from previous ones — vary phrases, metaphors, and tone"""
        }
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
        val persona = buildRoastPersona(roastLevel, selectedLangCode, debtDirection)
        return "$langInstruction\n$persona"
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
                generationConfig = GenerationConfig(temperature = temp, maxOutputTokens = 400)
            )
        ).getOrThrow()

        val text = response.candidates.firstOrNull()?.content?.parts
            ?.joinToString("") { it.text ?: "" }
            ?.takeIf { it.isNotBlank() }
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

        val lingualHint = if (isIndicFamily(langCode)) "Hinglish welcome" else "Use your selected language"
        val prompt = "Total lent: ${currency}$totalLent to friends. Total I owe: ${currency}$totalOwed. Recovery rate: $recoveryRate%. Worst debtor: $worstDebtor. Give ONE sharp, funny, honest 2-line insight. $lingualHint."

        val response = callGeminiWithFallback(
            apiKey = key,
            request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)), role = "user")),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart("$langInstruction\nYou are a funny personal finance analyst. Give ONE sharp 2-line insight. No disclaimers.")), role = "system"),
                generationConfig = GenerationConfig(temperature = 0.3, maxOutputTokens = 350)
            )
        ).getOrThrow()

        response.candidates.firstOrNull()?.content?.parts
            ?.joinToString("") { it.text ?: "" }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw Exception("Empty AI response")
    }

    suspend fun generateSplitSummary(title: String, total: Double, perPerson: Double, count: Int): Result<String> = runCatching {
        ensureRateLimit()
        val key = apiKey() ?: throw NoApiKeyException()
        val langCode = prefs.selectedLanguage.first()
        val currency = prefs.defaultCurrency.first()
        val langInstruction = buildSystemPrompt("MILD", langCode, null).substringBefore("\n")

        val lingualHint = if (isIndicFamily(langCode)) "Hinglish ok" else "Use your selected language"
        val prompt = "Split: $title, Total: ${currency}$total, $count people, ${currency}$perPerson each. Write ONE funny line about this. $lingualHint."

        val response = callGeminiWithFallback(
            apiKey = key,
            request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)), role = "user")),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart("$langInstruction\nYou are a funny commentator. One line only. Be creative.")), role = "system"),
                generationConfig = GenerationConfig(temperature = 0.7, maxOutputTokens = 250)
            )
        ).getOrThrow()

        response.candidates.firstOrNull()?.content?.parts
            ?.joinToString("") { it.text ?: "" }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
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
        val userKey = prefs.geminiApiKey.first().trim()
        val bundled = BuildConfig.GEMINI_API_KEY_2_5_FLASH_LITE.trim()
        val legacy = BuildConfig.GEMINI_API_KEY.trim()
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
                        generationConfig = GenerationConfig(temperature = 0.1, maxOutputTokens = 5)
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
