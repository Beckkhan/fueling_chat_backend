# T12-block1

## Goal
Implement `DialogHistory` (CONTRACT-HISTORY-001, D-1/D-11): the in-memory message list is the only read source; the `.jsonl` journal is append-only, `Mutex`-serialized, and truncated at construction; `recent(limit)` returns the last messages in order. Also make `ChatMessage` serializable for the journal.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- `ChatMessage` keeps its exact current fields (`role: String`, `body: String`, `timestamp: Long`) — only the `@Serializable` annotation is added.
- No DB, no Koog, no `LlmClient` here — pure memory + file.
- ServiceModule.kt is NOT touched (binding is integrator-step work).
- Runs after T11 (seam landed); T12-block2 depends on this block.
- No new dependencies without explicit permission.

## Files to create/modify
- `src/main/kotlin/com/fuelingchat/infra/llm/ChatMessage.kt` — modify: add `@Serializable` (kotlinx-serialization) to the existing data class; fields unchanged.
- `src/main/kotlin/com/fuelingchat/service/DialogHistory.kt` — create, per the Public API below.

## Public API
```kotlin
package com.fuelingchat.service

class DialogHistory(
    private val journalFile: Path,
    private val historyLimit: Int,
) {
    suspend fun append(message: ChatMessage)                    // cache += message; one JSONL line appended to the journal
    fun recent(limit: Int = historyLimit): List<ChatMessage>    // last `limit` cached messages, in append order
}
```
- Construction truncates the journal (create parent directories, empty the file) and starts with an empty cache — restarts clear context (D-1).
- Journal lines: kotlinx-serialization of `ChatMessage` (`{role, body, timestamp}`), one message per line, append-only, writes serialized by a `Mutex`.
- Defaults come from config: `DIALOG_HISTORY_FILE` = `data/dialog-history.jsonl`, `HISTORY_LIMIT` = 20 (D-11). The DI binding (integrator step) supplies `config.dialogHistoryFile` / `config.historyLimit`.
- Consumed by T12-block2 (`ChatServiceImpl`) — do not change this signature after landing.

## Invariants
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).
