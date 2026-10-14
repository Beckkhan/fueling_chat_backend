# T13 — Correctness Review (second gate)

**Verdict:** PASS
**Target:** commit `600144d5e2b25e6f7c5853afe0f58c9e413250ca` (T13: integrate Koog LlmClient 'local' (Ollama)), diff vs parent `c6f34f11d94771301c810487c35dd5db8bac90c6`
**Reviewer:** correctness-reviewer (second gate)
**Timestamp:** 2026-10-14T00:00:02Z
**Working tree:** `git status` shows only `metrics/cto-ledger.md` dirty; the reviewed state matches the commit. `reviews/T13-test-engineer.md` (PASS) exists but per the parallel fan-out the verdict is based on the diff and independent checks.

## Scope of the diff

9 files: `.env.example`, `build.gradle.kts`, `docs/tasks/T13.md` (scaffold), `config/AppConfig.kt` (one default), `infra/llm/ClientModule.kt` (wiring), new `infra/llm/KoogChatRunner.kt` / `infra/llm/OllamaLlmClient.kt`, `config/AppConfigTest.kt` (one assertion), new `infra/llm/OllamaLlmClientTest.kt`. Confirmed via `git diff 600144d~1 600144d --name-status`.

## Gate checks

### 1. Pinned `LlmClient` seam (spec §3, CONTRACT-LLM-001) untouched

- [x] `src/main/kotlin/com/fuelingchat/infra/llm/LlmClient.kt` is **not in the diff**; `git log -- LlmClient.kt` shows only `71c6f4e T04: DI wiring`. Its surface is byte-identical to spec §3: `suspend fun run(systemPrompt: String, history: List<ChatMessage>, userMessage: String, tools: List<Tool<*, *>>): String`.
- [x] `OllamaLlmClient.run` (`OllamaLlmClient.kt:47-58`) overrides that signature exactly (same parameter names and order) and only delegates — no extra members leak through the seam to `service`.

### 2. `OllamaLlmClient` from `AppConfig` + injectable HTTP-client factory

- [x] Constructor `OllamaLlmClient(config: AppConfig, httpClientFactory: KoogHttpClient.Factory)` (`OllamaLlmClient.kt:25-28`); base URL/model read from config at `:34` (`config.ollamaBaseUrl`) and `:38` (`LLModel(provider = LLMProvider.Ollama, id = config.ollamaModel)`).
- [x] Factory is the real injected seam: production binds `KtorKoogHttpClient.Factory()` (`ClientModule.kt:14`); the test injects `KtorKoogHttpClient.Factory(HttpClient(MockEngine { ... }))` (`OllamaLlmClientTest.kt:106-109`). No other construction site of `KtorKoogHttpClient.Factory` exists in the repo.
- [x] Pinned-surface call `PromptExecutor.builder().ollama(baseUrl, httpClientFactory).build()` (`OllamaLlmClient.kt:31-35`) verified against the pinned jars (see §5).

### 3. `KoogChatRunner` provider-agnostic (T14-reusable)

- [x] Constructor takes only Koog abstractions `PromptExecutor` + `LLModel` (`KoogChatRunner.kt:24-27`); imports are all `ai.koog.*` plus own package types. No `Ollama`/`DeepSeek`-specific type anywhere; KDoc (`:14-17, 21-22`) documents T14 reuse and the scaffold's T14 path (`PromptExecutor.builder().addClient(DeepSeekLLMClient(...))`) stays viable.
- [x] Turn assembly matches the scaffold exactly: `prompt("chat-turn") { system; user/assistant per role }` (`:44-53`), `ToolRegistryBuilder().tools(tools).build()` (`:54`), `AIAgentConfig(prompt, model, maxAgentIterations = 50)` (`:55`), top-level `AIAgent(promptExecutor, agentConfig, toolRegistry)` (`:56`), `agent.run(userMessage)` (`:57`).

### 4. Base URL default vs verified native endpoint (documented deviation)

- [x] Pinned-jar verification: `OllamaClient.DEFAULT_BASE_URL` (`ConstantValue: String http://localhost:11434`) and `DEFAULT_MESSAGE_PATH` (`ConstantValue: String api/chat`) in `prompt-executor-ollama-client-jvm-1.3.0.jar` — the pinned client posts to `<baseUrl>/api/chat`, **not** the OpenAI-compatible `/v1/chat/completions`. The task description's "Ollama via its OpenAI-compatible endpoint" wording is a **documented deviation** — recorded in `docs/tasks/T13.md:31`, in `OllamaLlmClient.kt:14-18` KDoc, and here. The implementation follows the verified pinned surface.
- [x] Default corrected `http://localhost:11434/v1` → `http://localhost:11434` in `AppConfig.kt:21` and `.env.example:14`; `AppConfigTest.kt:59` updated to the corrected default (one assertion). The explicit-override path still passes a `/v1` URL through verbatim (`AppConfigTest.kt:82, 101`), so a custom base URL remains configurable. No stale `/v1` default remains in tracked config.
- [x] Test proves the endpoint at runtime: asserts `request.url.protocolWithAuthority == config.ollamaBaseUrl` and `encodedPath == "/api/chat"` (`OllamaLlmClientTest.kt:189-191`).

### 5. Exact Koog 1.3.0 surface verified against the pinned jars (D-16)

`javap` against the Gradle-cache jars (version 1.3.0, same modules the project resolves):

- [x] `PromptExecutor$Companion.builder()` → `PromptExecutorBuilder`; `ollama(java.lang.String, KoogHttpClient$Factory, ...)` with defaults — `prompt-executor-model-jvm-1.3.0.jar`.
- [x] `KoogHttpClient$Factory` interface — `http-client-core-jvm-1.3.0.jar`; `KtorKoogHttpClient$Factory(HttpClient)` and `Factory()` — `http-client-ktor-jvm-1.3.0.jar`.
- [x] `LLModel(LLMProvider, String, capabilities, contextLength, maxOutputTokens)` and constant `LLMProvider.Ollama` — `prompt-llm-jvm-1.3.0.jar`.
- [x] `AIAgentConfig(Prompt, LLModel, int, ...)`; top-level `AIAgent(PromptExecutor, AIAgentConfig, ToolRegistry, String, KoogClock, Function1)` returning `GraphAIAgent` (`AIAgentFactoryKt`) — `agents-core-jvm-1.3.0.jar`.
- [x] `ToolRegistryBuilder().tools(List<? extends ToolBase<?, ?>>)` accepts `List<Tool<*, *>>` — `agents-tools-jvm-1.3.0.jar`; `SimpleTool(TypeToken, String, String)` for the test fake.
- [x] Top-level `prompt(String, LLMParams, KoogClock, Function1<PromptBuilder, Unit>)` and `PromptBuilder.system/user/assistant(String)` — `prompt-model-jvm-1.3.0.jar`.
- [x] `OllamaChatRequestDTO{model, messages, tools}` — `prompt-executor-ollama-client-jvm-1.3.0.jar`; the wire assertions (model id, `tools[0].type=="function"`, function name/description/parameters schema, role `"tool"` result message) are exercised by the passing tests (`OllamaLlmClientTest.kt:198-235`).

### 6. `infra/llm` references only Koog types and its own config; never service classes

- [x] Full import sweep over `src/main/kotlin/com/fuelingchat/infra/llm/`: only `ai.koog.*`, `org.kodein.di.*` (DI module), `com.fuelingchat.config.AppConfig` (the sanctioned own-config reference per spec §3 "infra references only Koog + its own config"), and own-package types (`LlmClient`, `ChatMessage`). Zero `com.fuelingchat.service.*` imports.

### 7. No credentials in code or logs

- [x] No logging statements in any T13 main file; nothing logs config values. Searched the T13 files for `api_key|secret|password|token`: only test-fixture dummies (`test-db-password`, `test-deepseek-key` in `OllamaLlmClientTest.kt:61-62`) and placeholders in `.env.example`. No real credential, and no credential value can reach a log or an error message via this diff.

### 8. History mapped by role; tool registry from the service (D-8)

- [x] `KoogChatRunner.kt:47-52` maps `ChatMessage.ROLE_USER → user(body)` and `ROLE_ASSISTANT → assistant(body)` in order; `DialogHistory` writes exactly those two roles (`DialogHistory.kt:52-53`), so the mapping is complete for the closed production set.
- [x] Tool registry is built from the caller-supplied list only (`KoogChatRunner.kt:54`); the service supplies the never-empty set by construction (`ChatServiceImpl.toolSet() = listOf(getFuelingInfoTool)`), and the passed-in `tools` parameter is the sole registry source. The tool-loop test proves the D-8 wiring end-to-end: the fake tool is executed with `{"fueling_id":"F-10001"}` and its fixed text is sent back as the role-`"tool"` message before the final answer (`OllamaLlmClientTest.kt:141-157, 224-235`; observed in the test log: `Executing tool (name: fake_tool, args: {"fueling_id":"F-10001"})`).

### 9. D-9 (fueling_id branching) / D-6

- [x] Not part of this diff (`ChatServiceImpl.kt` untouched by T13); reviewed at T12 and unchanged. The runner only forwards the service-built system prompt verbatim (`KoogChatRunner.kt:46`), so no branching logic can be bypassed at this layer.

### 10. Edge cases, resource and error behavior

- [x] `maxAgentIterations = 50` bounds the tool loop; a fresh `GraphAIAgent` is constructed per turn (`KoogChatRunner.kt:56`), so there is no cross-turn or shared mutable state; concurrent turns cannot interfere through the runner.
- [x] Failure paths: the runner/client do not catch exceptions, so `KoogHttpClientException` / `LLMClientException` / `AIAgentException` propagate to the T12 classification layer (502 masked) — consistent with CONTRACT-SERVICE-001; nothing is logged or echoed.
- [x] `ClientModule.kt:17` removes the `local` `error()` stub as required; the `remote` stub stays (T14) and remains unreachable until then.

## Independent verification

- `./gradlew test --tests "com.fuelingchat.infra.llm.OllamaLlmClientTest" --tests "com.fuelingchat.config.AppConfigTest"` → **BUILD SUCCESSFUL**; fresh XML: `OllamaLlmClientTest` **2 tests / 0 failures / 0 errors / 0 skipped** (timestamp 2026-10-13T23:59:11Z), `AppConfigTest` **13 / 0 / 0**. No Ollama process is involved (all HTTP served by `MockEngine`).
- `./gradlew build ktlintCheck detekt` → **BUILD SUCCESSFUL** (AC "0 errors" holds).
- Pinned-jar surface inspection via `javap` on the Gradle-cache `1.3.0` jars as listed in §5; `OllamaClient` bytecode confirms the `http://localhost:11434` / `api/chat` constants.

## Findings

None. No contract violation, no logic error, no credential exposure, no read-only/DDL concern (no DB code in this diff).

### Non-blocking observations (not findings)

- `KoogChatRunner.kt:48-51` — the role `when` is a statement with no `else`: an unknown role would be silently skipped rather than failing loudly. Safe today because `DialogHistory` emits only `user`/`assistant` (the closed seam set); an `else → error(...)` guard would surface future drift earlier.
- `ServiceModule.kt:27-28` — the comment "The named LlmClient bindings stay error() stubs until T13/T14" is now stale for `local`; comment-only, file untouched by this commit.
- No test resolves the `local` binding through the DI graph (`DiSmokeTest` does not resolve `LlmClient`/`ProviderRouter`); the Kodein wiring is type-verified by compilation and inspection but would only be exercised at first request. Not an acceptance-criteria requirement.
- The held `KtorKoogHttpClient` has no close hook in the pinned seam; it lives for process lifetime as a singleton — startup/shutdown lifecycle is outside this task's contract.
