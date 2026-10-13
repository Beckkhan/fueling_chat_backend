package com.fuelingchat.service

import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import com.fuelingchat.infra.llm.ChatMessage
import com.fuelingchat.infra.llm.LlmClient
import com.fuelingchat.service.tool.GetFuelingInfoTool
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

private const val JOURNAL_FILE_NAME = "history.jsonl"
private const val HISTORY_LIMIT = 20
private const val D9_INJECTION_LINE =
    "attached fueling_id: F-10001 — use exactly this value for get_fueling_info"

/**
 * CONTRACT-SERVICE-001 unit tests: one turn end-to-end at the service layer with a RECORDING
 * fake LlmClient (pinned below) so assertions pin the exact arguments passed to run —
 * systemPrompt (D-9 line), history contents, userMessage, never-empty tool set — not merely
 * "something was called". Exception instances for the classification tests are MockK-mocked.
 * No network, no DB (GetFuelingInfoTool is MockK-mocked too).
 */
class ChatServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var history: DialogHistory
    private lateinit var local: RecordingLlmClient
    private lateinit var remote: RecordingLlmClient
    private lateinit var tool: GetFuelingInfoTool
    private lateinit var service: ChatServiceImpl

    @BeforeEach
    fun setUp() {
        history = DialogHistory(tempDir.resolve(JOURNAL_FILE_NAME), historyLimit = HISTORY_LIMIT)
        local = RecordingLlmClient()
        remote = RecordingLlmClient()
        tool = mockk<GetFuelingInfoTool>()
        service =
            ChatServiceImpl(
                history = history,
                historyLimit = HISTORY_LIMIT,
                router = ProviderRouter(local = local, remote = remote),
                getFuelingInfoTool = tool,
            )
    }

    private fun request(
        prompt: String = "question",
        fuelingId: String? = null,
        provider: String = ProviderRouter.LOCAL_PROVIDER,
    ): ChatServiceRequest = ChatServiceRequest(prompt = prompt, fuelingId = fuelingId, provider = provider)

    @Test
    fun attachedFuelingIdInjectedIntoSystemPromptWhenPresent() =
        runBlocking {
            service.run(request(prompt = "what happened to F-10001?", fuelingId = "F-10001"))

            val systemPrompt = local.calls.single().systemPrompt
            assertTrue(systemPrompt.contains(D9_INJECTION_LINE), systemPrompt)
        }

    @Test
    fun attachedFuelingIdLineAbsentWhenFuelingIdNull() =
        runBlocking {
            service.run(request(prompt = "what happened?", fuelingId = null))

            val systemPrompt = local.calls.single().systemPrompt
            assertFalse(systemPrompt.contains("attached fueling_id"), systemPrompt)
        }

    @Test
    fun languageInstructionPresentInSystemPrompt() =
        runBlocking {
            service.run(request(prompt = "сколько стоит топливо?"))

            val systemPrompt =
                local.calls
                    .single()
                    .systemPrompt
                    .lowercase()
            assertTrue(systemPrompt.contains("language"), systemPrompt)
            assertTrue(systemPrompt.contains("user"), systemPrompt)
        }

    @Test
    fun historyPassedIsLastLimitMessagesExcludingCurrentTurn() =
        runBlocking {
            repeat(HISTORY_LIMIT + 2) { index -> history.appendTurn("seed-$index", "reply-$index") }

            val expected = history.recent(HISTORY_LIMIT)
            service.run(request(prompt = "current question"))

            val recorded = local.calls.single().history
            assertEquals(HISTORY_LIMIT, recorded.size)
            assertEquals(expected, recorded)
            assertEquals(
                listOf(ChatMessage.ROLE_USER, ChatMessage.ROLE_ASSISTANT),
                recorded.takeLast(2).map { it.role },
            )
            assertFalse(recorded.any { it.body == "current question" })
        }

    @Test
    fun toolSetPassedIsNeverEmptyAndContainsFuelingTool() =
        runBlocking {
            service.run(request())

            val tools = local.calls.single().tools
            assertEquals(1, tools.size, "tool set must never be empty (D-8)")
            assertSame(tool, tools.single())
        }

    @Test
    fun successfulRunAppendsTurnAndReturnsChatTurn() =
        runBlocking {
            local.summaryResult = "F-10001 completed at Tverskaya 7."

            val turn = service.run(request(prompt = "what happened to F-10001?"))

            assertEquals("F-10001 completed at Tverskaya 7.", turn.summary)
            assertEquals(ProviderRouter.LOCAL_PROVIDER, turn.provider)

            val appended = history.recent()
            val bodies = appended.map { it.body }
            assertEquals(listOf("what happened to F-10001?", "F-10001 completed at Tverskaya 7."), bodies)
            assertEquals(listOf(ChatMessage.ROLE_USER, ChatMessage.ROLE_ASSISTANT), appended.map { it.role })
            assertEquals(2, Files.readAllLines(tempDir.resolve(JOURNAL_FILE_NAME)).size)
        }

    @Test
    fun userMessageAndProviderPassThrough() =
        runBlocking {
            val localTurn = service.run(request(prompt = "local prompt", provider = ProviderRouter.LOCAL_PROVIDER))
            assertEquals("local prompt", local.calls.single().userMessage)
            assertEquals(ProviderRouter.LOCAL_PROVIDER, localTurn.provider)

            val remoteTurn = service.run(request(prompt = "remote prompt", provider = ProviderRouter.REMOTE_PROVIDER))
            assertEquals("remote prompt", remote.calls.single().userMessage)
            assertEquals(ProviderRouter.REMOTE_PROVIDER, remoteTurn.provider)
            assertEquals(1, local.calls.size)
        }

    @Test
    fun exposedSqlFailureClassifiedAsDatabasesUnavailable() {
        local.failure = mockk<ExposedSQLException>()

        val failure = assertThrows<ChatFailure> { runBlocking { service.run(request()) } }

        assertEquals(ChatFailureStatus.DATABASE, failure.status)
        assertEquals(ChatServiceImpl.MASKED_DATABASES_UNAVAILABLE, failure.maskedMessage)
        assertEquals(ChatServiceImpl.MASKED_DATABASES_UNAVAILABLE, failure.message)
        val appended = runBlocking { history.recent() }
        assertTrue(appended.isEmpty())
    }

    @Test
    fun providerAndToolLoopFailuresClassifiedAsProviderUnreachable() {
        assertProviderUnreachable(mockk<LLMClientException>())
        assertProviderUnreachable(mockk<KoogHttpClientException>())
        assertProviderUnreachable(mockk<AIAgentException>())
    }

    private fun assertProviderUnreachable(cause: Exception) {
        local.failure = cause

        val failure = assertThrows<ChatFailure> { runBlocking { service.run(request()) } }

        assertEquals(ChatFailureStatus.PROVIDER, failure.status)
        assertEquals(ChatServiceImpl.MASKED_PROVIDER_UNREACHABLE, failure.maskedMessage)
    }
}

/**
 * RECORDING fake LlmClient (pinned by the T11 gate): every run() call is captured so
 * assertions pin the exact seam arguments. An optional [failure] makes run() throw for the
 * failure-classification tests. No network, no DB — the LlmClient boundary is faked/mocked.
 */
private class RecordingLlmClient : LlmClient {
    /** One captured call, field-for-field the pinned LlmClient.run parameters (spec §3). */
    data class RunCall(
        val systemPrompt: String,
        val history: List<ChatMessage>,
        val userMessage: String,
        val tools: List<Tool<*, *>>,
    )

    val calls: MutableList<RunCall> = mutableListOf()
    var summaryResult: String = "fake summary"
    var failure: Exception? = null

    override suspend fun run(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        tools: List<Tool<*, *>>,
    ): String {
        failure?.let { throw it }
        calls.add(RunCall(systemPrompt, history, userMessage, tools))
        return summaryResult
    }
}
