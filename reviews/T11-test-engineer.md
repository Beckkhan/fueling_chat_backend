# T11 — Test Engineer Review (round 2)

**Verdict:** PASS
**Timestamp:** 2026-10-13T22:58:48Z
**Target:** commit `c5b691b85d92c5f20c4c08035e97ae13bcbb06ae` (T11: integrate Ktor Chat Route), diff vs parent `7b92eef` (11 files, +639/-1)
**Round-2 delta:** verified `git diff 0027644 c5b691b` touches exactly two files — `ChatRoutes.kt` (decomposed into private helpers, `@Suppress` removed) and `ChatRoutesTest.kt` (two over-long tests extracted to `assertFrozenSuccessShape` / `assertNoInternalDetailsLeak`). Assertion-by-assertion diff confirmed: assertions and test names unchanged; the extracted helpers are byte-for-byte the same checks with identical failure messages. Working tree matches the reviewed commit for `src/` and `build.gradle.kts`.

## Gate checks

### Tests run
- `./gradlew test --rerun-tasks` (cache bypassed, forced execution): BUILD SUCCESSFUL, exit 0 — **50 tests / 0 failures / 0 errors / 0 skipped**; `ChatRoutesTest` **8/8** (fresh XML, timestamp `2026-10-13T22:57:32Z`).
- Repeat forced run of `ChatRoutesTest` alone (`--tests com.fuelingchat.routes.ChatRoutesTest --rerun-tasks`): 8/8 pass again — no flakiness observed.
- `./gradlew ktlintCheck detekt --rerun-tasks`: BUILD SUCCESSFUL; detekt report 0 findings (AC8 re-verified on the amended files).

### Route tests use testApplication + fake ChatService seam
- All 8 tests call `testApplication { application { chatRoutes { fake } } }` with the private `FakeChatService` (`src/test/kotlin/com/fuelingchat/routes/ChatRoutesTest.kt:28-38`), a hand-written implementation of the `ChatService` seam returning a canned `ChatTurn` or throwing a canned `ChatFailure` / `RuntimeException`.
- No real network/DB/LLM on the route path: grep for `DbPools`, `LlmClient`, `postgres`, `ollama`, `deepseek`, `localhost` in `src/test/kotlin/com/fuelingchat/routes/` → no matches; requests are served in-process by the Ktor test engine. `ChatRoutesTest.kt` is the only test file touching the seam.
- The hand-written fake (rather than MockK) is the scaffold-pinned Block 4 design; the seam is a plain interface with no LLM/DB client on this path, which satisfies the mocking rule at this stage. T12 (real service) will need the `LlmClient` seam mocked — out of scope here.

### Isolation and determinism
- Each test creates its own `testApplication` and its own `FakeChatService` instance; no companion object, no static init, no `@BeforeAll`, no shared mutable state, no order dependence, no cross-test pollution.
- No `sleep`/`delay`/`Instant.now`/`System.currentTimeMillis` in `ChatRoutesTest.kt` (grep clean). `elapsed_ms` is asserted only as a non-string JSON number `>= 0`; production coerces with `coerceAtLeast(0L)` — deterministic, cannot flake.
- No `junit-platform.properties` and no parallel-execution config in `build.gradle.kts` (`tasks.test { useJUnitPlatform() }`) — suite runs sequentially; two consecutive forced runs produced identical results.
- DB-backed tests elsewhere in the suite (pre-existing, not in this diff) use the local Docker `postgres:15-alpine` on 25432 (observed `Up (healthy)`), never a production DB.

### Acceptance criteria → test mapping
- **AC1** (JSON contract + malformed JSON → 400 masked): `ok200ReturnsExactlyThreeFieldsAndNonNegativeElapsed` posts `{prompt, fueling_id, provider}` as `application/json` and asserts a JSON content type on the 200; `malformedJsonReturns400MaskedError` asserts 400, masked body and that the raw request body is not echoed. ContentNegotiation (kotlinx-json) installed once in `ChatRoutes.kt:34-43`.
- **AC2** (validation): `blankPromptReturns400MaskedError`, `whitespaceOnlyPromptReturns400MaskedError`, `invalidProviderReturns400MaskedError` (which also asserts absent provider → 200, D-10); all 400 bodies exactly `{"error": ...}` masked.
- **AC3** (200 body exactly `{summary, provider, elapsed_ms}`, integer ≥ 0): `assertFrozenSuccessShape` (`ChatRoutesTest.kt:253`) asserts the exact key set, traces `summary`/`provider` to the fake's `ChatTurn`, and asserts `elapsed_ms` is a non-string JSON number `>= 0`.
- **AC4** (failure mapping): `databaseFailureClassReturnsMasked500`, `providerFailureClassReturnsMasked502` (both assert status, exact masked message, and no class/exception/stack-frame leakage), `unmappedExceptionReturnsGenericMasked500` (asserts raw SQLSTATE text, credential and stack trace never leak via `assertNoInternalDetailsLeak`, `ChatRoutesTest.kt:267`).
- **AC5** (seam + placeholder DI): `ChatService` is one `suspend fun run` returning `ChatTurn`; `ServiceModule.kt:16` retains the `error("ChatService implementation lands in T12")` placeholder; lazy resolution (`AppModule.kt:49`) proven non-eager by the passing `BootFailFastTest.serverStartsAndAnswersHttp` (real boot with `chatRoutes` installed, HTTP answer asserted).
- **AC6** (no streaming): `grep -rE "text/event-stream|chunked|webSocket|EventSource" src/main/kotlin/com/fuelingchat/routes` → no matches; success-path tests additionally assert a single JSON response body.
- **AC7** (test list): all enumerated assertions present — 200 shape, the 400 cases (tests cover four: blank, whitespace-only, invalid provider, malformed JSON), masked 500, masked 502, `elapsed_ms ≥ 0`. `AppModule.boot()` installs the route (`AppModule.kt:49`).
- **AC8** (`./gradlew build ktlintCheck detekt`, 0 errors): forced `ktlintCheck detekt --rerun-tasks` → clean, detekt 0 findings; forced `test --rerun-tasks` → green (the `build` portion).

### Dependency scope
- `io.ktor:ktor-server-test-host-jvm:3.3.3` declared `testImplementation` only, aligned with the project's 3.3.3 Ktor stack.

## Findings

None — no blocking findings. Verdict is PASS.

## Non-blocking notes (carried from round 1; not grounds for failure)

1. `ChatRoutesTest.kt:28-38` — the fake ignores its `request` argument, so no test pins request pass-through to the seam (`prompt`, `fueling_id`, effective provider), nor that the 200 `provider` comes from the service's `ChatTurn` rather than the request (the 200 test uses `local` on both sides). A regression dropping `fuelingId` or the `local` default would not be caught at route level. AC7's enumerated assertions do not require this; recommend a recording-fake assertion when T12 lands the real service.
2. `build.gradle.kts:42` — MockK is declared but unused repo-wide (pre-existing, outside this diff); no action for T11.
