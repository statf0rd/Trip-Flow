package com.triloo.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit-контракт для OpenAI-совместимого chat completions endpoint у Gemini.
 */
interface OpenAiApi {
    @POST("v1beta/openai/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: OpenAiChatRequest
    ): OpenAiChatResponse
}

/**
 * Тело запроса к chat completions.
 */
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerializedName("response_format") val responseFormat: OpenAiResponseFormat? = null,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null
)

/**
 * Сообщение внутри диалога, отправляемого в модель.
 */
data class OpenAiMessage(
    val role: String,
    val content: String
)

/**
 * Ограничение формата ответа модели.
 */
data class OpenAiResponseFormat(
    val type: String
)

/**
 * Ответ chat completions с выборками и возможной ошибкой API.
 */
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice> = emptyList(),
    val error: OpenAiError? = null
)

/**
 * Один вариант ответа модели.
 */
data class OpenAiChoice(
    val message: OpenAiMessage? = null
)

/**
 * Ошибка, возвращаемая OpenAI-совместимым AI endpoint.
 */
data class OpenAiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
