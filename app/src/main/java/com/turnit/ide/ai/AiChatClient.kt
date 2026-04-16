package com.turnit.ide.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.turnit.ide.ui.AiModel
import com.turnit.ide.ui.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object AiChatClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendMessage(
        model: AiModel,
        chatHistory: List<ChatMessage>,
        newPrompt: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val normalizedHistory = if (
                chatHistory.lastOrNull()?.role == "user" &&
                    chatHistory.lastOrNull()?.content == newPrompt
            ) {
                chatHistory
            } else {
                chatHistory + ChatMessage(role = "user", content = newPrompt)
            }

            val payload = mapOf(
                "model" to model.modelId,
                "messages" to normalizedHistory.map {
                    mapOf(
                        "role" to it.role,
                        "content" to it.content
                    )
                }
            )

            val requestBody = gson.toJson(payload).toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(model.apiUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
            if (model.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${model.apiKey}")
            }

            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext "Error: HTTP ${response.code} ${response.message}"
                }
                if (bodyText.isBlank()) {
                    return@withContext "Error: Empty response body"
                }

                val jsonObject = JsonParser.parseString(bodyText).asJsonObject
                val choices = jsonObject.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val messageObject = choices[0].asJsonObject.getAsJsonObject("message")
                    val content = messageObject?.get("content")?.asString?.trim().orEmpty()
                    if (content.isNotBlank()) {
                        return@withContext content
                    }
                }

                val content = jsonObject.get("content")?.asString?.trim().orEmpty()
                if (content.isNotBlank()) {
                    return@withContext content
                }

                "Error: Unable to parse AI response"
            }
        } catch (e: Exception) {
            "Error: ${e.message ?: "Network request failed"}"
        }
    }
}
