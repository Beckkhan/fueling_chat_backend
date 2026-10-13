package com.fuelingchat.service

import com.fuelingchat.infra.llm.ChatMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * CONTRACT-HISTORY-001 (D-1, D-11): write-through dialog journal.
 * The in-memory cache is the ONLY read source; the .jsonl journal is append-only persistence
 * written per turn. Construction truncates the journal, so restarts clear context.
 */
class DialogHistory(
    private val journalFile: Path, // DI: AppConfig.dialogHistoryFile (DIALOG_HISTORY_FILE)
    private val historyLimit: Int, // DI: AppConfig.historyLimit (HISTORY_LIMIT, default 20)
) {
    private val cache: MutableList<ChatMessage> = mutableListOf()
    private val journalMutex = Mutex()

    init {
        journalFile.parent?.let { Files.createDirectories(it) }
        Files.writeString(
            journalFile,
            "",
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    /**
     * D-11: the last [limit] messages from the cache, oldest first. The cache — never the
     * file — is the read source. Defaults to [historyLimit].
     */
    suspend fun recent(limit: Int = historyLimit): List<ChatMessage> =
        journalMutex.withLock {
            if (limit <= 0) emptyList() else cache.takeLast(limit)
        }

    /**
     * Appends one completed turn — user line then assistant line — into the cache and writes
     * two kotlinx-serialization JSONL lines (one per ChatMessage) to the journal.
     * Mutex-serialized; both messages share the same epoch-millis timestamp.
     */
    suspend fun appendTurn(
        userMessage: String,
        assistantSummary: String,
    ) {
        val timestamp = System.currentTimeMillis()
        val user = ChatMessage(ChatMessage.ROLE_USER, userMessage, timestamp)
        val assistant = ChatMessage(ChatMessage.ROLE_ASSISTANT, assistantSummary, timestamp)
        journalMutex.withLock {
            cache += user
            cache += assistant
            appendLine(user)
            appendLine(assistant)
        }
    }

    private fun appendLine(message: ChatMessage) {
        Files.writeString(
            journalFile,
            Json.encodeToString(ChatMessage.serializer(), message) + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }
}
