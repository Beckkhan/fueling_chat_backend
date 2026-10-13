package com.fuelingchat.infra.llm

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val body: String,
    val timestamp: Long,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}
