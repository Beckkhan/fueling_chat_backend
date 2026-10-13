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

    @Test
    fun appendsUserAndAssistantLinesMatchingCache() =
        runBlocking {
            val file = tempDir.resolve("dialog-history.jsonl")
            var now = 1_000L
            val history = DialogHistory(file) { now++ }

            history.appendTurn("hello", "answer")

            val expected =
                listOf(
                    ChatMessage("user", "hello", 1_000L),
                    ChatMessage("assistant", "answer", 1_001L),
                )
            assertEquals(expected, history.recent(10))
            assertEquals(expected, journalMessages(file))
        }

    @Test
    fun restartTruncatesJournalAndClearsCache() =
        runBlocking {
            val file = tempDir.resolve("dialog-history.jsonl")
            val firstRun = DialogHistory(file) { 1L }
            firstRun.appendTurn("hello", "answer")
            assertTrue(journalMessages(file).isNotEmpty())

            val restarted = DialogHistory(file) { 1L }

            assertTrue(restarted.recent(20).isEmpty())
            assertTrue(Files.readAllLines(file).isEmpty())
        }

    @Test
    fun recentReturnsOnlyTheLastLimitMessagesInOrder() =
        runBlocking {
            val file = tempDir.resolve("dialog-history.jsonl")
            val history = DialogHistory(file) { 1L }
            repeat(3) { index -> history.appendTurn("question $index", "answer $index") }

            assertEquals(
                listOf(
                    ChatMessage("user", "question 2", 1L),
                    ChatMessage("assistant", "answer 2", 1L),
                ),
                history.recent(2),
            )
        }

    private fun journalMessages(file: Path): List<ChatMessage> =
        Files
            .readAllLines(file)
            .map { line -> Json.decodeFromString<ChatMessage>(line) }
}
