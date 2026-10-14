package com.fuelingchat.infra.llm

import kotlinx.serialization.Serializable

/** One dialog message, serialized to a single line of the `.jsonl` dialog journal (D-1). */
@Serializable
data class ChatMessage(
    val role: String,
    val body: String,
    val timestamp: Long,
)
