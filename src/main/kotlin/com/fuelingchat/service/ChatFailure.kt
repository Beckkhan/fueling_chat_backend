package com.fuelingchat.service

/**
 * CONTRACT-API-001 error carrier thrown by ChatService (and by route-level validation for 400s):
 * an HTTP status plus an already-masked message. The optional cause stays out of any response
 * body — it is for logs only, and must be logged through the masking pipeline.
 */
class ChatFailure(
    val status: Int,
    val maskedMessage: String,
    cause: Throwable? = null,
) : Exception(maskedMessage, cause) {
    companion object {
        fun databaseFailure(cause: Throwable? = null): ChatFailure = ChatFailure(500, "Databases unavailable", cause)

        fun providerFailure(cause: Throwable? = null): ChatFailure = ChatFailure(502, "Provider unreachable", cause)
    }
}
