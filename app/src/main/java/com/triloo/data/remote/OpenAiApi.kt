package com.triloo.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAiApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: OpenAiChatRequest
    ): OpenAiChatResponse
}

data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerializedName("response_format") val responseFormat: OpenAiResponseFormat? = null,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null
)

data class OpenAiMessage(
    val role: String,
    val content: String
)

data class OpenAiResponseFormat(
    val type: String
)

data class OpenAiChatResponse(
    val choices: List<OpenAiChoice> = emptyList(),
    val error: OpenAiError? = null
)

data class OpenAiChoice(
    val message: OpenAiMessage? = null
)

data class OpenAiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
