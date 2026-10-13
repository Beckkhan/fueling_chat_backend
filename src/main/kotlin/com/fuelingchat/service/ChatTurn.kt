package com.fuelingchat.service

/**
 * Successful turn result: the model's summary text plus the provider that served the turn
 * ("local" | "remote", CONTRACT-API-002).
 */
data class ChatTurn(
    val summary: String,
    val provider: String,
)
