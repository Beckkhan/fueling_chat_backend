# T14 — Test Engineer Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T18:15:10Z

## Scope

Commit `143066f` ("T14: Koog LlmClient 'remote' (DeepSeek)", 6 files) reviewed against `docs/tasks/T14.md` and CONTRACT-LLM-002 (spec §3 pinned `LlmClient` seam; D-8 tool-set contract, D-16 Koog-surface deviations). Reviewed paths: `DeepSeekLlmClient.kt`, `ClientModule.kt`, `build.gradle.kts`, `docs/tasks/T14.md` (main) and `DeepSeekLlmClientTest.kt`, `DiSmokeTest.kt` (tests). Working tree matches the commit for all reviewed paths — only out-of-scope `metrics/cto-ledger.md` is modified and the stray `.env.example.swp` (live vim artifact) is untracked.

## Verification performed

- Read the full diff of `143066f`; committed test sources are what the gate ran against (`git diff HEAD` empty for all reviewed paths).
- `./gradlew cleanTest test` then `./gradlew test --rerun` (genuine execution, not cache): BUILD SUCCESSFUL in 30s, `> Task :test` executed.
- Flakiness re-run: `./gradlew test --tests "com.fuelingchat.infra.llm.DeepSeekLlmClientTest" --tests "com.fuelingchat.DiSmokeTest" --rerun` — BUILD SUCCESSFUL again.
- Exact AC command: `./gradlew build ktlintCheck detekt --rerun-tasks` — BUILD SUCCESSFUL, 16 tasks executed, exit 0; fresh `detekt.xml` contains 0 errors; ktlint source-set checks executed and passed.
- JUnit XML (fresh): 73 tests, 0 failures, 0 errors, 0 skipped across 14 classes. T14 classes: `DeepSeekLlmClientTest` 2/2 (`plainTextReplyYieldsFinalText`, `toolCallReplyDrivesAgentLoopAndReturnsFinalAnswer`), `DiSmokeTest` 2/2 (incl. new `bothNamedLlmClientsResolveThroughAppModule`).
- Mock fidelity checked against the pinned jars/sources:
  - `prompt-executor-deepseek-client-jvm-1.3.0-beta.jar` contains `ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient`; its constructor is `(apiKey, settings, httpClientFactory, clock, schemaGenerator)` with defaults — matching the implementation and confirming the documented D-16 wording deviation. `DeepSeekClientSettings` defaults are `baseUrl = "https://api.deepseek.com"`, `chatCompletionsPath = "chat/completions"` (sources jar), so the test's host/path assertions pin the real defaults.
  - The reply envelope's required fields match the pinned `DeepSeekChatCompletionResponse` (`choices`, `created`, `id`, `model`, `systemFingerprint`; `object` defaults) — the MockEngine replies include `system_fingerprint`, so they exercise the real decoder, not a lenient one.
  - Bearer auth verified in `prompt-executor-openai-client-base-jvm-1.3.0` sources (`AbstractOpenAILLMClient.createConfiguredHttpClient`: `headers = mapOf("Authorization" to "Bearer $apiKey")`), and `getResponse` requires `LLMCapability.Completion` plus `LLMCapability.Tools` when tools are non-empty — exactly what `DeepSeekLlmClient`'s `LLModel` carries and what the tests exercise.
  - POM of the beta DeepSeek module depends on `prompt-executor-openai-client-base-jvm:1.3.0` — core Koog is not bumped off the pin.
- Isolation audit of the new/changed tests: JUnit per-method lifecycle (no `@TestInstance`/`@BeforeAll`/`@AfterAll`/ordering annotations); captured `requests` lists are test-local; `@TempDir` is fresh per test; companion members are immutable constants; `FakeEchoTool` is stateless; `AppConfig` reads only the `.env` file it is handed (never the process environment), so no shell-env or cross-test pollution.
- Flakiness audit of the T14 files: no sleeps, delays, wall-clock or randomness; MockEngine branching is a deterministic `requests.size` check; reply payloads are fixed. No `@Disabled` / `Assume` / `TODO` in the T14 sources or tests.
- Network audit: every DeepSeek test injects `KtorKoogHttpClient.Factory(baseClient = HttpClient(MockEngine { … }))`; an implementation that ignored the seam would capture zero requests and fail the `requests.size` assertions (and a real call with a fake key would 401) — passing tests prove no socket was opened. DI smoke resolution only constructs clients; nothing performs requests.

## Acceptance criteria check (test view)

- [x] `DeepSeekLlmClient : LlmClient` from `AppConfig` (`DEEPSEEK_API_KEY`, `DEEPSEEK_MODEL`): the test feeds both through a temp `.env` and asserts `Bearer $API_KEY` and the configured model id appear on the wire.
- [x] Same injectable HTTP-client factory as T13 and same `run` contract: constructor shape mirrors `OllamaLlmClient`; the captured requests prove the injected factory is actually used; turn assembly delegates to the shared `KoogChatRunner` (no duplicated run logic in `DeepSeekLlmClient` — run body is a one-line delegation).
- [x] Koog DeepSeek surface deviations documented: the D-16 deviations (`settings`-carried base URL, `addClient` instead of a `deepSeek(...)` builder member, `1.3.0-beta` module) are documented in the `DeepSeekLlmClient` KDoc, the commit message and the `build.gradle.kts` comment; independently spot-verified against the pinned jars above. Spec §3 seam and D-8 tool-set contract untouched (no spec/decision files in the diff).
- [x] `ClientModule` binds tag `remote` to `DeepSeekLlmClient`, last `LlmClient` stub removed: `error()` binding gone; grep confirms no other `LlmClient` stub in `src/main`.
- [x] `DiSmokeTest` extended: both named `LlmClient` bindings resolve through `AppModule` (a remaining stub would throw and fail the test). Closes T13 review observation 1.
- [x] MockEngine test (no network): outgoing request asserted to `api.deepseek.com` `/chat/completions` with `Authorization: Bearer <key>`, configured model id, `stream == false`, snake_case `messages`/`tools`; the tool-call reply drives the same fake-tool loop as T13 — exactly 2 requests, second request's last message `role = "tool"` with `FakeEchoTool.FIXED_RESULT` and `tool_call_id = call-test-1`, final answer returned. Proves the D-8 tool-set contract for the remote provider.
- [x] `./gradlew build ktlintCheck detekt` — 0 errors (forced re-run: 16 tasks executed, detekt report fresh with 0 findings).

## Observations (non-blocking)

1. `DiSmokeTest.bothNamedLlmClientsResolveThroughAppModule` asserts non-null resolution but not the concrete class; asserting `OllamaLlmClient`/`DeepSeekLlmClient` types would be marginally stronger. The stub-detection purpose of the AC is still served (an `error()` binding fails the test).
2. The tool-loop test asserts the echo/final answer but not its own first request's URL/model (covered by the plain-reply test for the same code path).
3. Request method (`POST`) is not asserted; host, path, header and body are.
4. Stale comment in `src/main/kotlin/com/fuelingchat/service/ServiceModule.kt` (lines 21–23: named `LlmClient` bindings "stay error() stubs until T13/T14"; "DiSmokeTest additions for the app graph land in T14") is now inaccurate. Comment-only, outside the commit's scope — flagged for the correctness/style gates, not a test-gate failure.
5. Tests construct `HttpClient` without closing it — the common Ktor MockEngine test pattern; MockEngine holds no OS resources.

## Verdict

`T14-test-engineer: PASS | verified: ok`
