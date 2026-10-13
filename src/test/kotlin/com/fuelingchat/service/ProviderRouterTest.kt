package com.fuelingchat.service

import com.fuelingchat.infra.llm.LlmClient
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class ProviderRouterTest {
    @Test
    fun resolvesNamedBindingsAndEchoesProviderName() {
        val local = mockk<LlmClient>()
        val remote = mockk<LlmClient>()
        val router = ProviderRouter(testDi(local, remote))

        val localSelection = router.resolve("local")
        assertEquals("local", localSelection.name)
        assertSame(local, localSelection.client)

        val remoteSelection = router.resolve("remote")
        assertEquals("remote", remoteSelection.name)
        assertSame(remote, remoteSelection.client)
    }

    @Test
    fun absentProviderDefaultsToLocal() {
        val local = mockk<LlmClient>()
        val router = ProviderRouter(testDi(local, mockk()))

        val selection = router.resolve(null)

        assertEquals("local", selection.name)
        assertSame(local, selection.client)
    }

    @Test
    fun unknownProviderFailsWithMasked400() {
        val router = ProviderRouter(testDi(mockk(), mockk()))

        val failure = assertThrows(ChatFailure::class.java) { router.resolve("cloud") }

        assertEquals(ChatFailure.BAD_PROVIDER, failure.status)
        assertEquals("provider must be \"local\" or \"remote\"", failure.maskedMessage)
        assertFalse(failure.maskedMessage.contains("cloud"))
    }

    private fun testDi(
        local: LlmClient,
        remote: LlmClient,
    ): DI =
        DI {
            bind<LlmClient>(tag = "local") with singleton { local }
            bind<LlmClient>(tag = "remote") with singleton { remote }
        }
}
