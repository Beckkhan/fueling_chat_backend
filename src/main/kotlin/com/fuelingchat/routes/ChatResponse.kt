package com.fuelingchat.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CONTRACT-API-002 success body: exactly {summary, provider, elapsed_ms} — no other fields (G-6).
 */
@Serializable
data class ChatResponse(
    val summary: String,
    val provider: String,
    @SerialName("elapsed_ms") val elapsedMs: Long,
)

/**
 * CONTRACT-API-002 error body: {"error": "<masked message>"} — masked bodies only,
 * never SQL, credentials or raw provider errors.
 */
@Serializable
data class ChatErrorResponse(
    val error: String,
)
