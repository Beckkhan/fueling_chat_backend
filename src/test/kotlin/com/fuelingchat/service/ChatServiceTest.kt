package com.fuelingchat.service

import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.executor.clients.LLMClientException
import com.fuelingchat.infra.llm.ChatMessage
import com.fuelingchat.infra.llm.LlmClient
import com.fuelingchat.service.tool.GetFuelingInfoTool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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

/**
 * Unit tests for one chat turn — MockK fake LlmClient, real DialogHistory on a @TempDir journal,
 * mocked GetFuelingInfoTool. No network, no DB.
 */
class ChatServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var localClient: LlmClient
    private lateinit var remoteClient: LlmClient
    private lateinit var history: DialogHistory
    private lateinit var tool: GetFuelingInfoTool

    @BeforeEach
    fun setUp() {
        localClient = mockk()
        remoteClient = mockk()
        history = DialogHistory(tempDir.resolve("dialog-history.jsonl"), 20)
        tool = mockk(relaxed = true)
    }

    private fun newService(historyLimit: Int = 20): DefaultChatService =
        DefaultChatService(
            history = history,
            historyLimit = historyLimit,
            router = ProviderRouter(localClient, remoteClient),
            getFuelingInfoTool = tool,
        )

    @Test
    fun systemPromptCarriesAttachedFuelingIdLineWhenPresent() {
        val systemPrompt = slot<String>()
        coEvery { localClient.run(capture(systemPrompt), any(), any(), any()) } returns "summary"

        runBlocking { newService().chat("what about F-10001?", "F-10001", null) }

        assertTrue(
            systemPrompt.captured.contains(
                "attached fueling_id: F-10001 — use exactly this value for get_fueling_info",
            ),
        )
    }

    @Test
    fun systemPromptOmitsFuelingIdLineWhenAbsent() {
        val systemPrompt = slot<String>()
        coEvery { localClient.run(capture(systemPrompt), any(), any(), any()) } returns "summary"

        runBlocking { newService().chat("hello", null, null) }

        assertFalse(systemPrompt.captured.contains("attached fueling_id"))
    }

    @Test
    fun systemPromptInstructsLanguageOfLatestMessageAndToolOnlyFuelingAnswers() {
        val systemPrompt = slot<String>()
        coEvery { localClient.run(capture(systemPrompt), any(), any(), any()) } returns "summary"

        runBlocking { newService().chat("привет", null, null) }

        assertTrue(systemPrompt.captured.contains("language"))
        assertTrue(systemPrompt.captured.contains("get_fueling_info"))
    }

    @Test
    fun historyPassedIsLastNLimitMessagesWithCorrectRoles() {
        runBlocking {
            history.appendTurn("q1", "a1")
            history.appendTurn("q2", "a2")
            history.appendTurn("q3", "a3")
        }
        val passedHistory = slot<List<ChatMessage>>()
        coEvery { localClient.run(any(), capture(passedHistory), any(), any()) } returns "summary"

        runBlocking { newService(historyLimit = 4).chat("q4", null, null) }

        val expectedRoles =
            listOf(
                ChatMessage.ROLE_USER,
                ChatMessage.ROLE_ASSISTANT,
                ChatMessage.ROLE_USER,
                ChatMessage.ROLE_ASSISTANT,
            )
        assertEquals(expectedRoles, passedHistory.captured.map { it.role })
        assertEquals(listOf("q2", "a2", "q3", "a3"), passedHistory.captured.map { it.body })
        assertFalse(passedHistory.captured.any { it.body == "q4" })
    }

    @Test
    fun toolSetIsNeverEmptyAndCarriesGetFuelingInfoTool() {
        val passedTools = slot<List<Tool<*, *>>>()
        coEvery { localClient.run(any(), any(), any(), capture(passedTools)) } returns "summary"

        runBlocking { newService().chat("hello", null, null) }

        assertEquals(1, passedTools.captured.size)
        assertSame(tool, passedTools.captured.single())
    }

    @Test
    fun successfulTurnAppendsBothMessagesAndReturnsChatTurn() {
        coEvery { remoteClient.run(any(), any(), any(), any()) } returns "summary"

        val turn = runBlocking { newService().chat("hello", null, "remote") }

        assertEquals(ChatTurn("summary", "remote"), turn)
        val appended = history.recent().takeLast(2)
        assertEquals(listOf(ChatMessage.ROLE_USER, ChatMessage.ROLE_ASSISTANT), appended.map { it.role })
        assertEquals(listOf("hello", "summary"), appended.map { it.body })
        assertEquals(2, Files.readAllLines(tempDir.resolve("dialog-history.jsonl")).size)
        coVerify(exactly = 0) { localClient.run(any(), any(), any(), any()) }
    }

    @Test
    fun dbFailureMapsTo500DatabasesUnavailable() {
        coEvery { localClient.run(any(), any(), any(), any()) } throws mockk<ExposedSQLException>()

        val failure =
            assertThrows<ChatFailure> {
                runBlocking { newService().chat("hello", null, null) }
            }

        assertEquals(500, failure.status)
        assertEquals("Databases unavailable", failure.maskedMessage)
        assertTrue(history.recent().isEmpty())
    }

    @Test
    fun providerFailureMapsTo502ProviderUnreachable() {
        val providerError = LLMClientException("local", "boom", RuntimeException())
        coEvery { localClient.run(any(), any(), any(), any()) } throws providerError

        val failure =
            assertThrows<ChatFailure> {
                runBlocking { newService().chat("hello", null, null) }
            }

        assertEquals(502, failure.status)
        assertEquals("Provider unreachable", failure.maskedMessage)
        assertTrue(history.recent().isEmpty())
    }

    @Test
    fun toolLoopBreakMapsTo502ProviderUnreachable() {
        val toolLoopError = AIAgentException("tool loop broke", RuntimeException())
        coEvery { localClient.run(any(), any(), any(), any()) } throws toolLoopError

        val failure =
            assertThrows<ChatFailure> {
                runBlocking { newService().chat("hello", null, null) }
            }

        assertEquals(502, failure.status)
        assertEquals("Provider unreachable", failure.maskedMessage)
    }
}
