# T14 — Test Engineer Review (first quality gate)

**Verdict:** PASS
**Timestamp:** 2026-10-14T00:24:13Z
**Target:** commit `3e258327a2f9f450db5d4959dca37cb2f45ca829` (T14: integrate Koog LlmClient 'remote' (DeepSeek)), diff vs parent `9950619b62ced66ecd85c7e79c2edcc2c0b4e360` (6 files, +467/−2; 1 new source file, 1 new test file 299 lines)
**Working tree:** `git diff HEAD -- src/ build.gradle.kts docs/tasks/T14.md` is empty — the reviewed state matches the commit (only `metrics/cto-ledger.md` dirty, pre-existing; untracked `reviews/T14-*.md` are the parallel gates' outputs; untracked `pem-src/` is unrelated to T14).

## Gate checks

### Tests actually run (cache/up-to-date bypassed)
- First `./gradlew test` reported `:test UP-TO-DATE` — discarded as stale evidence.
- First `./gradlew test --rerun` failed the *task* with "Could not write XML test results" for 7 classes (not a test failure). Root cause investigated: a concurrent `./gradlew ktlintFormat --rerun-tasks` from the parallel code-style gate was running against the same project directory (PID observed, competing Gradle daemon). Environmental collision, not a product defect.
- Re-ran on a quiet tree (`--no-daemon`): **BUILD SUCCESSFUL in 46 s — 71 tests / 0 failures / 0 errors / 0 skipped**, fresh XML timestamps 2026-10-14T00:22:55Z–00:23:25Z. Per class: `DeepSeekLlmClientTest` 2/2 (ts 00:23:24.259Z), `DiSmokeTest` 1/1 (ts 00:23:02.940Z), plus the other 12 classes all green (BootFailFast 4, AppConfig 13, DbInfrastructure 5, OllamaLlmClient 2, LogbackConfiguration 3, Masking 5, ChatRoutes 8, ChatService 9, DialogHistory 4, ProviderRouter 4, GetFuelingInfoToolIntegration 8, ToolDescriptorLoader 3).
- Flakiness probe: 3 × targeted `--rerun` of `DeepSeekLlmClientTest` + `DiSmokeTest` — 2/2 and 1/1 green each run (fresh timestamps 00:23:47Z / 00:23:50Z / 00:23:53Z). No flakiness observed.
- The DB-backed tests in the suite (`DbInfrastructureTest`, pre-existing, unchanged by this diff) ran against the local Docker container on 25432 — never a production DB.

### No network — MockEngine on the injectable seam (verified structurally)
- `DeepSeekLlmClientTest.kt:128-131` builds `KtorKoogHttpClient.Factory(HttpClient(MockEngine { … }))` and passes it to `DeepSeekLlmClient`; the factory is a REQUIRED constructor parameter (`DeepSeekLlmClient.kt:71-76`) forwarded to `DeepSeekLLMClient(…, httpClientFactory = …)` (`:196-200`), so the provider cannot bypass it — the same seam shape T13's gate accepted.
- The captured `requests` lists are populated only inside the MockEngine handler, and the tests assert `requests.single()` (plain) / exactly 2 requests (tool loop); had the mock been bypassed, those assertions or the run itself would fail — every HTTP call provably went through the MockEngine. No Ollama/DeepSeek server or account is involved; the DeepSeek base URL is only ever the `DeepSeekClientSettings` default asserted on the mock.
- Production is the only place the real factory is built: `ClientModule.kt:14` (`KtorKoogHttpClient.Factory()`); both LLM test files use MockEngine only (grep-checked).

### Gate item → test mapping
1. **Outgoing request targets the DeepSeek API base with Bearer `api_key` auth and the configured model id** — `assertChatRequest` (`DeepSeekLlmClientTest.kt:147-154`): `protocolWithAuthority == "https://api.deepseek.com"`, `encodedPath == "/chat/completions"`, `Authorization == "Bearer ${config.deepseekApiKey}"` (config read from the temp env: `test-deepseek-key`), `body.model == config.deepseekModel` (`deepseek-test-model`). Asserted on every captured request in both tests (`:261`, `:293-294`).
2. **Tool-call reply drives the same fake-tool loop as T13 (D-8 tool-set contract for remote)** — `toolCallReplyDrivesAgentLoopWithHistory` (`:272-298`): first reply is the OpenAI-compatible tool call to `fake_tool` with `function.arguments` as a JSON string (`:116-123`); the run returns `FINAL_ANSWER`, exactly 2 requests; `assertToolSchema` (`:163-181`) pins the D-8 wire contract (`{"type":"function","function":{name,description,parameters}}`, JSON Schema `type: object`, `properties.fueling_id`, `required == ["fueling_id"]`) on the first request; `assertToolResultSentBack` (`:185-198`) requires exactly one `role == "tool"` message in the second request with `content == FAKE_TOOL_OUTPUT` — proving the fake tool executed and its result was fed back. `FakeArgs`/`fakeTool` reuse T13's declarations (no redeclaration), and the run delegates to T13's `KoogChatRunner` unchanged (`DeepSeekLlmClient.kt:215, 227-232`) — no duplicated run logic.
3. **NON-EMPTY history covers KoogChatRunner's history role-mapping path (T13 gate carryover note 1)** — the tool-loop test passes a 2-entry history (`user` + `assistant`, `:285-289`); `assertHistoryMappedByRole` (`:203-223`) proves the first request body carries `system` (`system-prompt`), `user` (`prior-user-text`) and `assistant` (`prior-assistant-text`) entries, bucket-keyed by role — a swapped or dropped mapping would fail the assertion. This closes the T13 carryover.
4. **DiSmokeTest: both named `LlmClient` bindings resolve through `AppModule`** — `DiSmokeTest.kt:32-34` asserts `instance<LlmClient>(tag = "local")` and `tag = "remote"` are both non-null. The `remote` singleton realizes the real `DeepSeekLlmClient` with the production factory at resolution (Kodein `singleton`), so the assertion is the no-stub proof; `ClientModule.kt:19` replaced the `error()` stub and grep confirms no `error("LlmClient…` remains anywhere in `src/main`.
5. **`./gradlew test` passes** — verified fresh on a quiet tree (71/0/0/0) and 3× targeted reruns green.

### Coverage of new/changed code
- New `DeepSeekLlmClient` (all members: `promptExecutor`, `model`, `runner`, `run`) is exercised end-to-end through the pinned `LlmClient` seam in construction + plain reply + tool loop; `run` is a pure delegation covered by both tests.
- `ClientModule` `remote` binding covered by `DiSmokeTest`; no route/service code changed in this diff.
- LLM calls are mocked on the injectable HTTP seam (MockEngine), matching the AC wording and the T13 precedent.

### Isolation and determinism
- JUnit 5 per-method instances: `@TempDir` + lazily-initialised `config` (`:57-74`) read a temp env file written inside the test body — the developer's real `.env` is never read; both bindings resolve from test-local env.
- All mutable state (`requests`, `replies` ArrayDeque) is local to each test; the companion holds only `const` strings; no statics, no `@BeforeAll`/`@AfterAll`, no order dependence.
- No sleeps, delays, clocks, or randomness in any changed file (the only `Thread.sleep` in the repo is pre-existing `BootFailFastTest.kt:135`, outside this diff).
- Runaway-loop failure mode is a fast exception (`ArrayDeque.removeFirst()` on an exhausted list) plus `assertEquals(2, requests.size)` pins the loop length — a hang cannot pass.

## Findings

None — no blocking findings. Verdict is PASS.

## Non-blocking notes

1. **Environment, not code:** a concurrent `./gradlew ktlintFormat --rerun-tasks` (parallel gate) made the first `--rerun` fail with XML-write errors. Gates that need fresh test evidence should ensure a quiet build tree; re-verified clean here.
2. `DeepSeekLlmClientTest.kt:135-140` — the detached-`Job()` `MockRequestHandleScope` response helper is tied to ktor 3.3.3 internals (same accepted workaround as T13 note 2); revisit on any Ktor upgrade.
3. `DeepSeekLlmClientTest.kt:128-131` — the `HttpClient(MockEngine …)` instances are not closed; harmless with MockEngine (no sockets/threads), same as T13 note 3.
4. The tests do not assert the HTTP method (`POST`) — the path/base/auth/model/tool-schema assertions already pin the wire contract; mirror of T13, no action needed.
