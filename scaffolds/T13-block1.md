# T13-block1

## Goal
Implement the `local` (Ollama) side of the pinned `LlmClient` seam with Koog 1.3.0 (D-16): the shared `KoogChatRunner` turn assembly (reused by T14) and `OllamaLlmClient`. The tool set arrives from `ChatService` (D-8, never empty); `infrastructure.llm` references only Koog types and its own config — never service classes.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- `infrastructure.llm` stays business-free: imports only Koog types, `com.fuelingchat.config.AppConfig`, and `infra.llm`'s own types — never `ChatServiceImpl`, `DialogHistory`, `ProviderRouter`, `GetFuelingInfoTool`.
- Never modify `LlmClient.kt` (pinned seam), `ChatMessage.kt`, `ClientModule.kt` (T13 integrator step), `build.gradle.kts` (integrator), or `AppConfig.kt`/`.env.example` (integrator, only if the default is corrected).
- The exact Koog 1.3.0 surface is verified against the pinned jars at implementation time (D-16); wording deviations are documented in the correctness review. The spec §3 seam and the D-8 tool-set contract stay untouched.
- Runs after T12 (seam + service landed). T14 reads `KoogChatRunner.kt` — never modifies it.
- No new dependencies without explicit permission.

## Files to create/modify
- `src/main/kotlin/com/fuelingchat/infra/llm/KoogChatRunner.kt` — create, per the Public API below (shared with T14 — T14 only reads it).
- `src/main/kotlin/com/fuelingchat/infra/llm/OllamaLlmClient.kt` — create, per the Public API below.

## Public API
```kotlin
package com.fuelingchat.infra.llm

class KoogChatRunner(
    private val koogClient: /* Koog 1.3.0 LLM client type — verified against the pinned jars (D-16) */,
    private val model: /* Koog LLModel — e.g. LLModel(LLMProvider.Ollama, modelId), per D-16 */,
) {
    suspend fun runTurn(
        systemPrompt: String,
        history: List<ChatMessage>,   // mapped by role: "user" / "assistant"
        userMessage: String,
        tools: List<Tool<*, *>>,      // service-supplied (D-8), never empty
    ): String                        // the agent run's final text
}

class OllamaLlmClient(
    config: AppConfig,                // OLLAMA_BASE_URL, OLLAMA_MODEL
    httpClientFactory: /* injectable Koog HTTP-client factory — tests pass a MockEngine-backed factory */,
) : LlmClient {
    override suspend fun run(systemPrompt: String, history: List<ChatMessage>, userMessage: String, tools: List<Tool<*, *>>): String
}
```
- `KoogChatRunner.runTurn` assembles the Koog single-run turn: system prompt + history mapped by role (`user`/`assistant`), the service-supplied tool registry (`ToolRegistryBuilder().tools(tools).build()` per D-16), the model, and the agent run returning the final text. The exact agent-run API is verified against the pinned jars (D-16).
- `OllamaLlmClient` builds its runner from `OllamaClient(baseUrl = config.ollamaBaseUrl)` (default `http://localhost:11434/v1` — matches the current AppConfig and `.env.example`) + `LLModel(LLMProvider.Ollama, config.ollamaModel)` and delegates `run` to `runTurn`.
- Integrator-step items (not this block): ClientModule.kt — replace the `local` stub: `bind<LlmClient>(tag = "local") with singleton { OllamaLlmClient(instance(), <http-client factory>) }` (keep the `remote` stub as-is); build.gradle.kts — `testImplementation("io.ktor:ktor-client-mock-jvm:3.3.3")`; AppConfig.kt + `.env.example` — only if the base-URL default must be corrected (no edit if the verified surface matches the current default).

## Invariants
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).
