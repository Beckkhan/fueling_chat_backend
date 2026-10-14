# T14 — Correctness Review (second gate)

**Verdict:** PASS
**Target:** commit `3e258327a2f9f450db5d4959dca37cb2f45ca829` (T14: integrate Koog LlmClient 'remote' (DeepSeek)), diff vs parent `9950619b62ced66ecd85c7e79c2edcc2c0b4e360` (6 files, +467/−2)
**Reviewer:** correctness-reviewer (second gate)
**Timestamp:** 2026-10-14T00:26:13Z
**Working tree:** `git status` shows only `metrics/cto-ledger.md` dirty (pre-existing); the reviewed state matches the commit (untracked `reviews/T14-*.md` are the parallel gates' outputs). `reviews/T14-test-engineer.md` (PASS) appeared mid-review and is consistent with this verdict; per the parallel fan-out the findings below are based on the diff and independent checks alone.

## Scope of the diff

`build.gradle.kts` (one dependency line), `docs/tasks/T14.md` (scaffold), `infra/llm/ClientModule.kt` (remote binding), new `infra/llm/DeepSeekLlmClient.kt`, `DiSmokeTest.kt` (+2 assertions), new `infra/llm/DeepSeekLlmClientTest.kt`. Confirmed via `git show --stat 3e25832`.

## Gate checks

### 1. Pinned `LlmClient` seam (spec §3, CONTRACT-LLM-002) untouched

- [x] `LlmClient.kt`, `KoogChatRunner.kt`, `ChatMessage.kt`, `OllamaLlmClient.kt`, `config/`, `service/` are **not in the diff** (`git diff 3e25832^ 3e25832 --stat` for those paths is empty; `LlmClient.kt` last touched at `71c6f4e T04: DI wiring`).
- [x] `DeepSeekLlmClient.run` (`DeepSeekLlmClient.kt:70-81`) overrides the seam byte-for-byte (same parameter names/order/types) and only delegates to the shared `KoogChatRunner.runTurn` — no duplicated run logic, nothing extra leaks through the seam.

### 2. D-16 deviations — adjudicated against the pinned jars (all three verified)

**(a) Separate artifact `ai.koog:prompt-executor-deepseek-client-jvm:1.3.0-beta` — JUSTIFIED, MINIMAL, CONSISTENT.**

- [x] `koog-agents-jvm-1.3.0.jar` contains **zero** deepseek classes (`unzip -l | grep -i deepseek` → none); its POM pulls `prompt-executor-ollama-client-jvm:1.3.0` and `prompt-executor-openai-client-base-jvm:1.3.0` but no deepseek artifact. The scaffold premise is fact.
- [x] Maven Central `maven-metadata.xml` for the artifact: `<latest>` and `<release>` are both `1.3.0-beta`; every 1.x version published is a beta (1.0.0-beta*, 1.1.1-beta, 1.2.0-beta, 1.3.0-beta). **No stable version has ever existed** — 1.3.0-beta is not a downgrade from an available stable, it is the only coordinate on the 1.3.0 train (published 2026-09-23, the same day as the D-16 pin).
- [x] Its declared compile dependency is exactly `prompt-executor-openai-client-base-jvm:1.3.0` (jar .module metadata) — the same version koog-agents-jvm already pins transitively. Gradle `dependencyInsight` (runtimeClasspath): `prompt-executor-openai-client-base-jvm:1.3.0` resolves as a **single version** via both koog-agents-jvm and the deepseek artifact — the frozen 1.3.0 pin is not violated. Its runtime deps (`kotlin-stdlib 2.3.10`, `kotlin-logging 8.0.01`) sit below/equal what the project already resolves (Kotlin plugin 2.3.21; direct `kotlin-logging-jvm:8.0.4`), so the one-line addition moves no other version.
- [x] The addition is minimal (one `implementation` line, `build.gradle.kts:31`, commented) and is the only way to satisfy CONTRACT-LLM-002's DeepSeek binding against the pinned 1.3.0 surface.

**(b) No `.deepSeek()` on the pinned builder — `addClient(...)` → `MultiLLMPromptExecutor` dispatch verified.**

- [x] `javap` on `prompt-executor-model-jvm-1.3.0.jar`: `PromptExecutorBuilder` methods are `addClient`, `openAI(...)`, `anthropic(...)`, `ollama(...)`, `fallback`, `build` — **no `deepSeek`**. The scaffold's method list is exact.
- [x] Wiring chain verified in 1.3.0 sources: `addClient(client)` appends to the list; `build()` with a single client returns `MultiLLMPromptExecutor(clients)` (list constructor maps `llmProvider() → client`); `resolveModel` requires `model.provider in llmClients`; `DeepSeekLLMClient.llmProvider()` returns `LLMProvider.DeepSeek` and the client-side model is built with `LLMProvider.DeepSeek` (`DeepSeekLlmClient.kt:56-61`) — dispatch matches, and the passing MockEngine tests exercise this exact path end to end.

**(c) Constructor wording — the implementation follows the jar, D-16's wording was wrong.**

- [x] Source jar of the deepseek client: secondary constructor `DeepSeekLLMClient(apiKey: String, settings: DeepSeekClientSettings = DeepSeekClientSettings(), httpClientFactory: KoogHttpClient.Factory, clock = …, toolsConverter = …)` — `httpClientFactory` has **no default** (the injectable MockEngine seam is mandatory, same shape as T13); there is **no `baseUrl` parameter**. The base URL lives in `DeepSeekClientSettings(baseUrl = "https://api.deepseek.com", chatCompletionsPath = "chat/completions", modelsPath = "models", timeoutConfig = …)`, all defaults matching the DeepSeek API base.
- [x] The D-16 text `DeepSeekLLMClient(apiKey, baseUrl = "https://api.deepseek.com")` is therefore factually wrong in shape; the deviation is correctly documented in `docs/tasks/T14.md` (Scaffold §1–3) and `DeepSeekLlmClient.kt:16-26`. `AppConfig` stays untouched (only pre-existing `DEEPSEEK_API_KEY`/`DEEPSEEK_MODEL` keys are used, `DeepSeekLlmClient.kt:42-61`).

### 3. Capabilities fix — required, jar-evidenced, correctly scoped

- [x] `AbstractOpenAILLMClient.getResponse` (openai-client-base 1.3.0 sources) calls `model.requireCapability(LLMCapability.Completion)` unconditionally and `requireCapability(LLMCapability.Tools)` when `tools.isNotEmpty()` — and `LLModel.supports` is `capabilities?.contains(capability) ?: false` (prompt-llm 1.3.0), so an unset capability list fails every run on this path. `listOf(LLMCapability.Completion, LLMCapability.Tools)` (`DeepSeekLlmClient.kt:56-61`) is exactly the required set for the text+tools path — no over- or under-provisioning. (The Ollama path does not require capabilities; `OllamaLlmClient.kt:38` is unchanged and its tests pass.)

### 4. Bearer `api_key` auth and wire surface — verified in sources and by the tests

- [x] `AbstractOpenAILLMClient.createConfiguredHttpClient` passes `headers = mapOf("Authorization" to "Bearer $apiKey")`; `KtorKoogHttpClient.Factory.create` applies them via `defaultRequest` (plus `Content-Type: application/json` and base-URL normalization ensuring `https://api.deepseek.com/` + `chat/completions`). The test asserts `url.protocolWithAuthority == "https://api.deepseek.com"`, `encodedPath == "/chat/completions"`, and `Authorization == "Bearer test-deepseek-key"` on the captured request (`DeepSeekLlmClientTest.kt:141-149`).
- [x] Response decode gotcha verified in sources: `DeepSeekChatCompletionResponse.systemFingerprint` is a **non-null `String`** and the client JSON uses `SnakeCase` + `ignoreUnknownKeys` + `isLenient` — the test replies carry `system_fingerprint` (present in all three recipes) and the decode passes.
- [x] No network in tests: the factory is the required constructor parameter and the only HTTP path, so every call goes through `MockEngine` (test-engineer gate reached the same conclusion independently).
- [x] Production HTTP path viable: koog-agents-jvm 1.3.0 pulls `ktor-client-apache5-jvm:3.3.3`, so `KtorKoogHttpClient.Factory()` (bound at `ClientModule.kt:14`) resolves a real engine on the runtime classpath.

### 5. No credentials in code or logs

- [x] Grep over the T14 files for `api_key|secret|password|token|sk-`: only test-fixture dummies (`test-deepseek-key`, `test-db-password` in `DeepSeekLlmClientTest.kt:50-53`, `DiSmokeTest.kt:45-46`). No logging statements in `infra/llm` main code; nothing logs config values; the API key is only ever placed into the request header by the pinned Koog code. `.env` is gitignored; only `.env.example` is tracked, and `AppConfig` reads keys at construction without echoing them.

### 6. `infra/llm` references only Koog types and its own config

- [x] Import sweep over `src/main/kotlin/com/fuelingchat/infra/llm/`: only `ai.koog.*`, `org.kodein.di.*` (DI module), `kotlinx.serialization` (ChatMessage), and `com.fuelingchat.config.AppConfig` (the sanctioned own-config reference per spec §3). Zero `com.fuelingchat.service.*` imports.

### 7. No `LlmClient` stub remains in the DI graph

- [x] `grep "bind<LlmClient>" src/main/kotlin` returns exactly the two real bindings: `ClientModule.kt:18` (`local` → `OllamaLlmClient`) and `ClientModule.kt:19` (`remote` → `DeepSeekLlmClient(instance(), instance())`). No other module binds `LlmClient`; the previous `error("LlmClient 'remote' lands with the D-16 branch task")` stub is gone.
- [x] `DiSmokeTest.kt:33-34` resolves both named bindings through `AppModule` (the deferred T12 assertion) and passes live (below).

### 8. D-8 / D-9 and read-only DB

- [x] D-8: the tool-set path is untouched — `KoogChatRunner` still builds the registry solely from the caller-supplied `tools`; the remote test proves the tool wire schema (`{"type":"function","function":{"name","description","parameters"}}`, `fueling_id` required) and the role-`"tool"` result round-trip through the DeepSeek client (`DeepSeekLlmClientTest.kt:158-181, 220-235`). Never-empty enforcement stays in the service (unchanged).
- [x] D-9: no service/system-prompt logic in this diff; nothing can bypass the branching layer at the client.
- [x] Read-only DB: no DB code in this diff; no DDL/DML anywhere; `readOnlyMode=always` and `connectionInitSql` infrastructure untouched.

## Independent verification

- **Live test run** (forced: default `test` was FROM-CACHE, so re-ran with `--rerun`): full suite **71 tests / 0 failures / 0 errors / 0 skipped** across 14 classes; fresh XML timestamps — `DeepSeekLlmClientTest` **2/0/0** (2026-10-14T00:24:42Z), `DiSmokeTest` **1/0/0** (00:24:31Z), `OllamaLlmClientTest` **2/0/0**.
- `./gradlew build ktlintCheck detekt` → **BUILD SUCCESSFUL** (AC "0 errors" holds).
- `./gradlew dependencyInsight` (runtimeClasspath): `prompt-executor-deepseek-client-jvm:1.3.0-beta` present solely via the direct declaration; `prompt-executor-openai-client-base-jvm:1.3.0` single-version (koog-agents-jvm + deepseek artifact) — no conflict.
- `javap`/source inspection of the pinned jars: `koog-agents-jvm:1.3.0` (deepseek absent), `prompt-executor-model-jvm:1.3.0` (builder/`MultiLLMPromptExecutor`), `prompt-executor-deepseek-client-jvm:1.3.0-beta` (constructor/settings), `prompt-executor-openai-client-base-jvm:1.3.0` (Bearer auth, capability checks, JSON config), `prompt-llm-jvm:1.3.0` (`LLMProvider.DeepSeek`, `LLMCapability.Completion/Tools`, `LLModel.supports`), `http-client-ktor-jvm:1.3.0` (factory/`defaultRequest`), `ktor-client-mock-jvm:3.3.3` (`MockRequestHandleScope(CoroutineContext)` exists — the helper's `Job()` is a valid context).
- Maven Central `maven-metadata.xml`: `<release>1.3.0-beta</release>`, no stable 1.3.0.

## Findings

None blocking.

The three D-16 deviations are adjudicated **justified, minimal, and consistent with D-16's "pinned jars" intent**: the DeepSeek client was never part of the koog-agents distribution and has no stable release (only the 1.3.0-train beta), its transitive deps are already pinned at identical versions, the `addClient` wiring is the only public path on the pinned builder, and the constructor follows the actual jar rather than D-16's inaccurate wording. All T14 acceptance criteria items are met and independently verified.

### Non-blocking observations

- `ServiceModule.kt:23-24` — comment "The named LlmClient bindings stay error() stubs until T13/T14" is now stale (comment only; file untouched by this commit; no functional stub remains — see §7).
- `ChatService.kt:20` — pre-existing `TODO(T12)` doc line, outside this diff.
- `KoogChatRunner.kt:48-51` — role `when` without `else` (pre-existing; noted at T13's gate; production roles are the closed `user`/`assistant` set).
- The scratch extraction directories used for jar inspection were removed; the tree is left as found (only `metrics/cto-ledger.md` dirty plus the reviewers' untracked outputs).
