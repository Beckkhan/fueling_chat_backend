# T12-block2

## Goal
Implement the service layer of one chat turn (CONTRACT-SERVICE-001): `ProviderRouter` resolves the provider name (D-10) and `ChatServiceImpl` implements the T11 seam — system prompt per D-6/D-9, history from `DialogHistory`, the never-empty tool set per D-8, the `LlmClient.run` call, write-through journal append, and masked failure classification.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- `ChatService.kt` (T11-block1-owned seam interface) stays untouched — the implementation class lives in a NEW file `ChatServiceImpl.kt`. Documented scaffold deviation: the task artifact names `ChatService.kt` for the implementation; to keep the seam file single-owner, the impl gets its own file and is bound as `ChatService` by the integrator.
- ServiceModule.kt is NOT touched (integrator step: replace the T11 placeholder with the real bindings).
- No `elapsed_ms` here — it remains the route's concern (T11).
- Failure classification per CONTRACT-SERVICE-001; response messages and logs never carry raw SQL, credentials, or provider text.
- Exact Koog exception FQNs (`LLMClientException`/`KoogHttpClientException`, `AIAgentException`) are verified against the pinned Koog 1.3.0 jars at implementation time (D-16); wording deviations are documented in the correctness review.
- Runs after T12-block1 (imports `DialogHistory`).
- No new dependencies without explicit permission.

## Files to create/modify
- `src/main/kotlin/com/fuelingchat/service/ProviderRouter.kt` — create, per the Public API below.
- `src/main/kotlin/com/fuelingchat/service/ChatServiceImpl.kt` — create, per the Public API below.

## Public API
```kotlin
package com.fuelingchat.service

class ProviderRouter {
    fun resolve(requestedProvider: String?): String
    // null or "local" → "local"; "remote" → "remote" (D-10 default = local)
    // any other value → throw ChatFailure(400, "Unknown provider")  (masked)
}

class ChatServiceImpl(
    private val dialogHistory: DialogHistory,
    private val providerRouter: ProviderRouter,
    private val getFuelingInfoTool: GetFuelingInfoTool,
    private val localClient: LlmClient,     // named binding tag "local" (T13)
    private val remoteClient: LlmClient,    // named binding tag "remote" (T14)
) : ChatService {
    override suspend fun runTurn(prompt: String, fuelingId: String?, provider: String?): ChatTurn
}
```
- System prompt (built here, D-6/D-9): always instructs to answer in the language of the user's latest message and to answer fueling questions only via `get_fueling_info`; when `fuelingId != null` it carries an explicit line `attached fueling_id: <id> — use exactly this value for get_fueling_info`; when absent, no such line.
- Turn flow: `resolved = providerRouter.resolve(provider)` → `history = dialogHistory.recent()` → `tools = listOf<Tool<*, *>>(getFuelingInfoTool)` (never empty, D-8) → `summary = client.run(systemPrompt, history, prompt, tools)` with the client picked by `resolved`.
- On success: append the user message then the assistant summary to `DialogHistory` (one journal line each); return `ChatTurn(summary, resolved)`. On failure: nothing is appended.
- Classification: `ExposedSQLException` → `ChatFailure(500, "Databases unavailable")`; `LLMClientException` / `KoogHttpClientException` / `AIAgentException` → `ChatFailure(502, "Provider unreachable")`. A `ChatFailure` from the router propagates as-is.
- Integrator-step items (not this block): ServiceModule.kt — replace the T11 placeholder: `bind<ChatService>() with singleton { ChatServiceImpl(instance(), instance(), instance(), instance(tag = "local"), instance(tag = "remote")) }`, plus `bind<ProviderRouter>() with singleton { ProviderRouter() }` and `bind<DialogHistory>() with singleton { DialogHistory(instance<AppConfig>().dialogHistoryFile, instance<AppConfig>().historyLimit) }`; keep the `GetFuelingInfoTool` binding unchanged. Note: resolving `ProviderRouter`/`ChatService` through the app graph is deferred until T13/T14 replace the `LlmClient` stubs (DiSmokeTest extensions land in T14).

## Invariants
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).
