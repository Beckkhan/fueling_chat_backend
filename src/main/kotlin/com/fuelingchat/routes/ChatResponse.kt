package com.fuelingchat.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CONTRACT-API-002 200 body — exactly three fields: {summary, provider, elapsed_ms} (G-6).
 */
@Serializable
data class ChatResponse(
    val summary: String,
    val provider: String,
    @SerialName("elapsed_ms")
    val elapsedMs: Long,
)

/**
 * CONTRACT-API-002 error body: {"error": "<masked message>"} for the 400/500/502 cases.
 */
@Serializable
data class ErrorResponse(
    val error: String,
)
