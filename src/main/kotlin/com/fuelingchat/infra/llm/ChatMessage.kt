package com.fuelingchat.infra.llm

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val body: String,
    val timestamp: Long,
)
