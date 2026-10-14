package com.fuelingchat.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `ProviderRouter` (D-10): absent and `"local"` resolve to the local client, `"remote"` to the remote one,
 * every other value is rejected with a masked `ChatFailure(400, ...)`. Pure logic — no clients, no IO.
 */
class ProviderRouterTest {
    private val router = ProviderRouter()

    @Test
    fun absentProviderDefaultsToLocal() {
        assertEquals("local", router.resolve(null))
    }

    @Test
    fun explicitLocalResolvesToLocal() {
        assertEquals("local", router.resolve("local"))
    }

    @Test
    fun remoteResolvesToRemote() {
        assertEquals("remote", router.resolve("remote"))
    }

    @Test
    fun anyOtherValueIsRejectedWithAMasked400() {
        val unknownProviders = listOf("cloud", "ollama", "LOCAL", "gpt-4", "")

        unknownProviders.forEach { requested ->
            val failure = assertThrows(ChatFailure::class.java) { router.resolve(requested) }

            assertEquals(400, failure.status, "provider '$requested' must be rejected")
            assertEquals("Unknown provider", failure.maskedMessage)
            assertMasked(failure.maskedMessage)
            if (requested.isNotBlank()) {
                assertFalse(failure.maskedMessage.contains(requested), "the raw provider value leaked")
            }
        }
    }
}

/** CONTRACT-API-001: a masked message carries no raw SQL, credentials or provider internals. */
private fun assertMasked(message: String) {
    assertTrue(message.isNotBlank(), "the masked message must not be blank")
    listOf("Exception", "kotlin.", "java.", "io.ktor", "ai.koog", "org.jetbrains", "SELECT").forEach {
        assertFalse(message.contains(it), "the masked message leaked '$it': $message")
    }
}
