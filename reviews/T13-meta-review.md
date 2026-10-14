# T13 Meta-Review — Koog LlmClient 'local' (Ollama)

**Commit:** `4ecfaadc72201ef2535ac173dd1856244b1ea1b4` ("T13: Koog LlmClient 'local' (Ollama)")
**Scope:** invariants + logic + API contract + tests (single-pass, replaces the 4-gate pipeline).
**Verdict basis:** commit diff reviewed; Koog 1.3.0 surface verified against the pinned jar sources in the Gradle cache; `./gradlew --offline ktlintCheck detekt` green; `OllamaLlmClientTest` 3/3 and `AppConfigTest` 13/13 green.

## 1. Invariants compliance

| Rule | Status | Evidence |
|---|---|---|
| Seam untouched (`LlmClient`, `ChatMessage`) | ok | `git show` touches neither; `OllamaLlmClient : LlmClient` implements the pinned 4-arg `run(systemPrompt, history, userMessage, tools)`. |
| Tool set forwarded untouched, never empty (D-8) | ok | `KoogChatRunner.kt:38` `ToolRegistryBuilder().tools(tools).build()` — no filtering; enforced upstream: `ChatServiceImpl.kt:33` builds `listOf(getFuelingInfoTool)` (non-empty by construction); test supplies `listOf(harness.tool)`. |
| infra.llm business-free | ok | `KoogChatRunner.kt`/`OllamaLlmClient.kt` import only Koog types, `AppConfig`, and infra.llm's own `ChatMessage`/`LlmClient`. Grep confirms no service/db imports. |
| D-7 (no streaming/polling primitives) | ok | No `text/event-stream`/`chunked`/`webSocket`/`EventSource` in T13 code; wire `stream=false` asserted in test (`OllamaLlmClientTest.kt:98`); `withSse = false` is passed only in the test factory, deliberately disabling Koog's optional SSE plugin. |
| No SQL/DB, no credential logging | ok | No Exposed/transaction usage in T13 files; no logging statements at all; test env values are fake and never logged. |
| Exposed N+1 | n/a | No queries in T13 (tool queries belong to T12's `GetFuelingInfoTool`). |
| Complexity/style caps (detekt maxIssues 0) | ok | `./gradlew --offline ktlintCheck detekt` — BUILD SUCCESSFUL (all active rules pass; LongMethod ≤ 40, LongParameterList ≤ 5, MaxLineLength ≤ 120, no wildcard imports). |

## 2. Logic / correctness

- **Turn assembly** (`KoogChatRunner.kt`): one fresh `AIAgent(promptExecutor, agentConfig, toolRegistry)` per turn (single-run strategy, `GraphAIAgent<String, String>`), `agent.run(userMessage)` returns the final text. No state held between turns. `MAX_AGENT_ITERATIONS = 10` bounds the tool loop (Koog default is 3). ok.
- **History role mapping**: `assistant` → `assistant`, everything else → `user` (`KoogChatRunner.kt:68-71`); journal roles are only `user`/`assistant` (T12), so mapping is complete. ok.
- **Error handling**: this layer lets `LLMClientException`/`KoogHttpClientException`/`AIAgentException` propagate; `ChatServiceImpl.kt:56-64` (T12) masks them into `ChatFailure(502, PROVIDER_UNREACHABLE)` — consistent with CONTRACT-API-001. ok.
- **Test-only concern (non-blocking):** `OllamaLlmClientTest.kt:141` casts `request.body as TextContent`; a brittle but loud failure mode if Koog ever changes body serialization. Acceptable in a MockEngine harness.

## 3. Koog 1.3.0 surface (D-16) — verified against pinned jars

Verified by extracting the 1.3.0 `-sources.jar`s from `~/.gradle/caches/modules-2/files-2.1/ai.koog/*/1.3.0/`:

- **(a) Constructor deviation — REAL and SOUND.** `OllamaClient.kt` (commonMain, lines 143-167) has the secondary constructor `OllamaClient(httpClientFactory: KoogHttpClient.Factory, baseUrl: String = DEFAULT_BASE_URL, headers, queryParameters, timeoutConfig, clock, contextWindowStrategy, toolDescriptorConverter)`. The top-level `ollamaClient()` function documented in D-16 (jvmCommonMain) resolves its factory via `HttpClientFactoryResolver.resolve()` — not injectable — so the constructor with an explicit `KoogHttpClient.Factory` is required for MockEngine tests. This is a documented part of Koog's own public API ("Secondary constructor that builds the underlying KoogHttpClient from a KoogHttpClient.Factory"). Acceptable; the rationale is also stated in the `OllamaLlmClient` KDoc.
- **(b) Base URL — VERIFIED, scaffold was wrong, fix is right.** `OllamaClient.DEFAULT_BASE_URL = "http://localhost:11434"` — no `/v1`. Koog targets Ollama's **native** API: `DEFAULT_MESSAGE_PATH = "api/chat"`, posted as `httpClient.post(path = "api/chat", …)` with `stream = false` on the non-streaming execute path (`OllamaClient.kt` ~lines 214-230). `KtorKoogHttpClient.Factory.create` normalizes the base URL with a trailing slash and applies it via `defaultRequest { url.takeFrom(...) }` (`KtorKoogHttpClient.kt:309-317`), so posts land on `<base>/api/chat`. The commit's correction of `AppConfig` default, `.env.example`, and `AppConfigTest` from `…/v1` to `http://localhost:11434` makes them match D-16 exactly. The scaffold's "matches the current AppConfig" claim was simply wrong.
- **(c) Wire schema — DEVIATION REAL, ACCEPTABLE, DOCUMENTED.** The scaffold's `input_schema` wording does not exist in Koog's Ollama wire format: `OllamaModels.kt` defines `OllamaToolDTO(type = "function", function = Definition(name, description, parameters))`, and `OllamaToolDescriptorSchemaGenerator.generate` emits `{title, description, type: "object", properties: {…}, required: […]}` under `parameters`. The test asserts exactly this shape (`function.parameters`, `type`, `properties.fueling_id.type`, `required` contains `fueling_id`) and the deviation from the scaffold wording is documented in the comment above `assertToolSchema` (`OllamaLlmClientTest.kt:175-178`). INDEX.md requires deviation documentation in the correctness review — this meta-review serves as that record.
- Also verified against 1.3.0 sources: `MultiLLMPromptExecutor(vararg llmClients)` secondary constructor, `AIAgent(promptExecutor, agentConfig, toolRegistry)` single-run factory, `AIAgentConfig(prompt, model, maxAgentIterations)`, `prompt(id) { }` DSL with `PromptBuilder.system/user/assistant(content)`, `ToolRegistryBuilder().tools(List<ToolBase<*, *>>)`, `SimpleTool<TArgs>(argsType, name, description)`, `LLModel(provider, id)`, `ai.koog.serialization.typeToken`. All usages match.

## 4. API contract

- Pinned seam intact: `LlmClient.run` signature and `ChatMessage{role, body, timestamp}` unchanged. No cross-stack (Kotlin↔zod) DTOs in this task — n/a for CONTRACT-LLM-001.
- DI: `ClientModule` (commit state) binds `local` → `OllamaLlmClient(instance(), KtorKoogHttpClient.Factory())`; `instance()` resolves `AppConfig` through `AppModule`'s `configModule` import. The `remote` stub remains `error()` in the commit — T14's integrator replaces it (current working tree already shows T14's in-flight edit; not reviewed here). DiSmokeTest's `local` assertion resolves `OllamaLlmClient` without network access (construction only); its `remote` assertion is expected red until T14 lands — not counted against T13.

## 5. Test coverage

`OllamaLlmClientTest` (3/3 green, MockEngine only, no network/DB/Ollama):
1. `plainTextReplyIsReturnedAsTheFinalText` — final text returned; exactly 1 model call; 0 tool invocations.
2. `toolCallReplyRunsTheSuppliedToolThroughTheAgentLoopAndReturnsTheFinalAnswer` — fake tool executed exactly once with the model-supplied `fueling_id`; exactly 2 model calls; `TOOL_RESULT` present in the 2nd request body (tool result fed back). This proves the D-8 tool set is wired into the agent run.
3. `outgoingRequestUsesTheConfiguredModelAndEndpointAndCarriesTheToolSchema` — POST; URL starts with the configured base (a non-default `http://127.0.0.1:19191/v1` guards against hardcoding) and ends with `/api/chat`; `model` from config; `stream=false` (D-7); tool schema name `get_fueling_info`, `type: object`, `properties.fueling_id.type: string`, `required` contains `fueling_id`.

`AppConfigTest` 13/13 green, updated to the corrected default `http://localhost:11434`.
Assertions are live (behavioral, with failure messages); a `require` in the MockEngine harness fails loudly on any extra model call.

## 6. Style / complexity / checks

- `./gradlew --offline ktlintCheck detekt` — green (tasks up-to-date; inputs for T13 files unchanged since the successful run).
- T13 suites run green: `OllamaLlmClientTest` 3/3, `AppConfigTest` 13/13. Full `build`/`test` not judged — DiSmokeTest's `remote` assertion is expected red until T14 commits.

## 7. Status sync

- `docs/tasks/INDEX.md:21` — T13 `in progress` (correct until verdict).
- `docs/tasks/T13.md:3` — `in progress` (correct until verdict).

## Findings

No blocking findings. Non-blocking notes (no action required for T13):
1. (Info) `KoogChatRunner` KDoc line "single response per turn, D-7" — a tool-call turn performs two HTTP POSTs; D-7's no-streaming/no-polling semantics hold (each call is plain request/response, `stream=false`), wording only.
2. (Info) Deviation documentation for (a)/(b)/(c) above is spread across the test comment and the `OllamaLlmClient` KDoc; this review records it for the correctness-review gate.

VERDICT: PASS
