package com.fuelingchat.service

import com.fuelingchat.infra.llm.ChatMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * CONTRACT-HISTORY-001 (D-1, D-11): write-through dialog journal. The in-memory message list is
 * the ONLY read source; the .jsonl journal file is append-only persistence written per turn.
 * Construction truncates the journal and starts with an empty cache, so restarts clear context.
 */
class DialogHistory(
    private val journalFile: Path,
    private val historyLimit: Int,
) {
    private val cache: MutableList<ChatMessage> = mutableListOf()
    private val journalMutex = Mutex()
    private val journalJson = Json

    init {
        truncateJournal()
    }

    /**
     * D-11: the last `limit` messages from the in-memory cache, oldest first. Defaults to
     * historyLimit (HISTORY_LIMIT, default 20). The cache, never the file, is the read source.
     */
    fun recent(limit: Int = historyLimit): List<ChatMessage> {
        if (limit <= 0) return emptyList()
        return cache.takeLast(limit)
    }

    /**
     * Writes one turn as two entries — user line then assistant line — into the cache and
     * appends two JSONL lines to the journal. Serialized through the journal Mutex.
     */
    suspend fun appendTurn(
        userMessage: String,
        assistantSummary: String,
    ) {
        val timestamp = System.currentTimeMillis()
        val userLine = ChatMessage(ChatMessage.ROLE_USER, userMessage, timestamp)
        val assistantLine = ChatMessage(ChatMessage.ROLE_ASSISTANT, assistantSummary, timestamp)
        val jsonl =
            journalJson.encodeToString(ChatMessage.serializer(), userLine) + "\n" +
                journalJson.encodeToString(ChatMessage.serializer(), assistantLine) + "\n"

        journalMutex.withLock {
            cache += userLine
            cache += assistantLine
            Files.writeString(journalFile, jsonl, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
    }

    private fun truncateJournal() {
        journalFile.parent?.let { Files.createDirectories(it) }
        Files.writeString(journalFile, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
}
