package com.triloo.data.ai

import com.triloo.BuildConfig
import com.triloo.data.remote.OpenAiApi
import com.triloo.data.remote.OpenAiChatRequest
import com.triloo.data.remote.OpenAiMessage
import com.triloo.data.remote.OpenAiResponseFormat
import javax.inject.Inject
import javax.inject.Singleton

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

        return runCatching {
            openAiApi.chatCompletions(
                authorization = "Bearer ${BuildConfig.OPENAI_API_KEY}",
                request = request
            )
        }.getOrNull()?.choices?.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun hasValidApiKey(): Boolean {
        val key = BuildConfig.OPENAI_API_KEY
        return key.isNotBlank() && !key.contains("YOUR_OPENAI_API_KEY")
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
