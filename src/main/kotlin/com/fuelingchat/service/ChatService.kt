package com.fuelingchat.service

/**
 * Seam between the `/chat` route and the turn implementation.
 *
 * One suspend entry point per turn. A `null` [provider] leaves the D-10 default to the implementation;
 * invalid non-null values are rejected by the route with 400 before reaching this interface.
 */
interface ChatService {
    suspend fun runTurn(
        prompt: String,
        fuelingId: String?,
        provider: String?,
    ): ChatTurn
}
