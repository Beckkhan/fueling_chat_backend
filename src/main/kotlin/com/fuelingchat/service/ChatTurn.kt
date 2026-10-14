package com.fuelingchat.service

/** Successful turn result: the masked [summary] plus the [provider] that produced it. */
data class ChatTurn(
    val summary: String,
    val provider: String,
)
