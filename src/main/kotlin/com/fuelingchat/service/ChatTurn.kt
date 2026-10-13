package com.fuelingchat.service

/**
 * Result of one chat turn (source data for CONTRACT-API-002): the model's summary plus the
 * provider that served the turn. elapsed_ms is the route's concern (T11), not carried here.
 */
data class ChatTurn(
    val summary: String,
    val provider: String,
)
