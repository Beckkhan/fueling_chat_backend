package com.fuelingchat.service

import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import com.fuelingchat.infra.llm.ChatMessage
import com.fuelingchat.infra.llm.LlmClient
import com.fuelingchat.service.tool.GetFuelingInfoTool
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.nio.file.Path

class ChatServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun injectsAttachedFuelingIdLineWhenFuelingIdPresent() =
        runBlocking {
            val captured = CapturedRun()
            val service = service(capturedClient(captured))

            service.chat("how much fuel did I get", "F-10001", "local")

            assertTrue(captured.prompt.captured.contains(ATTACHED_FUELING_ID_LINE))
            assertTrue(captured.prompt.captured.contains("language of the user's latest message"))
        }

    @Test
    fun omitsAttachedFuelingIdLineWhenFuelingIdAbsent() =
        runBlocking {
            val captured = CapturedRun()
            val service = service(capturedClient(captured))

            service.chat("what did I fuel on monday", null, "local")

            assertFalse(captured.prompt.captured.contains("attached fueling_id"))
            assertTrue(captured.prompt.captured.contains("get_fueling_info"))
        }

    @Test
    fun passesOnlyTheLastHistoryLimitMessagesWithRoles() =
        runBlocking {
            val captured = CapturedRun()
            val history = dialogHistory()
            repeat(3) { index -> history.appendTurn("question $index", "answer $index") }
            val service = service(capturedClient(captured), history = history, historyLimit = 4)

            service.chat("follow up", "F-10001", "local")

            assertEquals(
                listOf(
                    ChatMessage("user", "question 1", 1L),
                    ChatMessage("assistant", "answer 1", 1L),
                    ChatMessage("user", "question 2", 1L),
                    ChatMessage("assistant", "answer 2", 1L),
                ),
                captured.history.captured,
            )
            assertEquals("follow up", captured.user.captured)
        }

    @Test
    fun passesExactlyTheFuelingInfoToolNeverEmpty() =
        runBlocking {
            val captured = CapturedRun()
            val tool = mockk<GetFuelingInfoTool>()
            val service = service(capturedClient(captured), tool = tool)

            service.chat("show fueling", null, "local")

            assertEquals(1, captured.tools.captured.size)
            assertSame(tool, captured.tools.captured.single())
        }

    @Test
    fun appendsBothMessagesAndReturnsTurnWithEchoedProvider() =
        runBlocking {
            val remote = mockk<LlmClient>()
            coEvery { remote.run(any(), any(), any(), any()) } returns "the answer"
            val history = dialogHistory()
            val service = service(remoteClient = remote, history = history)

            val turn = service.chat("hello there", "F-10001", "remote")

            assertEquals(ChatTurn("the answer", "remote"), turn)
            assertEquals(
                listOf(
                    ChatMessage("user", "hello there", 1L),
                    ChatMessage("assistant", "the answer", 1L),
                ),
                history.recent(10),
            )
        }

    @Test
    fun dbFailureMapsToMasked500() =
        runBlocking {
            val client = mockk<LlmClient>()
            coEvery { client.run(any(), any(), any(), any()) } throws mockk<ExposedSQLException>()
            val history = dialogHistory()
            val service = service(client, history = history)

            val failure = failureOf(service)

            assertEquals(ChatFailure.DB_FAILURE, failure.status)
            assertEquals("Databases unavailable", failure.maskedMessage)
            assertTrue(history.recent(10).isEmpty())
        }

    @Test
    fun llmClientFailureMapsToMasked502() =
        runBlocking {
            val service = service(failingClient(mockk<LLMClientException>()))

            assertProviderUnreachable(service)
        }

    @Test
    fun koogHttpFailureMapsToMasked502() =
        runBlocking {
            val service = service(failingClient(mockk<KoogHttpClientException>()))

            assertProviderUnreachable(service)
        }

    @Test
    fun toolLoopBreakMapsToMasked502() =
        runBlocking {
            val service = service(failingClient(mockk<AIAgentException>()))

            assertProviderUnreachable(service)
        }

    @Test
    fun unclassifiedExceptionPropagatesUnmasked() =
        runBlocking {
            val client = mockk<LlmClient>()
            coEvery { client.run(any(), any(), any(), any()) } throws IllegalStateException("boom")
            val service = service(client)

            val propagated: IllegalStateException? =
                try {
                    service.chat("hello", null, "local")
                    null
                } catch (error: IllegalStateException) {
                    error
                }

            assertNotNull(propagated)
        }

    private suspend fun assertProviderUnreachable(service: ChatService) {
        val failure = failureOf(service)

        assertEquals(ChatFailure.PROVIDER_FAILURE, failure.status)
        assertEquals("Provider unreachable", failure.maskedMessage)
    }

    private suspend fun failureOf(service: ChatService): ChatFailure =
        try {
            service.chat("hello", null, "local")
            fail("expected a ChatFailure")
        } catch (error: ChatFailure) {
            error
        }

    private fun service(
        localClient: LlmClient = mockk(),
        remoteClient: LlmClient = mockk(),
        history: DialogHistory = dialogHistory(),
        tool: GetFuelingInfoTool = mockk(),
        historyLimit: Int = 20,
    ): ChatService = ChatServiceImpl(history, ProviderRouter(testDi(localClient, remoteClient)), tool, historyLimit)

    private fun dialogHistory(): DialogHistory =
        DialogHistory(tempDir.resolve("dialog-${System.nanoTime()}.jsonl")) { 1L }

    private fun capturedClient(captured: CapturedRun): LlmClient {
        val client = mockk<LlmClient>()
        coEvery {
            client.run(
                capture(captured.prompt),
                capture(captured.history),
                capture(captured.user),
                capture(captured.tools),
            )
        } returns "answer"
        return client
    }

    private fun failingClient(error: Exception): LlmClient {
        val client = mockk<LlmClient>()
        coEvery { client.run(any(), any(), any(), any()) } throws error
        return client
    }

    private fun testDi(
        local: LlmClient,
        remote: LlmClient,
    ): DI =
        DI {
            bind<LlmClient>(tag = "local") with singleton { local }
            bind<LlmClient>(tag = "remote") with singleton { remote }
        }

    private class CapturedRun {
        val prompt = slot<String>()
        val history = slot<List<ChatMessage>>()
        val user = slot<String>()
        val tools = slot<List<Tool<*, *>>>()
    }
}

private const val ATTACHED_FUELING_ID_LINE =
    "attached fueling_id: F-10001 — use exactly this value for get_fueling_info"
