VERDICT: PASS

# T14 — Correctness Review

**Timestamp:** 2026-10-13T15:18:45Z
**Target:** commit `6aae421` (`T14: Koog LlmClient 'remote' (DeepSeek)`), diff vs parent `3c9f4c0`.
**Working tree:** `src/` and `build.gradle.kts` byte-identical to `6aae421` (`git diff 6aae421 --quiet -- src build.gradle.kts` clean; only `metrics/cto-ledger.md` modified, `reviews/T14-test-engineer.md` untracked), so the reviewed code equals the committed code.
**Input gate:** `reviews/T14-test-engineer.md` — PASS (present, this round).

## Files under review

- `src/main/kotlin/com/fuelingchat/infra/llm/DeepSeekLlmClient.kt` (new, 41 lines)
- `src/main/kotlin/com/fuelingchat/infra/llm/ClientModule.kt` (one line: `remote` stub → `DeepSeekLlmClient`)
- `build.gradle.kts` (one dependency + one deviation comment)
- `src/test/kotlin/com/fuelingchat/infra/llm/DeepSeekLlmClientTest.kt` (new), `FakeEchoTool.kt` (pure move), `OllamaLlmClientTest.kt` (class removed only), `src/test/kotlin/com/fuelingchat/DiSmokeTest.kt` (two assertions added)

## Contract verification — CONTRACT-LLM-002 (spec §3 seam + D-8 + D-16, `remote` binding)

- **Pinned seam untouched.** `LlmClient.kt`, `ChatMessage.kt` and `KoogChatRunner.kt` are not in the diff. `DeepSeekLlmClient` implements exactly `run(systemPrompt, history, userMessage, tools): String` and delegates verbatim to the shared `KoogChatRunner` (`: String = runner.run(...)`) — no duplicated run logic, parity with T13's `OllamaLlmClient`.
- **D-8 tool-set contract.** The never-empty invariant lives in `ChatService` (`tools = listOf(fuelingInfoTool)`) and `KoogChatRunner` forwards the list into `ToolRegistryBuilder().tools(tools).build()`; both are byte-identical to the T12/T13-reviewed versions. The new test proves the contract for the remote transport on the wire: `tools[0].type == "function"`, `function.name == "fake_echo"`, `parameters.type == "object"`, `properties.echo_id.type == "string"`, `required == ["echo_id"]`, and the tool-call reply drives the same fake-tool loop (two requests, `tool`-role message with `tool_call_id == "call-test-1"` and the executed tool's `FIXED_RESULT`).
- **AC "constructed from AppConfig; same injectable HTTP-client factory".** `DeepSeekLlmClient(config: AppConfig, httpClientFactory: KoogHttpClient.Factory)` reads `config.deepseekApiKey` / `config.deepseekModel` (both `requiredValue` keys — fail-fast preserved) and receives the same `koogHttpClientFactory()` helper from `ClientModule` as `local`. The test drives the factory path end-to-end: the env file values (`test-deepseek-model`, `test-deepseek-key`) are exactly the values asserted on the outgoing wire, so config wiring is under test, not the mock.
- **AC "no `LlmClient` stub remains".** `ClientModule` binds `remote` to `DeepSeekLlmClient(instance(), koogHttpClientFactory())`; `grep -rn 'error("' src/main/kotlin` → no matches. `DiSmokeTest` resolves both tags through `AppModule` (the T12 `error(...)` stub would throw at singleton creation — the test cannot pass with the stub).
- **D-10 / D-9.** Provider selection is unchanged (`ProviderRouter` tags `local`/`remote`, route normalizes absent → `local`); `ChatService` builds the same system prompt (including the D-9 `attached fueling_id: …` branch) before handing it to whichever binding was selected, so the D-9 branching semantics are provider-independent and untouched by this diff.
- **Infra layering.** `DeepSeekLlmClient` imports only `ai.koog.*` types and its own `com.fuelingchat.config.AppConfig` — no service types, no infra → service dependency; matches spec §3's "infra references only Koog + its own config".

## D-16 surface verification against the pinned jars (four documented wording deviations)

Verified against `~/.gradle/caches/.../prompt-executor-deepseek-client-jvm-1.3.0-beta` (sources + binary), `prompt-executor-model-jvm-1.3.0`, `prompt-llm-jvm-1.3.0`, `prompt-executor-openai-client-base-jvm-1.3.0`, `prompt-executor-clients-jvm-1.3.0` (javap), and Maven Central metadata (live HTTP).

1. **`DeepSeekClientSettings` base URL (D-16 writes `DeepSeekLLMClient(apiKey, baseUrl = "https://api.deepseek.com")`).** The jar has no `baseUrl` parameter on the client: the apiKey constructor is `DeepSeekLLMClient(apiKey: String, settings: DeepSeekClientSettings = DeepSeekClientSettings(), httpClientFactory: KoogHttpClient.Factory, …)`. `DeepSeekClientSettings` defaults are exactly `baseUrl = "https://api.deepseek.com"`, `chatCompletionsPath = "chat/completions"` — the URL D-16 pins. The code passes `apiKey` + `httpClientFactory` and takes the settings default; the MockEngine test asserts host `api.deepseek.com` and path `/chat/completions`, so the effective base equals D-16's. **Behaviorally equivalent** (same endpoint), and the exact version pin (`1.3.0-beta`, no dynamic range) plus the wire assertion prevent silent drift.
2. **Artifact version `1.3.0-beta` (D-16 "Koog pinned to 1.3.0").** Live Maven Central metadata: `prompt-executor-deepseek-client-jvm` `latest = release = 1.3.0-beta` (lastUpdated 2026-09-23) — no stable `1.3.0` exists for the DeepSeek transport; `koog-agents-jvm` latest stable is `1.3.0` (same day). The beta POM depends on `prompt-executor-openai-client-base-jvm:1.3.0`, so the DeepSeek module was compiled against the stable base and the runtime graph has no Koog version skew (the test-engineer's `testRuntimeClasspath` report shows every other `ai.koog` artifact at `1.3.0`). The deviation is documented in a `build.gradle.kts` comment. **Behaviorally equivalent** — core Koog stays 1.3.0 per D-16; the transport module simply has no non-beta publication.
3. **`.addClient` registration.** `PromptExecutor.builder()` and `addClient(LLMClient)` exist in `prompt-executor-model-jvm:1.3.0` (javap); the builder's only convenience constructors are `openAI`/`anthropic`/`ollama` — there is no `deepseek(...)` method, so `addClient` is the sanctioned path. With one client, `build()` returns a `MultiLLMPromptExecutor` keyed by `LLMClient.llmProvider()`; `DeepSeekLLMClient.llmProvider()` returns `LLMProvider.DeepSeek`, which is exactly the `LLModel(provider = LLMProvider.DeepSeek, …)` provider, so dispatch resolves without a fallback. **Behaviorally equivalent**, proven by the green MockEngine tests.
4. **`LLMCapability.Completion + Tools` declaration.** `AbstractOpenAILLMClient.requireCapability` (bytecode) throws `IllegalArgumentException` unless the model declares the capability; the OpenAI-compatible execution path requires `Completion` and — when the request carries tools — `Tools`; `LLModel`'s `capabilities` defaults to empty, so a bare `LLModel` would fail every request. The DeepSeek preset models declare a superset (`Completion, Temperature, Tools, ToolChoice, Schema.JSON…, MultipleChoices, Thinking`), but no other capability is ever `require`d on the app's path (non-streaming, single choice, no schema, no tool-choice override), and the model id is user-configured (`DEEPSEEK_MODEL`) so presets cannot be used. Declaring exactly the two used capabilities is **necessary and sufficient** and behaviorally equivalent; T13's `Ollama` path is unaffected (its client is a different implementation).

## Failure/exception propagation vs the T12 classification layer

- On HTTP-level failure the DeepSeek path surfaces `ai.koog.http.client.KoogHttpClientException` (thrown by `KtorKoogHttpClient.processResponse`; `LLMClientException` exists in `prompt-executor-clients:1.3.0`), both of which `ChatServiceImpl.classifiedFailure` maps to `ChatFailure(502, "Provider unreachable")`, alongside `AIAgentException`. The DeepSeek client introduces no new exception types beyond Koog's.
- `ModelResolutionException`/capability `IllegalArgumentException` would fall through to the route's generic masked 500; both are unreachable on this binding by construction (client provider == model provider; capabilities declared), same posture as T13.
- `ES-001` (open escalation, F1: Koog catches tool-loop exceptions below the seam) applies to the remote provider too because the shared `KoogChatRunner` is used, but this diff does not touch the tool-call path; it is not re-litigated or charged here.

## Secrets and masking

- The API key flows only into the HTTP `Authorization` header: `createConfiguredHttpClient` builds `mapOf("Authorization" to "Bearer $apiKey", …)` (bytecode-verified), and the test asserts exactly `Bearer test-deepseek-key` on the wire.
- No credential or provider text in messages/logs: failure responses are compile-time constants (`"Provider unreachable"`); the `KoogHttpClientException.errorBody` (provider text) is neither logged by the app nor returned; Koog's own client-side logging is `debug`-level (only the close path) while the root logger is `INFO`, and the logback `MaskingTurboFilter` + `SecretMasker` (Bearer / `api_key|password|secret|token` / `DEEPSEEK_API_KEY=`) cover any future line containing the key. `grep` of the diff: no real secrets, no `println`/`System.out`/`printStackTrace` in the new code; test-only fake literals (`test-deepseek-key`).
- Provider error text/credentials never reach the client response (route responds `failure.maskedMessage`); no logging added in the diff.

## Read-only DB / DDL-DML

No DB code, SQL, DDL or DML in the diff; `DbPools` (`isReadOnly = true` + `connectionInitSql = "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY"`) untouched.

## Independent verification (this round)

- `./gradlew test --tests "com.fuelingchat.infra.llm.DeepSeekLlmClientTest" --tests "com.fuelingchat.DiSmokeTest" --rerun --no-build-cache` → **exit 0**; XML results: `DeepSeekLlmClientTest` 2 tests / 0 failures / 0 errors (0.112 s — MockEngine, no network), `DiSmokeTest` 1 / 0 / 0 (1.33 s, both `LlmClient` tags resolve through `AppModule`). The bulk gate (`build ktlintCheck detekt`, 72 tests) is the test-engineer's this-round PASS; not re-run here beyond this targeted confirmation.

## Findings

None blocking. No logic error, edge-case failure, contract violation, unmasked leak or DDL/DML in the reviewed diff.

## Non-blocking observations (for the record / downstream gates)

1. The DeepSeek base URL is implicit (Koog's `DeepSeekClientSettings()` default) rather than written out as `DeepSeekClientSettings(baseUrl = "https://api.deepseek.com")`; exact version pinning plus the host/path wire assertion make silent drift a caught failure, but an explicit settings value would make the D-16 endpoint literal in code.
2. `ModelResolutionException` and capability `IllegalArgumentException` map to the generic masked 500 rather than 502 — unreachable by construction; consistent with T13's shape and the T12 classification decision.
3. Test tightening opportunities already recorded by the test-engineer (scheme `https` not asserted; `DiSmokeTest` asserts non-nullity only, not the concrete binding type) — not correctness defects.
4. Per-binding `HttpClient(Apache5)` is created twice (one per named binding) and never closed — carried forward from the T13 review; the factory helper predates this diff and is unchanged.
5. Bookkeeping: `docs/tasks/T14.md` keeps `**Status:** in progress` / unchecked AC boxes with an appended `STATUS: DONE`, and `docs/tasks/INDEX.md` still lists T14 as `todo` — the status flip belongs to the CTO after all five gates (process note, outside this gate).

## Verdict

T14 done | verified: ok — the `remote` binding matches CONTRACT-LLM-002 (spec §3 seam, D-8 tool-set, D-10 selection, D-9 untouched), the four D-16 wording deviations are each jar-verified and behaviorally equivalent, the DeepSeek failure path classifies to 502 per T12, no credentials or provider text leak, the DI stub is gone, and the deepseek MockEngine + DI smoke tests pass on an independent rerun.
