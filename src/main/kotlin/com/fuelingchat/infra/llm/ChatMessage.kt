package com.fuelingchat.infra.llm

import kotlinx.serialization.Serializable

/**
 * One dialog-history entry: role, body and epoch-millis UTC timestamp. Serialized one-per-line
 * into the .jsonl journal by DialogHistory (CONTRACT-HISTORY-001, D-1).
 */
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
