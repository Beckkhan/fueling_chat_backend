package com.fuelingchat.service

import com.fuelingchat.infra.llm.ChatMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Write-through dialog history (D-1, D-11).
 *
 * The in-memory cache is the only read source for LLM context; the `.jsonl` journal is an append-only
 * copy that is truncated at construction, so a restart clears the dialog context.
 */
class DialogHistory(
    private val journalFile: Path,
    private val historyLimit: Int,
) {
    private val json = Json
    private val journalMutex = Mutex()
    private val cache = CopyOnWriteArrayList<ChatMessage>()

    init {
        journalFile.parent?.let { Files.createDirectories(it) }
        Files.write(journalFile, ByteArray(0))
    }

    /** Appends [message] to the cache and as one JSONL line to the journal, preserving append order. */
    suspend fun append(message: ChatMessage) {
        journalMutex.withLock {
            cache.add(message)
            Files.write(journalFile, journalLine(message), StandardOpenOption.APPEND)
        }
    }

    /** The last [limit] cached messages, in append order. */
    fun recent(limit: Int = historyLimit): List<ChatMessage> = cache.takeLast(limit.coerceAtLeast(0))

    private fun journalLine(message: ChatMessage): ByteArray =
        (json.encodeToString(ChatMessage.serializer(), message) + "\n").toByteArray(StandardCharsets.UTF_8)
}
