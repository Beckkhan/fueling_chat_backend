# T11 — Test Engineer Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T16:29:34Z

## Verification performed

- `./gradlew test --rerun` — full suite 50 tests, 0 failures, 0 errors (9 classes), including `ChatRoutesTest` 8/8.
- `ChatRoutesTest` force-rerun 3 consecutive times — green each run (flakiness check).
- `./gradlew build ktlintCheck detekt` — BUILD SUCCESSFUL; ktlint (all source-set tasks) and detekt re-executed with forced flags — 0 errors.
- `grep -rE "text/event-stream|chunked|webSocket|EventSource" src/main/kotlin/com/fuelingchat/routes` — no matches (D-7).
- Reviewed the full diff of ac759f7 (main sources, tests, build.gradle.kts, detekt.yml); working tree clean apart from out-of-scope `metrics/cto-ledger.md`.

## Acceptance criteria check (test view)

- [x] Route tests use `testApplication` with a fake `ChatService` seam; every test builds a fresh application and fresh fake — no shared mutable state, no order dependence, no cross-test pollution.
- [x] 200 shape: exact response key set `{summary, provider, elapsed_ms}` asserted (`assertEquals(setOf(...), body.keys)`); `provider` asserted from the service's `ChatTurn` (request says "remote", fake returns "local", response is "local") — pins "provider that served the turn", not an echo.
- [x] Three 400 cases covered: blank/whitespace prompt, invalid provider, malformed JSON — each asserts status 400 and an exact masked body; the `maskedErrorOf` helper also pins the body key set to `{error}` alone.
- [x] Masked 500 (`ChatFailure.databaseFailure` → "Databases unavailable") and masked 502 (`ChatFailure.providerFailure` → "Provider unreachable") asserted; unmapped `RuntimeException` carrying fake SQL/credentials → generic 500 "Internal server error" with explicit leak assertions (`assertFalse` on "supersecret" and "SQL").
- [x] `elapsed_ms ≥ 0` asserted, plus JSON-integer check (`isString` false). Timing uses `TimeSource.Monotonic`, so the assertion cannot flake; no sleeps, delays or wall-clock expectations anywhere in the test file.
- [x] No real network/DB/LLM: in-memory `testApplication` engine, hand-written fake seam; the T11 route path touches no `LlmClient`, no DB, no sockets.
- [x] `./gradlew test` passes; `./gradlew build ktlintCheck detekt` completes with 0 errors.
- [x] D-7: no streaming primitives in `routes/` (grep empty); one request → one response.

## Observations (non-blocking)

1. `FakeChatService` ignores its parameters and no test request includes `fueling_id`: argument pass-through (prompt / fueling_id / provider) and the request-side `@SerialName("fueling_id")` mapping are not asserted, so a miswired argument would not fail these tests. Not required by the T11 test AC; the natural home is T12's service tests (one extra case asserting captured args would close it).
2. `AppModule.boot()` wiring (`configureChatStatusPages()` + `routing { chatRoutes(...) }`) is verified by inspection only: `boot()` blocks on `embeddedServer(..., wait = true)`, and the route tests build their own application — a boot-wiring regression would not be red in CI. Consistent with the project's existing setup; the tests do mirror the same wiring (StatusPages application-level, ContentNegotiation route-scoped).
3. Error response `Content-Type` header is not asserted (bodies are parsed as JSON, which pins it in practice). The specific masked message strings are implementation-chosen and non-leaky, which the contract allows.
4. `detekt.yml` now allows `TODO:` comments to support the dual-coder scaffold handoff — pipeline/style concern, noted for the code-style gate, not a test issue.

## Verdict

`T11-test-engineer: PASS | verified: ok`
