# T13 — Test Engineer Review (first quality gate)

**Verdict:** PASS
**Timestamp:** 2026-10-13T23:58:54Z
**Target:** commit `600144d5e2b25e6f7c5853afe0f58c9e413250ca` (T13: integrate Koog LlmClient 'local' (Ollama)), diff vs parent `c6f34f11d94771301c810487c35dd5db8bac90c6` (9 files, +443/−4; 1 new test file, 241 lines)
**Working tree:** `git diff 600144d -- src/ build.gradle.kts .env.example docs/tasks/T13.md` is empty — the reviewed state matches the commit (only `metrics/cto-ledger.md` is dirty; untracked `reviews/T13-*.md` are the parallel gates' outputs).

## Gate checks

### Tests actually run (cache/up-to-date bypassed)
- First `./gradlew test` reported `:test UP-TO-DATE`; `cleanTest test` reported `:test FROM-CACHE` — both were discarded as stale evidence.
- `./gradlew test --rerun` (forced execution, 41 s): **BUILD SUCCESSFUL — 69 tests / 0 failures / 0 errors / 0 skipped**, fresh timestamps 23:57Z. Per class: `OllamaLlmClientTest` 2/2 (ts 23:57:55.469Z), `BootFailFastTest` 4/4, `AppConfigTest` 13/13, `DiSmokeTest` 1/1, `DbInfrastructureTest` 5/5, `LogbackConfigurationTest` 3/3, `MaskingTest` 5/5, `ChatRoutesTest` 8/8, `ChatServiceTest` 9/9, `DialogHistoryTest` 4/4, `ProviderRouterTest` 4/4, `GetFuelingInfoToolIntegrationTest` 8/8, `ToolDescriptorLoaderTest` 3/3.
- Flakiness probe: 3 × `./gradlew test --tests "com.fuelingchat.infra.llm.OllamaLlmClientTest" --rerun` — 2/2 green each run (fresh XML timestamps 23:58:11.717Z / 23:58:14.934Z / 23:58:17.668Z, ~2–3 s each). No flakiness observed.
- The DB-backed tests in the suite ran against the local Docker `postgres:15-alpine` container on 25432 (never a production DB); unchanged by this diff.

### No Ollama running, no real network (verified, not assumed)
- Port 11434 had **no listener** for the whole session (`curl` → connection refused, `lsof` → none). Had Koog bypassed the injected factory, its POSTs to `http://localhost:11434/api/chat` would have failed with connection-refused; the tests pass and the captured request list is non-empty, so every HTTP call was served by the `MockEngine`.
- Mocking is on the injectable seam exactly as the AC requires: `OllamaLlmClientTest.kt:106-109` builds `KtorKoogHttpClient.Factory(HttpClient(MockEngine { … }))` and passes it to `OllamaLlmClient(config, factory)`; production (`ClientModule.kt`) is the only place `KtorKoogHttpClient.Factory()` is constructed.
- No `Thread.sleep`/`delay`/clock/random in any new or changed T13 file (the only `Thread.sleep` in the repo is pre-existing `BootFailFastTest.kt:135`, outside this diff).

### Gate item → test mapping
1. **Plain text reply yields final text** — `OllamaLlmClientTest.kt:128-143`: `assertEquals(PLAIN_ANSWER, runFakeTurn(client))` and `requests.single()` pins exactly one outgoing call; the reply recipe (`:93-94`) is the Ollama native `{"message":{"role":"assistant","content":…},"done":true}`.
2. **Tool-call reply drives the agent loop, fake tool's fixed text sent back, final answer returned (D-8 proof)** — `:152-172`: first reply is a tool call to `fake_tool` (`:99-101`); the run returns `FINAL_ANSWER` after the loop, exactly 2 requests are made, and `assertToolResultSentBack` (`:213-225`) requires exactly one `role == "tool"` message in the second request whose `content == "fixed-tool-text"` — i.e. `FAKE_TOOL_OUTPUT` produced by the fake tool's `execute()` (`:82`). Since `fake_tool` is only supplied via `runFakeTurn`'s `tools = listOf(fakeTool)` (`:174-180`), this proves the service-supplied tool set is wired into the Koog registry, executed, and fed back — the D-8 invariant.
3. **Outgoing request asserts model id and tool schema** — `assertChatRequest` (`:182-188`) asserts base URL (`config.ollamaBaseUrl`), path `/api/chat`, and `body.model == config.ollamaModel`; `assertToolSchema` (`:193-211`) asserts the `tools` array has exactly one entry with `type == "function"`, `function.name == "fake_tool"`, the description, and a JSON-Schema `parameters` object with `properties.fueling_id` and `required == ["fueling_id"]`. Asserted on the actual wire body parsed from the captured request.

### Isolation and determinism
- JUnit 5 per-method instances: `@TempDir` (`:52-53`) + lazily-initialised `config` (`:57-69`) read a temp env file written inside the test body — the developer's real `.env` is never read; `envFile` resolves under the per-test temp dir (`:227-231`).
- All mutable state is local to each test (`requests`, `replies` ArrayDeque); the companion holds only `const` strings; no `@BeforeAll`/`@AfterAll`, no statics, no order dependence.
- Failure mode of a runaway loop is a fast exception (`ArrayDeque.removeFirst()` on an exhausted reply list) rather than a hang; `assertEquals(2, requests.size)` pins the loop length.
- `jsonResponse` (`:111-118`) builds responses in a `MockRequestHandleScope(Job())` rather than the engine's own call context — a deliberate, documented workaround for the Ktor response-body lifecycle (`DefaultHttpResponse.coroutineContext = responseData.callContext`, verified against the ktor-3.3.3 sources); the detached Job never completes, so it is deterministic under the pinned `3.3.3`.

### Supporting test-scope changes in the diff
- `build.gradle.kts:45-46` — adds only `testImplementation("io.ktor:ktor-client-mock-jvm:3.3.3")` (aligned with Koog 1.3.0's transitive Ktor 3.3.3).
- `AppConfigTest.kt:59` — the default assertion updated `…:11434/v1` → `…:11434` to match the corrected default, as the scaffold's pinned-jar verification requires; the explicit-override path still asserts `…/v1` passthrough (`:82`, `:101`), so both default and override behaviours remain pinned. This is a tracked expectation change, not a weakened test.

## Findings

None — no blocking findings. Verdict is PASS.

## Non-blocking notes

1. **History role-mapping branch has no coverage.** `runFakeTurn` always passes `history = emptyList()` (`OllamaLlmClientTest.kt:177`), so the `when (message.role) → user/assistant` branch in `KoogChatRunner.kt:37-40` never executes in any test. The T13 acceptance-criteria test list does not require it, and the seam itself is covered by both tests — but `KoogChatRunner` is the shared turn assembly T14 reuses, so a single test with non-empty history asserting the outgoing `messages` roles (`system` → `user`/`assistant` → `user`) would close it at/with T14.
2. `OllamaLlmClientTest.kt:111-118` — the detached-`Job()` response helper is tied to ktor 3.3.3 internals; revisit on any Ktor upgrade even though the dependency is pinned.
3. `OllamaLlmClientTest.kt:106-109` — the `HttpClient(MockEngine …)` instances are not closed; harmless with MockEngine (no sockets/threads), no leakage in practice.
4. Informational for the correctness gate: the tool-loop run logs `WARN OllamaConverters — Skipping unsupported message part: Result(…)`, yet the wire assertion proves the tool result is present as the `role == "tool"` message with the exact tool text — behaviour on the wire is correct despite the Koog warning.
