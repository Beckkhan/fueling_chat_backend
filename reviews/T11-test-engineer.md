# T11 — Test Engineer Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T12:39:30Z

Review target: HEAD `1331d9a` on branch `first` — full task diff `d73bf57` (Ktor Chat Route) + `1a3d476` (simplicity handback fix: dead `import com.fuelingchat.service.ChatTurn` removed from `ChatRoutes.kt`) + `1331d9a` (task-file marker). Re-verification round after the gate-3 handback; round-1 verdict for this gate was PASS and is superseded by this run.

Scope of the re-review: the only source change since round 1 is the one-line import removal — `git diff d73bf57..HEAD` touches `src/main/kotlin/com/fuelingchat/routes/ChatRoutes.kt` (1 deletion) and `docs/tasks/T11.md` (8 lines). Test sources are unchanged (`ChatRoutesTest.kt` identical between `d73bf57` and HEAD). Working tree has no uncommitted `src/` or `build.gradle.kts` changes, so the reviewed code equals the committed code.

## Test evidence (live, this round)

- `./gradlew test --rerun-tasks --no-build-cache` — **BUILD SUCCESSFUL** (37s, `:test` executed, not cached). JUnit XML report (`build/test-results/test/*.xml`, 9 suites): **52 tests, 0 failures, 0 errors, 0 skipped**. `ChatRoutesTest` 10/10 pass.
- `./gradlew build ktlintCheck detekt` — **BUILD SUCCESSFUL**; then `./gradlew ktlintCheck detekt --rerun-tasks --no-build-cache` (8 tasks executed) — **BUILD SUCCESSFUL** (AC8; the removal of the unused import is confirmed clean — no ktlint/detekt findings).
- Docker Postgres container `fueling_chat_backend-postgres-1` up/healthy on `localhost:25432`; the DB-backed suites (`DbInfrastructureTest`, `GetFuelingInfoToolIntegrationTest`, `BootFailFastTest`) ran green against it. No test points at any non-local DB (`grep` confirms `localhost:25432`; the unreachable-DB cases use `localhost:1` intentionally).
- `grep -rE "text/event-stream|chunked|webSocket|EventSource" src/main/kotlin/com/fuelingchat/routes` — no matches (exit 1), so AC6 holds.

## Acceptance criteria — test coverage (AC7)

All required route tests exist in `src/test/kotlin/com/fuelingchat/routes/ChatRoutesTest.kt`, use `testApplication` with a fake `ChatService` seam:

- 200 shape → `returns200WithExactContractShape` asserts the exact key set `{summary, provider, elapsed_ms}`, values, and that `prompt` / `fueling_id` / `provider` reach the service; `defaultsProviderToLocalWhenAbsent` covers absent provider → `local` (D-10).
- Three 400 cases → `blankPromptReturns400` (whitespace-only), `invalidProviderReturns400` (`"cloud"`), `malformedJsonReturns400Masked` (`"{oops"`); plus `missingPromptFieldReturns400`.
- Masked 500 → `dbFailureMapsTo500WithMaskedMessage`; plus `unexpectedServiceExceptionReturnsGenericMasked500` asserting the generic body and no leak of `jdbc` / secret text.
- Masked 502 → `providerFailureMapsTo502WithMaskedMessage`.
- `elapsed_ms ≥ 0` → `elapsedMsIsNonNegative` and the 200-shape test.
- `AppModule.boot()` route installation → exercised by the pre-existing `BootFailFastTest.serverStartsAndAnswersHttp` (real Netty boot on `:8080`, GET `/` → 404), green with the new `configureChat(deferredChatService())` wiring.

Every new/changed main-source function is exercised through the route tests (`configureChat`, `handleChat`, `requirePromptNotBlank`, `validatedProvider`, the DTOs, `ChatFailure`); no new route branch is left untested.

## Isolation, determinism, mocking

- Each test constructs its own `testApplication` and its own private `FakeChatService` instance: no shared mutable state, no statics, no order dependence, no `@Disabled`/`@Ignore`. Gradle test executor is sequential (no parallel forks configured).
- No sleeps, randomness, or wall-clock assertions in the new tests (`grep` for `Thread.sleep|Random|Instant.now|LocalDate.now|currentTimeMillis` in `routes/` is empty). The only delay is the fake's `delay(50)` coroutine suspension, which cannot affect the asserted `elapsed_ms ≥ 0` bound.
- Route tests run on Ktor's in-process test engine (`client.post("/chat")` relative path) — no port binding, no real network. The `ChatService` seam is faked, so no LLM call is reachable on this path; no test reaches a real provider or a production DB.

## Findings

None blocking.

## Notes (non-blocking, carried from round 1, for T12+)

1. Provider provenance is still not discriminated: the 200-path fake returns `ChatTurn("answer", "local")` for requests that are also `"local"`, so a regression echoing the request's provider instead of `turn.provider` would pass. A discriminating fake (request `local`, turn `remote`, expect `remote`) would pin the contract's "provider that served the turn".
2. `elapsedMsIsNonNegative` checks only the contract bound (`≥ 0`); asserting the 50 ms fake delay shows up in `elapsed_ms` would couple the test to scheduler timing, so the current choice is the non-flaky one.
3. `AppModule.deferredChatService()` has no direct test; its construction is exercised by the boot smoke test, and its DI resolution intentionally throws until T12 replaces the `bind<ChatService>() { error("ChatService lands with T12") }` placeholder.
4. Error bodies are parsed for the `error` key but the tests do not assert the error body's exact key set or the `Content-Type: application/json` header; `"remote"` on the 200 path is not exercised (only the reject path). Not required by AC7.
5. Pre-existing, outside this diff: `BootFailFastTest.serverStartsAndAnswersHttp` binds real port 8080 with a 40 s poll loop; it passed here but can conflict if the port is occupied.

## Verdict

`T11 done | verified: ok`
