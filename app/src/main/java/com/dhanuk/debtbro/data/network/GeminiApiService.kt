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

// Request model: Gemini uses a simplified structure compared to Groq
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig = GenerationConfig()
)

data class GenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 300,
    val topK: Int = 1,
    val topP: Double = 0.95
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
         * Order matters: most widely-supported first to minimize wasted calls when
         * a key works fine but the named variant is preview-only or regionally
         * restricted. The previous ordering (gemini-2.5-flash-lite first) wasted
         * ~5 seconds of latency on every healthy call because that name returned
         * HTTP 400 for the user's free-tier key — the whole chain had to loop
         * through all 6 candidates before producing a response.
         *
         * `gemini-2.0-flash-lite` is the most reliable across all key tiers
         * (free, paid, Vertex AI). `gemini-2.5-flash-lite` is kept at the very
         * end for users whose keys are SPECIFICALLY scoped to that preview
         * model — a small minority that pays the small latency cost intentionally.
         *
         * When the user adds a custom key in Settings, the same fallback chain
         * applies — a key they're testing will quietly succeed against the most
         * permissive model it has access to.
         */
        val MODEL_CANDIDATES: List<String> = listOf(
            "gemini-2.0-flash-lite",
            "gemini-flash-lite-latest",
            "gemini-flash-lite",
            "gemini-1.5-flash-latest",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite"
        )

        fun buildUrl(model: String): String = "v1beta/models/$model:generateContent"
    }
}
