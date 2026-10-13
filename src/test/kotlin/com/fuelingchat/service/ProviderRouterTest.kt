package com.fuelingchat.service

import com.fuelingchat.infra.llm.LlmClient
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * D-10 routing unit tests: the two named bindings are MockK-mocked LlmClients (identity is
 * all the router cares about); no network, no DB.
 */
class ProviderRouterTest {
    private val localClient: LlmClient = mockk()
    private val remoteClient: LlmClient = mockk()
    private val router = ProviderRouter(local = localClient, remote = remoteClient)

    @Test
    fun localProviderResolved() {
        val selection = router.resolve(ProviderRouter.LOCAL_PROVIDER)

        assertEquals(ProviderRouter.LOCAL_PROVIDER, selection.name)
        assertSame(localClient, selection.client)
    }

    @Test
    fun remoteProviderResolved() {
        val selection = router.resolve(ProviderRouter.REMOTE_PROVIDER)

        assertEquals(ProviderRouter.REMOTE_PROVIDER, selection.name)
        assertSame(remoteClient, selection.client)
    }

    @Test
    fun absentProviderDefaultsToLocal() {
        val selection = router.resolve(null)

        assertEquals(ProviderRouter.LOCAL_PROVIDER, selection.name)
        assertSame(localClient, selection.client)
    }

    @Test
    fun unknownProviderThrowsMaskedChatFailure() {
        val failure = assertThrows<ChatFailure> { router.resolve("openai") }

        assertEquals(ChatFailureStatus.PROVIDER, failure.status)
        assertEquals(ProviderRouter.MASKED_INVALID_PROVIDER, failure.maskedMessage)
        assertFalse(failure.maskedMessage.contains("openai"))
    }
}
