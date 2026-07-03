package com.dhanuk.debtbro.data.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

// Gemini Flash 2.5 API models (Vertex AI / AI Studio compatible)
data class GeminiPart(val text: String)
data class GeminiContent(val parts: List<GeminiPart>, val role: String = "user")
data class GeminiCandidate(val content: GeminiContent)
data class GeminiResponse(val candidates: List<GeminiCandidate>, val error: GeminiError?)
data class GeminiError(val code: Int, val message: String, val status: String)

// Request model
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig = GenerationConfig()
)

data class GenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 300,
)

interface GeminiApiService {
    /**
     * Uses [Url] so the AI repository can swap model identifiers per call.
     * This lets us implement a graceful fallback chain ("gemini-2.5-flash-lite"
     * → "gemini-2.0-flash-lite" → …) without writing one Retrofit method per model.
     *
     * The Gemini API key is passed as a `?key=...` query parameter (Gemini API
     * does not support the standard `Authorization: Bearer` header for AI Studio
     * endpoints; Vertex AI does, but the AI Studio path is what free-tier keys
     * use). Treat the key as a secret — it is redacted from OkHttp's BASIC log.
     */
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    companion object {
        /**
         * Models to attempt in order on each AI call.
         *
         * Updated 2026-07-03. The `gemini-2.0-flash-lite` family (and the
         * bare `gemini-flash-lite` Google-deprecated aliases) were retired
         * by AI Studio through 2026; the previous chain started there and —
         * after the deprecations — every deployed APK hit HTTP 400/404 on
         * every candidate. Primary is now `gemini-2.5-flash-lite`, the model
         * the project's bundled `GEMINI_API_KEY_2_5_FLASH_LITE` secret is
         * named after and the most widely-supported free-tier Flash-Lite on
         * AI Studio as of July 2026.
         *
         * 2026-07-03 follow-up pruning: `gemini-flash-lite` (the bare alias)
         * and `gemini-1.5-flash-latest` were both verified to return HTTP 404
         * against AI Studio — they no longer exist. Keeping them in the
         * chain only guaranteed every roast attempt burned two extra
         * round-trips before failure. Pruned.
         *
         * Order rationale, top-to-bottom:
         *  - `gemini-2.5-flash-lite` — primary, name-matched to the GH secret.
         *  - `gemini-2.5-flash` — slightly larger sibling, the safest
         *    paid-tier fallback when 2.5-flash-lite is region-gated.
         *  - `gemini-flash-lite-latest` — Google's rolling alias; cheap to
         *    keep in case 2.5-flash-lite is renamed.
         */
        val MODEL_CANDIDATES: List<String> = listOf(
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-flash-lite-latest"
        )

        fun buildUrl(model: String): String = "v1beta/models/$model:generateContent"
    }
}
