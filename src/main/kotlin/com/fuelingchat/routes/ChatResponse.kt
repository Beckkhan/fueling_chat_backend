package com.fuelingchat.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val summary: String,
    val provider: String,
    @SerialName("elapsed_ms") val elapsedMs: Long,
)

@Serializable
data class ErrorResponse(
    val error: String,
)
