package com.fuelingchat.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CONTRACT-API-001 request body, field-for-field: {prompt, fueling_id?, provider?}.
 * `provider` absent -> "local" (D-10), applied via [effectiveProvider].
 */
@Serializable
data class ChatRequest(
    val prompt: String,
    @SerialName("fueling_id") val fuelingId: String? = null,
    val provider: String? = null,
)

/**
 * Providers allowed by CONTRACT-API-001; any other value -> 400.
 */
val ALLOWED_PROVIDERS: Set<String> = setOf("local", "remote")

/**
 * Return the masked 400 error message when the request is invalid, else null.
 *  - `prompt` blank after trim -> masked message (never echo raw request content)
 *  - `provider` present and not in ALLOWED_PROVIDERS -> masked message
 *  - `fuelingId` is never rejected here (optional, no format constraint per CONTRACT-API-001)
 */
fun ChatRequest.validationError(): String? =
    when {
        prompt.isBlank() -> "Prompt must not be blank"
        provider != null && provider !in ALLOWED_PROVIDERS -> "Unsupported provider"
        else -> null
    }

/**
 * Return the provider that will serve the turn: provider ?: "local" (D-10).
 */
fun ChatRequest.effectiveProvider(): String = provider ?: "local"
