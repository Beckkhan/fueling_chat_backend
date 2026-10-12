package com.fuelingchat.infra.llm

data class ChatMessage(
    val role: String,
    val body: String,
    val timestamp: Long,
)
