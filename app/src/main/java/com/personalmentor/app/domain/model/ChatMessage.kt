package com.personalmentor.app.domain.model

enum class Sender { USER, ASSISTANT }

data class ChatMessage(
    val id: Long = 0,
    val text: String,
    val sender: Sender,
    val mode: AssistantMode,
    val timestamp: Long = System.currentTimeMillis(),
)
