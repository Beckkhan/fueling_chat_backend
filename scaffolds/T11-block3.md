# T11-block3

## Goal
Route tests for `POST /chat` using `testApplication` with a fake `ChatService` seam: 200 shape, the three 400 cases, masked 500/502, unmapped-exception masking, and `elapsed_ms ≥ 0`.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- Tests construct their own application module (install ContentNegotiation json + routing with the fake) — never touch AppModule.kt or production DI.
- No network, no DB, no Koog — the private fake seam returns canned `ChatTurn`s / throws `ChatFailure`.
- Runs after T11-block1 and T11-block2. Requires the task integrator's build.gradle.kts edit (`io.ktor:ktor-server-test-host-jvm`) before `./gradlew test` can run.
- No new dependencies without explicit permission.

## Files to create/modify
- `src/test/kotlin/com/fuelingchat/routes/ChatRoutesTest.kt` — create: tests per the case list below, with a private fake `ChatService` (recording the seam arguments) in the test file.

Cases:
1. 200: body is exactly `{summary, provider, elapsed_ms}` (no extra keys); values echo the fake's `ChatTurn`; `elapsed_ms` is an integer ≥ 0.
2. 400 blank prompt (empty and whitespace-only) — body `{"error": "..."}` masked.
3. 400 invalid provider (e.g. `"cloud"`) — masked body.
4. 400 malformed JSON (broken body / wrong content type) — masked body, no exception internals.
5. `ChatFailure(500, "…")` from the fake → HTTP 500 with `{"error": "<maskedMessage>"}`.
6. `ChatFailure(502, "…")` from the fake → HTTP 502 with `{"error": "<maskedMessage>"}`.
7. Unmapped `RuntimeException` from the fake → 500 with generic masked body; assert the raw exception message does not appear in the body.
8. A valid `fueling_id` passes through to the fake's `runTurn` unchanged (seam wiring); absent `provider` arrives as `null`.

## Public API
None — test-only block. Consumes T11-block1's seam (`ChatService`/`ChatTurn`/`ChatFailure`) and T11-block2's `chatRoutes`.

## Invariants
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).
