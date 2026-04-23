package com.triloo.data.ai

import com.triloo.BuildConfig
import com.triloo.data.remote.OpenAiApi
import com.triloo.data.remote.OpenAiChatRequest
import com.triloo.data.remote.OpenAiMessage
import com.triloo.data.remote.OpenAiResponseFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Тонкая обёртка над Gemini через OpenAI-совместимый endpoint для AI-рекомендаций.
 */
@Singleton
class OpenAiService @Inject constructor(
    private val openAiApi: OpenAiApi
) {

    suspend fun generateJson(
        systemPrompt: String,
        userPrompt: String,
        model: String = DEFAULT_MODEL,
        temperature: Double = 0.2,
        maxTokens: Int = 800
    ): String? {
        if (!hasValidApiKey()) return null

        val request = OpenAiChatRequest(
            model = model,
            messages = listOf(
                OpenAiMessage(role = "system", content = systemPrompt),
                OpenAiMessage(role = "user", content = userPrompt)
            ),
            responseFormat = OpenAiResponseFormat(type = "json_object"),
            temperature = temperature,
            maxTokens = maxTokens
        )

        for (apiKey in validApiKeys()) {
            val content = runCatching {
                openAiApi.chatCompletions(
                    authorization = "Bearer $apiKey",
                    request = request
                )
            }.getOrNull()?.choices?.firstOrNull()?.message?.content?.trim()

            if (!content.isNullOrBlank()) {
                return content
            }
        }

        return null
    }

    private fun hasValidApiKey(): Boolean {
        return validApiKeys().isNotEmpty()
    }

    private fun validApiKeys(): List<String> {
        return BuildConfig.APP_GEMINI_API_KEYS
            .split(',')
            .map { it.trim() }
            .filter { key ->
                key.isNotBlank() && !key.contains("YOUR_GEMINI_API_KEY")
            }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
