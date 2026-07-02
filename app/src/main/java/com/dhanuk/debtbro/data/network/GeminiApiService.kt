package com.dhanuk.debtbro.data.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

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
    @POST("v1beta/models/gemini-2.5-flash-lite:generateContent")
    suspend fun generateContent(@Query("key") apiKey: String, @Body request: GeminiRequest): GeminiResponse
}