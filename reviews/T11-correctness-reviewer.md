# T11 — Correctness Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T16:33:41Z

## Scope

Commit `ac759f7` ("T11: Ktor Chat Route") reviewed against `docs/tasks/T11.md` and CONTRACT-API-001/002 (spec §2; per `docs/tasks/INDEX.md`, CONTRACT-API-001 covers request **and** response for `first`-branch tasks). Working tree clean for all reviewed paths — `git diff HEAD -- src build.gradle.kts detekt.yml` is empty; only out-of-scope `metrics/cto-ledger.md` is modified.

## Verification performed

- Read the full diff of `ac759f7` (12 files) and the current content of `AppModule.kt`, `ChatRoutes.kt`, DTOs, seam types, `ServiceModule.kt`, test file.
- `./gradlew build ktlintCheck detekt --rerun-tasks` — BUILD SUCCESSFUL, 0 errors (the first, cached run was inconclusive, so the gate result rests on the forced re-run). JUnit XML: 50 tests / 0 failures / 0 errors; `ChatRoutesTest` 8/8.
- `dependencyInsight`: `ktor-server-status-pages-jvm` and `ktor-server-test-host-jvm` resolve to 3.3.3 (the stale 3.2.3 jar in the cache is unused).
- Read the Ktor 3.3.3 sources from the Gradle-cache source jars (the exact resolved version) for the load-bearing plugin behavior — findings cited below by file/line.
- `grep -rE "text/event-stream|chunked|webSocket|EventSource|streamResponse|…" src/main/kotlin/com/fuelingchat/routes` — no matches (D-7).

## Acceptance criteria check

- [x] **Request contract.** `ChatRequest(prompt, fuelingId@SerialName("fueling_id"), provider)` matches `{prompt, fueling_id?, provider?}` field-for-field; `prompt` required, the other two optional with `null` defaults. `ContentNegotiation(kotlinx-json)` is installed in `chatRoutes` and malformed JSON yields `400 {"error":"Invalid request body"}` — verified by test and by source: ContentNegotiation's `convertBody` wraps every deserialization throwable in `BadRequestException` (`RequestConverter.kt:66-70`), and StatusPages' nearest-class selection (see below) routes it to the `BadRequestException` handler.
- [x] **Validation.** `validatePrompt` uses `isBlank()` (blank/whitespace-only → `ChatFailure(400, "Prompt must not be blank")`). `validateProvider` rejects any non-null value other than `"local"`/`"remote"` (exact, case-sensitive) with `ChatFailure(400, "Invalid provider")`; `null` is returned and forwarded untouched — correct per the task's seam note (D-10 default belongs to T12, whose interface signature accepts `String?`). All bodies masked; no exception message or cause is ever placed in a body.
- [x] **200 contract.** `ChatResponse` is exactly `{summary, provider, elapsed_ms}` (`@SerialName("elapsed_ms")`, `Long` → JSON integer); `provider` comes from `turn.provider` (not the echoed request — pinned by the test, which sends `"remote"` and expects the fake's `"local"`). `elapsed_ms` is `TimeSource.Monotonic.markNow()` taken at route-handler entry, `elapsedNow().inWholeMilliseconds ≥ 0`; monotonic, so it cannot flake. The test asserts the exact key set, integer-ness and ≥ 0.
- [x] **ChatFailure mapping.** `ChatFailure(status, maskedMessage)` → `respondMasked(status, maskedMessage)`; companion factories pin the two classes: `databaseFailure` = 500, `providerFailure` = 502 (matches the spec error table). Unmapped `Throwable` → generic `500 {"error":"Internal server error"}`, with the handler discarding the exception entirely — tests feed a fake SQL/credential string and assert it is absent.
- [x] **Seam.** `ChatService` has one `suspend fun chat(prompt, fuelingId, provider): ChatTurn` and no logic (comment only); `ChatTurn(summary, provider)`; `ChatFailure` carries status + masked message (cause kept for logs only). `ServiceModule` binds the placeholder `error("ChatService lands with T12")`; `boot()` resolves it lazily per request, so boot never touches it.
- [x] **No streaming (D-7).** Grep empty; single request → single response.
- [x] **Wiring and tests.** `AppModule.boot()` installs `configureChatStatusPages()` then `routing { chatRoutes { di.direct.instance<ChatService>() } }` — same order as the tests' `application { … }`. `ChatRoutesTest` uses `testApplication` with a fake seam and asserts 200 shape, all three 400 cases with exact masked bodies (plus a helper pinning the error body to key set `{error}`), masked 500, masked 502, leak assertions on the unmapped path, and `elapsed_ms ≥ 0`.
- [x] **Build gate.** `./gradlew build ktlintCheck detekt` — 0 errors (forced re-run; all three linters/checks executed).

## Load-bearing decision under scrutiny: route-scoped ContentNegotiation + application-level StatusPages

The implementer serialized masked error bodies explicitly (`respondMasked` → `TextContent(..., ContentType.Application.Json)`) instead of `call.respond(ErrorResponse(...))`, on the rationale that the route-scoped converter may not be in scope for the app-level StatusPages handler. Verified against the pinned 3.3.3 sources:

- `ContentNegotiation` is a **route-scoped** plugin (`ContentNegotiation.kt:58`); `StatusPages` is an application plugin. The rationale is sound in principle and, more importantly, **irrelevant to the outcome**: `convertResponseBody` returns immediately when the body is an `OutgoingContent` (`ResponseConverter.kt:19-22`), and `TextContent` is one. The masked bodies therefore go out exactly as written — `{"error":"<masked message>"}` with `Content-Type: application/json` — regardless of plugin scoping.
- **406 is not reachable on any contract error path.** The 406 branch (`ResponseConverter.kt:84-90`) is only reached for non-`OutgoingContent` bodies, i.e. never for `respondMasked`. It remains reachable in Ktor's normal way only on the *success* path if a client sends an `Accept` header that cannot match `application/json` — outside the frozen error table and not exercised by the contract's frontend (which sends JSON), noted below.
- **Handler selection is correct for every reachable exception.** `findHandlerByValue` filters registered keys by `instanceOf` and picks the nearest superclass on the JVM (`StatusPages.kt:44-54`, `StatusPagesUtilsJvm.kt:9-21`). Since `Throwable` is registered, no exception can fall through to Ktor's default (which would produce an unmasked engine error); since all registered keys are classes on the actual superclass chain of their matches, `distance()` cannot hit its null-superclass edge. `ChatFailure` → its own handler (distance 0). `JsonConvertException` is wrapped into `BadRequestException` before it can surface (`RequestConverter.kt:66-70`), so malformed JSON deterministically hits the 400 handler. `UnsupportedMediaTypeException`/`CannotTransformContentToTypeException` (wrong body content type) are `ContentTransformationException` subclasses → masked 400, not a 500 or an engine default.
- **No internals leak.** Every handler body is a constant string; the caught exception is bound to `_`. `ChatFailure`'s `cause` never reaches a response. No credentials or connection strings exist in the reviewed sources (the test's `supersecret` string is a fixture used to prove non-leakage).

Conclusion: the pin satisfies CONTRACT-API-001 exactly, and the `respondMasked` construction is the safe choice either way.

## Observations (non-blocking)

1. `Json { ignoreUnknownKeys = true }` makes request parsing lenient about unknown fields; the contract is silent on this, but a typo'd `fueling_id` would be silently ignored rather than 400. Also used for encoding, harmless there.
2. The `exception<ContentConvertException>` handler is effectively dead in this path (Ktor wraps conversion failures into `BadRequestException` before StatusPages sees them). Harmless defence-in-depth; flagging for the simplicity gate.
3. `respondMasked` passes `failure.status` through 1:1 with no allowlist — fine for the frozen 400/500/502 table; a future `ChatFailure` with an off-table status would surface verbatim (T12 owns status selection).
4. `elapsed_ms` is captured at handler entry and read just before `call.respond`, so it excludes serialization and the network write. Consistent with "route entry → response ready"; the AC's verifiable requirement (integer ≥ 0) is met.
5. Test-side (already noted by the test engineer, repeated here for the trail): the fake seam ignores its arguments, so the route's argument pass-through (`prompt`, `fueling_id` mapping, `provider`) is not pinned by a test; `boot()` wiring is verified by inspection only (tests build their own application with mirrored ordering).
6. `detekt.yml` now project-allows `TODO:` comments to support the dual-coder scaffold handoff (FIXME/STOPSHIP still forbidden) — pipeline/style concern, for the code-style gate.
7. D-9 check: the route carries no `fueling_id` branching — the field is forwarded untouched, which is exactly this task's boundary; the branching is T12's. Read-only DB: no DB code introduced by T11. Tool set: T11 builds no LLM requests. All clean.

## Verdict

`T11 done | verified: ok`
