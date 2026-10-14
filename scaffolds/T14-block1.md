# T14-block1

## Goal
Implement the `remote` (DeepSeek) side of the pinned `LlmClient` seam for the DeepSeek API (CONTRACT-LLM-002): the same `run` contract and the same tool-set contract as `local` (D-8), with the turn assembly delegated to T13's shared `KoogChatRunner` — no duplicated run logic.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- `KoogChatRunner.kt` is T13-owned: READ-ONLY here — never modify it (T13 and T14 blocks may run in parallel).
- `infrastructure.llm` stays business-free — the same import rule as T13-block1.
- Never modify `LlmClient.kt`, `ChatMessage.kt`, `ClientModule.kt` (T14 integrator step), or `OllamaLlmClient.kt` / `OllamaLlmClientTest.kt` (T13-owned).
- The exact Koog 1.3.0 DeepSeek surface is verified against the pinned jars at implementation time (D-16); wording deviations are documented in the correctness review. The spec §3 seam and the D-8 tool-set contract stay untouched.
- Runs after T12; consumes T13-block1's `KoogChatRunner` (must have landed).
- No new dependencies without explicit permission.

## Files to create/modify
- `src/main/kotlin/com/fuelingchat/infra/llm/DeepSeekLlmClient.kt` — create, per the Public API below.

## Public API
```kotlin
package com.fuelingchat.infra.llm

class DeepSeekLlmClient(
    config: AppConfig,                // DEEPSEEK_API_KEY, DEEPSEEK_MODEL
    httpClientFactory: /* injectable — the same factory contract as T13's OllamaLlmClient */,
) : LlmClient {
    override suspend fun run(systemPrompt: String, history: List<ChatMessage>, userMessage: String, tools: List<Tool<*, *>>): String
}
```
- Builds its `KoogChatRunner` from `DeepSeekLLMClient(apiKey = config.deepseekApiKey, baseUrl = "https://api.deepseek.com")` + `LLModel(LLMProvider.DeepSeek, config.deepseekModel)` (exact surface verified per D-16) and delegates `run` to `runTurn`.
- Integrator-step items (not this block): ClientModule.kt — replace the `remote` stub: `bind<LlmClient>(tag = "remote") with singleton { DeepSeekLlmClient(instance(), <http-client factory>) }` (keep the `local` binding landed by the T13 integrator). After this task no `LlmClient` stub remains in the DI graph.

## Invariants
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).
