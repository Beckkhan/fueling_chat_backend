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

private const val JOURNAL_FILE_NAME = "history.jsonl"
private const val DEFAULT_LIMIT = 20

/**
 * CONTRACT-HISTORY-001 unit tests (D-1, D-11): journal + cache semantics.
 * No network, no DB — @TempDir journal files only. kotlinx-coroutines runBlocking
 * drives the suspend members.
 */
class DialogHistoryTest {
    @TempDir
    lateinit var tempDir: Path

    private fun dialogHistory(limit: Int = DEFAULT_LIMIT): DialogHistory =
        DialogHistory(tempDir.resolve(JOURNAL_FILE_NAME), historyLimit = limit)

    @Test
    fun appendTurnWritesTwoJsonlLinesMatchingCache() =
        runBlocking {
            val journal = tempDir.resolve(JOURNAL_FILE_NAME)
            val history = dialogHistory()

            history.appendTurn(userMessage = "q", assistantSummary = "a")

            val decoded = Files.readAllLines(journal).map { Json.decodeFromString(ChatMessage.serializer(), it) }
            assertEquals(2, decoded.size)
            assertEquals(listOf(ChatMessage.ROLE_USER, ChatMessage.ROLE_ASSISTANT), decoded.map { it.role })
            assertEquals(listOf("q", "a"), decoded.map { it.body })
            assertEquals(decoded[0].timestamp, decoded[1].timestamp)
            assertEquals(decoded, history.recent(2))
        }

    @Test
    fun constructionTruncatesJournalAndCacheStartsEmpty() =
        runBlocking {
            val journal = tempDir.resolve(JOURNAL_FILE_NAME)
            Files.writeString(journal, "stale line one\nstale line two\n")

            val history = DialogHistory(journal, historyLimit = DEFAULT_LIMIT)

            assertEquals("", Files.readString(journal))
            assertTrue(history.recent().isEmpty())
        }

    @Test
    fun recentReturnsLastLimitMessagesOldestFirst() =
        runBlocking {
            val history = dialogHistory()

            history.appendTurn("q1", "a1")
            history.appendTurn("q2", "a2")
            history.appendTurn("q3", "a3")

            val recent = history.recent(limit = 2)

            assertEquals(2, recent.size)
            assertEquals(listOf(ChatMessage.ROLE_USER, ChatMessage.ROLE_ASSISTANT), recent.map { it.role })
            assertEquals(listOf("q3", "a3"), recent.map { it.body })
        }

    @Test
    fun recentDefaultsToHistoryLimit() =
        runBlocking {
            val limit = 3
            val history = dialogHistory(limit = limit)

            repeat(limit + 1) { index -> history.appendTurn("q$index", "a$index") }

            val recent = history.recent()

            assertEquals(limit, recent.size)
            assertEquals(listOf("a2", "q3", "a3"), recent.map { it.body })
        }
}
