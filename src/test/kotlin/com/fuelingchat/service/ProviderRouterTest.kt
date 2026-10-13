package com.fuelingchat.service

import com.fuelingchat.infra.llm.LlmClient
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProviderRouterTest {
    @Test
    fun localRoutesToLocalBinding() {
        val local = mockk<LlmClient>()
        val remote = mockk<LlmClient>()

        val selection = ProviderRouter(local, remote).route("local")

        assertEquals("local", selection.name)
        assertSame(local, selection.client)
    }

    @Test
    fun remoteRoutesToRemoteBinding() {
        val local = mockk<LlmClient>()
        val remote = mockk<LlmClient>()

        val selection = ProviderRouter(local, remote).route("remote")

        assertEquals("remote", selection.name)
        assertSame(remote, selection.client)
    }

    @Test
    fun absentProviderDefaultsToLocal() {
        val local = mockk<LlmClient>()
        val remote = mockk<LlmClient>()

        val selection = ProviderRouter(local, remote).route(null)

        assertEquals("local", selection.name)
        assertSame(local, selection.client)
    }

    @Test
    fun unknownProviderThrowsMasked400() {
        val router = ProviderRouter(mockk(), mockk())

        val failure = assertThrows<ChatFailure> { router.route("banana") }

        assertEquals(400, failure.status)
        assertEquals("Invalid provider", failure.maskedMessage)
    }
}
