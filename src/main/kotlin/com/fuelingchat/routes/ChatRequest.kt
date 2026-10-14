package com.fuelingchat.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** CONTRACT-API-001 request body: `prompt` plus the optional `fueling_id` and `provider`. */
@Serializable
data class ChatRequest(
    val prompt: String,
    @SerialName("fueling_id")
    val fuelingId: String? = null,
    val provider: String? = null,
)
