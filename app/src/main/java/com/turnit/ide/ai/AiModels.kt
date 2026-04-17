package com.turnit.ide.ai

import java.util.concurrent.atomic.AtomicLong

private object ChatMessageIdGenerator {
    private val nextId = AtomicLong(1L)
    fun next(): Long = nextId.getAndIncrement()
}

data class ChatMessage(
    val role: String,
    val content: String,
    val id: Long = ChatMessageIdGenerator.next()
)

data class AiModel(
    val name: String,
    val modelId: String,
    val apiUrl: String,
    val apiKey: String,
    val isCustom: Boolean = false
)
