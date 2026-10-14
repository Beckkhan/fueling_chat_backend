package com.fuelingchat.service

/**
 * Failed turn mapped to an HTTP [status] carrying a [maskedMessage] safe for response bodies and logs
 * (CONTRACT-API-001: no raw SQL, credentials, or provider text).
 */
class ChatFailure(
    val status: Int,
    val maskedMessage: String,
) : Exception(maskedMessage)
