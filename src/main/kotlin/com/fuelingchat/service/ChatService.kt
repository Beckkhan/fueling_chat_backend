package com.fuelingchat.service

/**
 * Request for one chat turn. The route normalizes before delegating:
 * `provider` is always the effective provider ("local" default, D-10), `fuelingId` may be null.
 */
data class ChatServiceRequest(
    val prompt: String,
    val fuelingId: String?,
    val provider: String,
)

/**
 * Seam the /chat route consumes (design §2: the route holds no business logic).
 * One suspend entry point per turn; T12 replaces the placeholder DI binding with the
 * real implementation (Koog agent + pinned LlmClient + fueling tool, D-8).
 */
interface ChatService {
    /**
     * TODO(T12): run one chat turn against the pinned LlmClient seam and the fueling tool;
     *  return the model's summary and the provider that served the turn.
     *  Failures surface as ChatFailure — never as raw provider/DB exceptions.
     */
    suspend fun run(request: ChatServiceRequest): ChatTurn
}
