package com.fuelingchat.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val prompt: String,
    @SerialName("fueling_id") val fuelingId: String? = null,
    val provider: String? = null,
)
