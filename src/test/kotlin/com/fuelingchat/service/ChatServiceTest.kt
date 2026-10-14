package com.fuelingchat.service

import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.Tool
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import com.fuelingchat.infra.db.DbPools
import com.fuelingchat.infra.llm.ChatMessage
import com.fuelingchat.infra.llm.LlmClient
import com.fuelingchat.service.tool.GetFuelingInfoTool
import com.fuelingchat.service.tool.ToolDescriptorLoader
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

private const val HISTORY_LIMIT = 20
private const val FUELING_ID = "F-10001"
private const val PROMPT = "what did fueling F-10001 cost?"
private const val LOCAL_SUMMARY = "local summary"
private const val REMOTE_SUMMARY = "remote summary"

/**
 * `ChatServiceImpl` (CONTRACT-SERVICE-001) against MockK `LlmClient` fakes: no network and no DB — the real
 * [GetFuelingInfoTool] is built with a mocked [DbPools] and never executes a transaction, and no test
 * resolves the application DI graph.
 */
class ChatServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private val journalFile: Path
        get() = tempDir.resolve("dialog-history.jsonl")

    @Test
    fun attachedFuelingIdIsInjectedIntoTheSystemPrompt() {
        val fixture = fixture()

        val turn = runBlocking { fixture.service.runTurn(PROMPT, FUELING_ID, null) }

        assertEquals(LOCAL_SUMMARY, turn.summary)
        val systemPrompt = fixture.local.lastSystemPrompt
        assertTrue(
            systemPrompt.contains("attached fueling_id: $FUELING_ID"),
            "the D-9 line must be injected when fueling_id is present: $systemPrompt",
        )
        assertTrue(systemPrompt.contains("get_fueling_info"), "the prompt must name the tool: $systemPrompt")
    }

    @Test
    fun absentFuelingIdLeavesNoAttachedLineOnTheSystemPrompt() {
        val fixture = fixture()

        runBlocking { fixture.service.runTurn(PROMPT, null, null) }

        val systemPrompt = fixture.local.lastSystemPrompt
        assertFalse(
            systemPrompt.contains("attached fueling_id"),
            "no D-9 line may appear without a fueling_id: $systemPrompt",
        )
    }

    @Test
    fun theClientReceivesTheLastCachedMessagesInOrderWithTheirRoles() {
        val fixture = fixture(historyLimit = 2)
        val seeded =
            listOf(
                ChatMessage("user", "earlier question", 1_000L),
                ChatMessage("assistant", "earlier answer", 1_001L),
                ChatMessage("user", "another question", 1_002L),
            )
        runBlocking { seeded.forEach { fixture.history.append(it) } }

        runBlocking { fixture.service.runTurn(PROMPT, null, null) }

        val passed = fixture.local.lastHistory
        assertEquals(2, passed.size, "the configured historyLimit caps the context")
        assertEquals(seeded.takeLast(2), passed)
        assertEquals(listOf("assistant", "user"), passed.map { it.role })
    }

    @Test
    fun absentOrLocalProviderRunsTheLocalClientAndEchoesLocal() {
        listOf<String?>(null, "local").forEach { requested ->
            val fixture = fixture()

            val turn = runBlocking { fixture.service.runTurn(PROMPT, FUELING_ID, requested) }

            assertEquals(LOCAL_SUMMARY, turn.summary)
            assertEquals("local", turn.provider, "provider '$requested' must resolve to local")
            assertEquals(1, fixture.local.callCount)
            assertEquals(0, fixture.remote.callCount, "the remote client must not be called")
        }
    }

    @Test
    fun remoteProviderRunsTheRemoteClientAndEchoesRemote() {
        val fixture = fixture()

        val turn = runBlocking { fixture.service.runTurn(PROMPT, FUELING_ID, "remote") }

        assertEquals(REMOTE_SUMMARY, turn.summary)
        assertEquals("remote", turn.provider)
        assertEquals(1, fixture.remote.callCount)
        assertEquals(0, fixture.local.callCount, "the local client must not be called")
    }

    @Test
    fun theClientReceivesExactlyTheFuelingInfoToolAndNeverAnEmptySet() {
        val fixture = fixture()

        runBlocking { fixture.service.runTurn(PROMPT, FUELING_ID, null) }

        val tools = fixture.local.lastTools
        assertEquals(listOf(fixture.tool), tools)
        assertFalse(tools.isEmpty(), "D-8: the tool set passed to the client is never empty")
    }

    @Test
    fun aSuccessfulTurnJournalsTheUserAndAssistantMessagesMatchingTheCache() {
        val fixture = fixture()

        runBlocking { fixture.service.runTurn(PROMPT, FUELING_ID, null) }

        assertEquals(PROMPT, fixture.local.lastUserMessage)
        val cached = fixture.history.recent()
        assertEquals(listOf("user", "assistant"), cached.map { it.role })
        assertEquals(listOf(PROMPT, LOCAL_SUMMARY), cached.map { it.body })
        val lines = Files.readAllLines(journalFile)
        assertEquals(2, lines.size, "one JSONL line per message")
        assertEquals(cached, lines.map { Json.decodeFromString(ChatMessage.serializer(), it) })
    }

    @Test
    fun aDatabaseFailureIsMaskedAs500DatabasesUnavailable() {
        val fixture = fixture(local = ClientStub(failure = mockk<ExposedSQLException>()))

        val failure =
            assertThrows(ChatFailure::class.java) {
                runBlocking { fixture.service.runTurn(PROMPT, FUELING_ID, null) }
            }

        assertEquals(500, failure.status)
        assertEquals("Databases unavailable", failure.maskedMessage)
        assertMasked(failure.maskedMessage)
        assertTrue(fixture.history.recent().isEmpty(), "a failed turn must not append to the history")
        assertEquals(0L, Files.size(journalFile), "a failed turn must not write the journal")
    }

    @Test
    fun providerFailuresAreMaskedAs502ProviderUnreachable() {
        val failures =
            listOf(
                LLMClientException("ollama", "connection refused at http://localhost:11434/api/chat"),
                KoogHttpClientException(
                    clientName = "deepseek",
                    statusCode = 401,
                    errorBody = """{"error": "invalid api key sk-live-sentinel"}""",
                ),
                AIAgentException("tool loop failed after 10 iterations"),
            )

        failures.forEach { failure ->
            val fixture = fixture(local = ClientStub(failure = failure))

            val thrown =
                assertThrows(ChatFailure::class.java) {
                    runBlocking { fixture.service.runTurn(PROMPT, FUELING_ID, null) }
                }

            assertEquals(502, thrown.status, "a ${failure.javaClass.simpleName} must classify as 502")
            assertEquals("Provider unreachable", thrown.maskedMessage)
            assertMasked(thrown.maskedMessage)
            assertNoProviderText(thrown.maskedMessage)
            assertEquals(0L, Files.size(journalFile), "a failed turn must not write the journal")
        }
    }

    @Test
    fun anUnknownProviderFailsWith400BeforeAnyClientRuns() {
        val fixture = fixture()

        val failure =
            assertThrows(ChatFailure::class.java) {
                runBlocking { fixture.service.runTurn(PROMPT, FUELING_ID, "cloud") }
            }

        assertEquals(400, failure.status)
        assertEquals("Unknown provider", failure.maskedMessage)
        assertEquals(0, fixture.local.callCount, "no client may run for a rejected provider")
        assertEquals(0, fixture.remote.callCount)
        assertTrue(fixture.history.recent().isEmpty(), "a rejected provider must not journal a turn")
    }

    private fun fixture(
        historyLimit: Int = HISTORY_LIMIT,
        local: ClientStub = ClientStub(reply = LOCAL_SUMMARY),
        remote: ClientStub = ClientStub(reply = REMOTE_SUMMARY),
    ): Fixture {
        val history = DialogHistory(journalFile, historyLimit)
        val tool = GetFuelingInfoTool(ToolDescriptorLoader().load(), mockk<DbPools>())
        val service = ChatServiceImpl(history, ProviderRouter(), tool, local.client, remote.client)
        return Fixture(history, tool, local, remote, service)
    }
}

private class Fixture(
    val history: DialogHistory,
    val tool: GetFuelingInfoTool,
    val local: ClientStub,
    val remote: ClientStub,
    val service: ChatServiceImpl,
)

/** A MockK [LlmClient] that records every `run` argument and answers [reply] or throws [failure]. */
private class ClientStub(
    private val reply: String = "",
    private val failure: Throwable? = null,
) {
    val client: LlmClient = mockk()
    private val systemPrompts = mutableListOf<String>()
    private val histories = mutableListOf<List<ChatMessage>>()
    private val userMessages = mutableListOf<String>()
    private val toolSets = mutableListOf<List<Tool<*, *>>>()

    init {
        coEvery {
            client.run(capture(systemPrompts), capture(histories), capture(userMessages), capture(toolSets))
        } coAnswers {
            failure?.let { throw it }
            reply
        }
    }

    val callCount: Int get() = systemPrompts.size
    val lastSystemPrompt: String get() = systemPrompts.last()
    val lastHistory: List<ChatMessage> get() = histories.last()
    val lastUserMessage: String get() = userMessages.last()
    val lastTools: List<Tool<*, *>> get() = toolSets.last()
}

/** CONTRACT-API-001: a masked message carries no raw SQL, credentials or internals. */
private fun assertMasked(message: String) {
    assertTrue(message.isNotBlank(), "the masked message must not be blank")
    listOf("Exception", "kotlin.", "java.", "io.ktor", "ai.koog", "org.jetbrains", "SELECT").forEach {
        assertFalse(message.contains(it), "the masked message leaked '$it': $message")
    }
}

/** The provider's own text (endpoint, error body, credentials) must never reach a response message. */
private fun assertNoProviderText(message: String) {
    listOf("localhost", "11434", "connection refused", "tool loop failed", "sk-live-sentinel").forEach {
        assertFalse(message.contains(it), "provider text leaked into the masked message: $it")
    }
}
