package com.fuelingchat.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CONTRACT-API-001 request body — fields match the contract field-for-field:
 * {prompt, fueling_id?, provider?}. Validation rules live in ChatRoutes; this DTO stays a
 * pure serialization carrier.
 */
@Serializable
data class ChatRequest(
    val prompt: String,
    @SerialName("fueling_id")
    val fuelingId: String? = null,
    val provider: String? = null,
)
