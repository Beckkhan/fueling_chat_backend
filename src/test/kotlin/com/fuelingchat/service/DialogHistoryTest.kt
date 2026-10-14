package com.fuelingchat.service

import com.fuelingchat.infra.llm.ChatMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

private const val HISTORY_LIMIT = 20

/**
 * `DialogHistory` (D-1, D-11): the in-memory cache is the only read source, the `.jsonl` journal is the
 * append-only write-through copy truncated at construction. Pure memory + file — no DB, no network.
 */
class DialogHistoryTest {
    @TempDir
    lateinit var tempDir: Path

    private val journalFile: Path
        get() = tempDir.resolve("data/dialog-history.jsonl")

    @Test
    fun recentReturnsAppendedMessagesInOrderWithTheirRoles() {
        val history = DialogHistory(journalFile, HISTORY_LIMIT)
        val appended =
            listOf(
                ChatMessage("user", "where is my fueling?", 1_000L),
                ChatMessage("assistant", "Please share the fueling id.", 1_001L),
                ChatMessage("user", "F-10001", 1_002L),
            )

        runBlocking { appended.forEach { history.append(it) } }

        assertEquals(appended, history.recent())
        assertEquals(listOf("user", "assistant", "user"), history.recent().map { it.role })
    }

    @Test
    fun journalAfterOneTurnHoldsTwoJsonlLinesMatchingTheCache() {
        val history = DialogHistory(journalFile, HISTORY_LIMIT)
        val user = ChatMessage("user", "what did fueling F-10001 cost?", 1_000L)
        val assistant = ChatMessage("assistant", "1500.00 RUB", 1_001L)

        runBlocking {
            history.append(user)
            history.append(assistant)
        }

        val lines = Files.readAllLines(journalFile)
        assertEquals(2, lines.size, "one JSONL line per message")
        val journaled = lines.map { Json.decodeFromString(ChatMessage.serializer(), it) }
        assertEquals(listOf(user, assistant), journaled)
        assertEquals(history.recent(), journaled, "the journal must match the cache")
    }

    @Test
    fun aSecondConstructionTruncatesTheJournalAndClearsTheCache() {
        val first = DialogHistory(journalFile, HISTORY_LIMIT)
        runBlocking {
            first.append(ChatMessage("user", "hello", 1_000L))
            first.append(ChatMessage("assistant", "hi", 1_001L))
        }
        assertTrue(Files.size(journalFile) > 0L, "the first instance must have written the journal")

        val restarted = DialogHistory(journalFile, HISTORY_LIMIT)

        assertEquals(0L, Files.size(journalFile), "a restart truncates the journal")
        assertTrue(restarted.recent().isEmpty(), "a restart clears the dialog context")
    }

    @Test
    fun constructionCreatesTheJournalAndItsMissingParentDirectories() {
        val nested = tempDir.resolve("data/nested/dialog-history.jsonl")
        assertFalse(Files.exists(nested), "precondition: the journal path does not exist yet")

        DialogHistory(nested, HISTORY_LIMIT)

        assertTrue(Files.exists(nested))
        assertEquals(0L, Files.size(nested))
    }

    @Test
    fun recentCapsAtTheLastMessagesUsingHistoryLimitByDefault() {
        val history = DialogHistory(journalFile, historyLimit = 3)
        val appended = (1..6).map { ChatMessage("user", "message-$it", it.toLong()) }

        runBlocking { appended.forEach { history.append(it) } }

        assertEquals(listOf("message-4", "message-5", "message-6"), history.recent().map { it.body })
        assertEquals(listOf("message-5", "message-6"), history.recent(2).map { it.body })
        assertEquals(appended, history.recent(10), "a limit larger than the cache returns everything")
        assertEquals(6, Files.readAllLines(journalFile).size, "the journal is append-only, never capped")
    }
}
