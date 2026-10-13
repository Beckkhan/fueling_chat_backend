package com.fuelingchat.service

import com.fuelingchat.infra.llm.ChatMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DialogHistoryTest {
    @TempDir
    lateinit var tempDir: Path

    private fun journalPath(): Path = tempDir.resolve("dialog-history.jsonl")

    @Test
    fun journalGetsTwoJsonlLinesPerTurnMatchingTheCache() {
        val history = DialogHistory(journalPath(), 20)

        runBlocking { history.appendTurn("hello", "world") }

        val lines = Files.readAllLines(journalPath())
        assertEquals(2, lines.size)
        val cached = history.recent()
        assertEquals(2, cached.size)
        val userLine = Json.decodeFromString(ChatMessage.serializer(), lines[0])
        val assistantLine = Json.decodeFromString(ChatMessage.serializer(), lines[1])
        assertEquals(ChatMessage.ROLE_USER, userLine.role)
        assertEquals("hello", userLine.body)
        assertEquals(ChatMessage.ROLE_ASSISTANT, assistantLine.role)
        assertEquals("world", assistantLine.body)
        assertEquals(userLine, cached[0])
        assertEquals(assistantLine, cached[1])
        assertTrue(userLine.timestamp > 0)
        assertTrue(assistantLine.timestamp > 0)
    }

    @Test
    fun constructionTruncatesTheJournalAndStartsEmpty() {
        Files.writeString(journalPath(), "stale line one\nstale line two\n")

        val history = DialogHistory(journalPath(), 20)

        assertEquals(0L, Files.size(journalPath()))
        assertTrue(history.recent().isEmpty())
    }

    @Test
    fun recentReturnsLastNLimitMessagesOldestFirstWithCorrectRoles() {
        val history = DialogHistory(journalPath(), 20)
        runBlocking {
            history.appendTurn("q1", "a1")
            history.appendTurn("q2", "a2")
            history.appendTurn("q3", "a3")
        }

        val recent = history.recent(4)

        val expectedRoles =
            listOf(
                ChatMessage.ROLE_USER,
                ChatMessage.ROLE_ASSISTANT,
                ChatMessage.ROLE_USER,
                ChatMessage.ROLE_ASSISTANT,
            )
        assertEquals(expectedRoles, recent.map { it.role })
        assertEquals(listOf("q2", "a2", "q3", "a3"), recent.map { it.body })
    }

    @Test
    fun recentDefaultsToHistoryLimit() {
        val history = DialogHistory(journalPath(), 2)
        runBlocking {
            history.appendTurn("q1", "a1")
            history.appendTurn("q2", "a2")
            history.appendTurn("q3", "a3")
        }

        assertEquals(listOf("q3", "a3"), history.recent().map { it.body })
        assertEquals(6, history.recent(6).size)
    }

    @Test
    fun recentWithNonPositiveLimitReturnsEmpty() {
        val history = DialogHistory(journalPath(), 20)
        runBlocking { history.appendTurn("q", "a") }

        assertTrue(history.recent(0).isEmpty())
        assertTrue(history.recent(-1).isEmpty())
    }
}
