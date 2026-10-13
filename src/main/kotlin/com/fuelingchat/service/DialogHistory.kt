package com.fuelingchat.service

import com.fuelingchat.infra.llm.ChatMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class DialogHistory(
    file: Path,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val file = file.toAbsolutePath()
    private val messages = mutableListOf<ChatMessage>()
    private val mutex = Mutex()

    init {
        file.parent?.let { Files.createDirectories(it) }
        Files
            .newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { }
    }

    suspend fun appendTurn(
        userMessage: String,
        assistantMessage: String,
    ) {
        val user = ChatMessage(USER_ROLE, userMessage, clock())
        val assistant = ChatMessage(ASSISTANT_ROLE, assistantMessage, clock())
        val lines = journalLine(user) + journalLine(assistant)
        mutex.withLock {
            Files.writeString(file, lines, StandardCharsets.UTF_8, StandardOpenOption.APPEND)
            messages += user
            messages += assistant
        }
    }

    suspend fun recent(limit: Int): List<ChatMessage> = mutex.withLock { messages.takeLast(limit) }

    private fun journalLine(message: ChatMessage): String = Json.encodeToString(message) + "\n"

    companion object {
        const val USER_ROLE = "user"
        const val ASSISTANT_ROLE = "assistant"
    }
}
