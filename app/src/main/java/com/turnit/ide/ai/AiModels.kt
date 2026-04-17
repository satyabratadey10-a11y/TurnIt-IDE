package com.turnit.ide.ai

data class ChatMessage(val role: String, val content: String)

data class AiModel(
    val name: String,
    val modelId: String,
    val apiUrl: String,
    val apiKey: String,
    val isCustom: Boolean = false
)
