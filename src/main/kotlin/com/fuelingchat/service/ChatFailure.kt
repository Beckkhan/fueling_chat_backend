package com.fuelingchat.service

class ChatFailure(
    val status: Int,
    val maskedMessage: String,
) : RuntimeException(maskedMessage) {
    companion object {
        const val DB_FAILURE = 500
        const val PROVIDER_FAILURE = 502
    }
}
