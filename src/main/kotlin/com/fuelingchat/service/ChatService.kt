package com.fuelingchat.service

/**
 * Seam between the route (T11) and the service layer (T12). Declared here so the route has
 * something to call; carries no business logic. One suspend entry point per chat turn returning
 * ChatTurn; failures are thrown as ChatFailure (CONTRACT-API-001 error table).
 * T12 implements this interface and replaces the placeholder binding in ServiceModule.
 */
interface ChatService {
    // TODO: implement in T12 — orchestrate one turn: system prompt (D-6/D-9), dialog history (D-1),
    //       never-empty tool set (D-8), provider routing with absent → local default (D-10);
    //       return ChatTurn(summary, provider); throw ChatFailure per CONTRACT-SERVICE-001.
    suspend fun chat(
        prompt: String,
        fuelingId: String?,
        provider: String?,
    ): ChatTurn
}
