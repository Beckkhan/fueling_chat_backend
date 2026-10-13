package com.fuelingchat.service

/**
 * Failure classes the route maps to HTTP statuses (CONTRACT-API-002):
 * DATABASE -> 500 (any of the four DBs fails), PROVIDER -> 502 (provider unreachable / tool loop breaks).
 */
enum class ChatFailureStatus {
    DATABASE,
    PROVIDER,
}

/**
 * Service failure carrying a masked message — safe to serialize into {"error": ...}.
 * Never carries SQL, credentials or raw provider errors.
 */
data class ChatFailure(
    val status: ChatFailureStatus,
    val maskedMessage: String,
) : Exception(maskedMessage)
