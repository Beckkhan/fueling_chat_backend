package com.fuelingchat.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** CONTRACT-API-002 success body: exactly the three frozen fields (G-6). */
@Serializable
data class ChatResponse(
    val summary: String,
    val provider: String,
    @SerialName("elapsed_ms")
    val elapsedMs: Int,
)

/** Masked body of every non-200 response (CONTRACT-API-001). */
@Serializable
data class ErrorBody(
    val error: String,
)
