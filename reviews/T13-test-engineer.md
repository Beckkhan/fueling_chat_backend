# T13 — Test Engineer Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T17:41:15Z

## Scope

Commit `1c678db` ("T13: Koog LlmClient 'local' (Ollama)", 10 files) reviewed against `docs/tasks/T13.md`, CONTRACT-LLM-001 (spec §3 pinned `LlmClient` seam; D-8, D-16; mapping in `docs/tasks/INDEX.md`). Reviewed paths: `OllamaLlmClient.kt`, `KoogChatRunner.kt`, `ClientModule.kt`, `AppConfig.kt`, `.env.example`, `build.gradle.kts` (main) and `OllamaLlmClientTest.kt`, `FakeEchoTool.kt`, `AppConfigTest.kt` (tests). Working tree matches the commit for all reviewed paths — only out-of-scope `metrics/cto-ledger.md` is modified and the stray `.env.example.swp` (live vim artifact) is untracked.

## Verification performed

- Read the full diff of `1c678db`; committed test sources are what the gate ran against (`git diff HEAD -- src/` empty).
- `./gradlew build ktlintCheck detekt` (exact AC command) — BUILD SUCCESSFUL, exit 0 (16 tasks, all up-to-date on the first invocation, so the gate rests on forced re-runs, below).
- `./gradlew test ktlintCheck detekt --rerun-tasks` — BUILD SUCCESSFUL, 13 tasks executed (compile, test, detekt, all ktlint source-set checks). Fresh detekt report: 0 findings (`detekt.xml` has an empty `<checkstyle>` element); ktlint report dirs contain no non-empty report files.
- `./gradlew test --rerun-tasks` — BUILD SUCCESSFUL (repeatability run). `./gradlew build --rerun-tasks` — BUILD SUCCESSFUL, 16 tasks executed.
- JUnit XML (fresh): 70 tests, 0 failures, 0 errors, 0 skipped. T13 class `OllamaLlmClientTest` 2/2 (0.167s total — no network-timeout latency); `AppConfigTest` 13/13 with the updated default assertion.
- Mock fidelity checked against the pinned Koog 1.3.0 sources jar (`prompt-executor-ollama-client-jvm-1.3.0-sources.jar`): `DEFAULT_BASE_URL = "http://localhost:11434"`, `DEFAULT_MESSAGE_PATH = "api/chat"`; `OllamaChatRequestDTO(model, messages, tools, stream)`, `OllamaToolDTO(type, function{name, description, parameters})`, `OllamaToolCallDTO(function{name, arguments: JsonElement})`; responses carry `model`/`message`/`done`. Tool results are emitted as `role = "tool", content = part.output` (raw text). The MockEngine replies and request assertions reproduce exactly this wire format — the mocks are faithful, not invented.
- Isolation audit of the new tests: JUnit per-method lifecycle (no `@TestInstance`/`@BeforeAll`/`@AfterAll`/ordering annotations); captured `requests` lists are test-local; `@TempDir` is fresh per test; companion members are immutable constants; `FakeEchoTool` is stateless. `AppConfig` reads only its `.env` file (never process environment), so no shell-env pollution.
- Flakiness audit of the new/changed tests: no sleeps, delays, wall-clock or randomness anywhere in `OllamaLlmClientTest`/`FakeEchoTool`/`AppConfigTest`; assertions are exact-value; `runBlocking` over a deterministic MockEngine. The only `Thread.sleep` in the suite is pre-existing in `BootFailFastTest` (untouched by this commit, out of T13 scope).
- Network audit: the tests inject `KtorKoogHttpClient.Factory(baseClient = HttpClient(MockEngine { … }))`; the configured host `http://ollama.test:11434` is not DNS-resolvable, so passing tests prove no socket was opened. No real Ollama process is required or contacted. `ClientModule`'s production `Apache5` factory is never instantiated by any test (no test resolves the `LlmClient` tags — see observation 1).
- Repo hygiene: no `data/` directory exists after the full suite; `git status` unchanged apart from the two out-of-scope files.
- `grep` for `@Disabled` / `Assume` / `TODO` in the T13 sources and tests: none.

## Acceptance criteria check (test view)

- [x] Injectable Koog HTTP-client factory: tests construct `OllamaLlmClient(config, KtorKoogHttpClient.Factory(baseClient = HttpClient(MockEngine { … })))`. The captured-request assertions prove the injected factory is actually used — an implementation ignoring the seam would make zero captured requests and attempt a real connection to an unresolvable host, failing deterministically.
- [x] Turn assembly: exact role sequence `[system, user, assistant, user]` and exact contents `[SYSTEM_PROMPT, HISTORY_USER_BODY, HISTORY_ASSISTANT_BODY, USER_MESSAGE]` asserted on the outgoing body; model id asserted equal to `config.ollamaModel` and `stream == false`; the tool schema is asserted present in the same request (registry wired).
- [x] Tiered model assertion: `LLModel(LLMProvider.Ollama, config.ollamaModel)` is observed through the wire model id, which is the only surface a Kubernetes-external test can see; consistent with D-16's pinned client.
- [x] Plain reply: `run(...)` returns the mocked final text exactly (`assertEquals(PLAIN_REPLY, summary)`); exactly one request is issued.
- [x] Tool-call reply drives the agent loop (D-8): first reply proposes `fake_echo`; the test asserts exactly 2 requests (a runaway loop or a missing second call both fail) and that the second request's last message is `role = "tool"` with `content == FakeEchoTool.FIXED_RESULT` — the fixed text produced by `execute()` travelling through Koog into the next HTTP request. This is behavior-level proof that the service-supplied tool set is wired into the run, not just passed along.
- [x] Outgoing request asserts URL `{base}/api/chat` (native protocol; no `/v1`), model id, roles, and tool schema (`type == "function"`, name, parameter type/`required`).
- [x] `OLLAMA_BASE_URL` default: `AppConfigTest:59` asserts `http://localhost:11434`, matching the pinned jar constant; `.env.example` updated in the same commit (diff line visible). The `/v1` occurrences at `AppConfigTest:82/101` are explicit pass-through fixture values (set and asserted identically), deliberately left and self-consistent — the out-of-scope-but-required edit is minimal and correct.
- [x] `ClientModule` `local` binding: `error()` stub removed; binding is now `singleton { OllamaLlmClient(instance(), koogHttpClientFactory()) }` and compiles against the pinned jars. Not resolved by a test — see observation 1.
- [x] `./gradlew build ktlintCheck detekt` — 0 errors (forced re-runs: test, ktlint and detekt freshly executed; detekt 0 findings).
- [x] New test dependency `io.ktor:ktor-client-mock-jvm:3.3.3` matches the project's Ktor line; test task uses the JUnit Platform and picks the new class up (observed in XML).

## Observations (non-blocking)

1. No test resolves the `LlmClient` tag `local` through DI (DiSmokeTest covers foundation bindings only). The binding is verified by compilation and diff inspection; consistent with the T12 review note that DiSmokeTest additions land in T14. Natural home: T14, when a resolution test would also catch a missing `Apache5` runtime artifact.
2. `KoogChatRunner.appendHistoryMessage`'s `else -> Unit` branch (unknown role dropped) is untested; `DialogHistory` only ever emits `user`/`assistant`, so this is defensive code with no reachable production path.
3. The tool-call test asserts the echo and final answer but not URL/model/schema of its own first request; the plain-reply test covers those assertions for the same code path.
4. The tests create an `HttpClient` without closing it; MockEngine holds no OS resources — the common, harmless Ktor test pattern.
5. AC wording "the outgoing request asserts the model id and the tool schema" is satisfied in the plain-reply test only; if the intent was per-request assertions, one extra `assertOutgoingTurn` call in the tool-call test would close it cheaply.

## Verdict

`T13-test-engineer: PASS | verified: ok`
