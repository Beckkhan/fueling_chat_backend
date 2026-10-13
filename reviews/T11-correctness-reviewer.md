# T11 — Correctness Review (round 2, post style-gate handback)

**Verdict:** PASS
**Target:** commit `c5b691b85d92c5f20c4c08035e97ae13bcbb06ae` (T11: integrate Ktor Chat Route), diff vs parent `7b92eef`
**Reviewer:** correctness-reviewer (second gate)
**Timestamp:** 2026-10-13T23:01:16Z

Round-1 reviewed `0027644` (PASS). `git diff 0027644 c5b691b --stat` shows only `ChatRoutes.kt` (+99/-52 lines combined) and `ChatRoutesTest.kt` changed since; the working tree is clean for all source/build files, so this review is of the commit exactly.

## Handback re-run focus — decomposition preservation

- [x] **Masked bodies preserved** — every error body is a static masked constant or the seam-contracted `ChatFailure.maskedMessage`; raw request content and raw exception text are never echoed. `receiveValidRequest` maps parse failure → `MASKED_MALFORMED_JSON`, validation failure → the static DTO message (`validationError ?: MASKED_MALFORMED_JSON`, `ChatRoutes.kt:78`); `runTurn`'s generic branch ignores the exception object entirely (`ChatRoutes.kt:98-100`). Same message set as round 1; the catch bindings renamed to `_` carry no data into the response path.
- [x] **Exactly one response per request** — traced every path: `receiveValidRequest` either returns a value (no response) or responds 400 and returns null (`ChatRoutes.kt:74-79`); `runTurn` either returns a `ChatTurn` (no response) or responds the mapped failure and returns null (`ChatRoutes.kt:91-101`); `handleChat` responds 200 only when it holds a non-null turn (`ChatRoutes.kt:54-57`). The two-stage respond-and-return-null / return-value protocol is equivalent to round 1's `return@post` structure; no path can call `respond` twice.
- [x] **CancellationException rethrown** — `runTurn` catches `ChatFailure` first, then `cancellation: CancellationException` and rethrows the same instance, before the generic `catch (_: Exception)` (`ChatRoutes.kt:93-101`). `ChatFailure` is unrelated to cancellation, so the ordering intercepts nothing it should not; the parse path does not swallow cancellation either.
- [x] **elapsed_ms ≥ 0** — `entryMillis` captured at handler entry before parsing (`ChatRoutes.kt:51`, effectively route entry as in round 1); elapsed computed immediately before `respond` and `.coerceAtLeast(0L)` still applied (`ChatRoutes.kt:55`); `Long` → JSON integer.
- [x] **Route holds no business logic** — helpers only parse/validate (`receiveValidRequest`), delegate and map (`runTurn`, `respondMaskedFailure`), and respond (`respondMaskedError`). No Exposed/Koog/config/infra imports in `routes/` (grep clean); `chatRoutes` body is 15 lines.

## Contract checks (CONTRACT-API-001 / CONTRACT-API-002)

- [x] **Request shape** — `{prompt, fueling_id?, provider?}` field-for-field, `@SerialName("fueling_id")`, `application/json` via ContentNegotiation (`ChatRequest.kt:10-15`, `ChatRoutes.kt:35-42`, installed exactly once in `src/main`).
- [x] **Validation / D-10** — blank or whitespace-only prompt → 400; provider not in `{"local","remote"}` (including `""`) → 400; absent/null provider → `effectiveProvider()` = `"local"` (`ChatRequest.kt:28-38`). Malformed JSON → 400 masked (verified by passing `malformedJsonReturns400MaskedError` against Ktor 3.3.3's `BadRequestException` wrapping).
- [x] **200 body exactly `{summary, provider, elapsed_ms}`** — `ChatResponse` has exactly the three fields, no defaults; respond site passes `turn.summary` and `turn.provider` (the provider that served the turn) (`ChatResponse.kt:9-14`, `ChatRoutes.kt:56`).
- [x] **Failure mapping** — exhaustive `when` over the enum: `DATABASE → 500`, `PROVIDER → 502` (`respondMaskedFailure`, `ChatRoutes.kt:104-111`); unmapped exception (including the T12 placeholder `error(...)` via lazy DI) → generic masked 500 (`ChatRoutes.kt:98-100`); no code path emits a status outside 400/500/502.
- [x] **D-9 fueling_id** — transported unchanged to `ChatServiceRequest(request.prompt, request.fuelingId, request.effectiveProvider())` (`ChatRoutes.kt:90`); no defaulting, dropping, or route-side extraction; the explicit-vs-extracted branching itself is correctly left to T12.
- [x] **No streaming (D-7)** — `grep -rE "text/event-stream|chunked|webSocket|EventSource" src/main/kotlin/com/fuelingchat/routes` → no matches; single `post("/chat")` is the only route in `src/main`; one request → one response.
- [x] **No credentials in code/logs/responses** — no logging statements anywhere in `routes/` or the seam types (grep clean); no secrets in any added file; the only credential-adjacent strings are doc comments ("never SQL, credentials or raw provider errors").
- [x] **Read-only DB / empty-tool-array invariants** — N/A for this diff: no DB code and no LLM request construction in T11 (the route and seam open no connections; T12 assembles the Koog run). No DDL/DML added.
- [x] **Seam + wiring** — `ChatService` is a single `suspend fun run`; `ServiceModule.kt:16` keeps the `error("…")` placeholder with the intact `TODO(T12)` marker; `AppModule.kt:49` resolves the service lazily per request, so boot cannot fail on the placeholder and a placeholder hit is masked 500.

## Independent verification

- `./gradlew test --tests "com.fuelingchat.routes.ChatRoutesTest" --rerun-tasks` → BUILD SUCCESSFUL; XML report: **8 tests, 0 failures, 0 errors** (all eight named test methods present).
- `./gradlew test --rerun-tasks` (full suite) → BUILD SUCCESSFUL on three consecutive forced runs; **50 tests / 0 failures / 0 errors** (ChatRoutesTest 8, BootFailFastTest 4, plus DB/tool/config/logging suites).
- One earlier full-suite invocation failed spuriously with Gradle's `Could not write XML test results for …` for four classes (no test assertion failed). Root cause recovered from the daemon log: a concurrent Gradle build in the same project directory (parallel fan-out harness) deleted/recreated `build/test-results/` mid-write. Environmental, not a code or test failure; the three serialized reruns above are the control.
- Greps: streaming primitives (AC6 pattern) → none; `ContentNegotiation` installs in `src/main` → only `ChatRoutes.kt:35`; `StatusPages`/`exception<` in `src/main` → none (no path bypasses route masking); infra/DB/Koog imports in `routes/` → none.

## Findings

None.

## Notes (non-blocking, informational for the ledger)

1. The `catch (_: SerializationException)` clause remains likely unreachable under Ktor 3.3.3 (the converter wraps decode failures into `BadRequestException` before the route sees them). Harmless defense-in-depth; masking is unaffected.
2. `ChatRoutesTest.FakeChatService` still ignores its `request` argument, so no test pins prompt/`fueling_id`/effective-provider pass-through to the seam. Round-1 note carried forward; not required by T11's ACs, recommended when T12 lands.
3. Ktor's converter wraps any `Throwable` raised while deserializing a request body into `BadRequestException`; that affects only the inbound-body path and cannot swallow a service-side cancellation, which `runTurn` rethrows correctly.

## Verdict

`T11 done | verified: ok`
