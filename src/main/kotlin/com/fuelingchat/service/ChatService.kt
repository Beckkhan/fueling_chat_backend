package com.fuelingchat.service

interface ChatService {
    suspend fun chat(
        prompt: String,
        fuelingId: String?,
        provider: String,
    ): ChatTurn
}
