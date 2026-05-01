package com.dhanuk.debtbro.data.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class GroqMessage(val role: String, val content: String)
data class GroqRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.9,
    val max_tokens: Int = 300,
    val stream: Boolean = false
)
data class GroqChoice(val message: GroqMessage)
data class GroqResponse(val choices: List<GroqChoice>)

interface GroqApiService {
    @POST("chat/completions")
    suspend fun chat(@Header("Authorization") auth: String, @Body request: GroqRequest): GroqResponse
}
