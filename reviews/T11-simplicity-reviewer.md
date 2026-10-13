# T11 — Simplicity Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T16:36:32Z

## Scope

Commit `ac759f7` ("T11: Ktor Chat Route"), reviewed against `docs/tasks/T11.md` and CONTRACT-API-001 (spec §2). `git diff HEAD -- src build.gradle.kts detekt.yml` is empty — the reviewed tree is the commit. `metrics/cto-ledger.md` is out of scope. Build/test status is relied on from gates 1–2, which force-ran `./gradlew build ktlintCheck detekt` on this identical tree.

## Verification performed

- Read the full diff and the current content of all 12 touched files.
- Independently read the pinned Ktor 3.3.3 sources from the Gradle cache to settle the reachability question behind the exception-handler set:
  - `io.ktor.serialization.ContentConvertException : Exception` and `io.ktor.server.plugins.BadRequestException : Exception` are unrelated types; `ContentTransformationException` (abstract, `: IOException`) has subclasses `CannotTransformContentToTypeException` / `UnsupportedMediaTypeException` (ktor-serialization `ContentConvertException.kt:9`, ktor-server-core `plugins/Errors.kt:22,80,83,101`). The three handlers are therefore three distinct branches, not redundant registrations of one another.
  - `RequestConverter.convertBody` wraps every converter failure in `BadRequestException` (`ktor-server-content-negotiation .../RequestConverter.kt:66-70`), so malformed JSON never surfaces as `ContentConvertException`.
  - `ResponseConverter.convertResponseBody` returns `OutgoingContent` early and emits `NOT_ACCEPTABLE` content (not an exception) on Accept mismatch; response-serialization failures would be kotlinx `SerializationException`, not `ContentConvertException`.
- Grepped the project for other JSON configurations / ContentNegotiation installs / StatusPages installs: `jsonFormat` is the only configured `Json` instance, `configureChatStatusPages()` is the only StatusPages install, `chatRoutes` the only route group — no duplicated wiring.

## Findings (non-blocking)

1. `exception<ContentConvertException>` (`ChatRoutes.kt:62`) is unreachable in this application: request-side conversion failures are wrapped into `BadRequestException` before StatusPages sees them, wrong/missing `Content-Type` arrives as a `ContentTransformationException`, and nothing else in the app throws `ContentConvertException` (verified above). It is architect-pinned defence-in-depth — three lines, no branching, class-correct, and it does not mislead (no comment claims it is exercised, no test pretends to cover it). I judge it acceptable to keep, but recommend T12 either annotate it as deliberate defence-in-depth or drop it; a test pinning it would be impossible without deliberately throwing an unreachable type.
2. `Json { ignoreUnknownKeys = true }` (`ChatRoutes.kt:30`) is leniency the contract does not require: a misspelled `fueling_id` is silently dropped, which interacts with D-9 (a present-and-typed id must be injected). Contract-silent either way, one-flag fix, no structure or masking impact — non-blocking. Recommendation: either parse strictly (most literal reading of "field-for-field"), or keep the leniency and pin it with a test so the choice is deliberate and change-detected.
3. `detekt.yml` `ForbiddenComment.allowedPatterns: 'TODO:'` relaxes a lint rule project-wide to support the scaffold handoff. Not a source-simplicity issue (no YAGNI/dup/dead-code impact), noted for the code-style gate; the three scaffold TODOs (AppModule, ServiceModule, ChatService) should be consumed and the relaxation reverted or narrowed once the dual-coder pipeline no longer needs it.

## Judged non-issues (prior gates' candidates)

- **`respondMasked` passing `failure.status` through 1:1 with no allowlist** — correct as written. CONTRACT-API-001 freezes the statuses (400/500/502) and the AC says "maps to that HTTP status"; an allowlist would be an unrequested feature and a second source of truth for the error table. Leave it.
- **Route-scoped ContentNegotiation + application-level StatusPages split with the explicit `jsonFormat.encodeToString` in `respondMasked`** — justified, not incidental complexity. `TextContent` is `OutgoingContent`, so the masked body is emitted verbatim with `Content-Type: application/json` unconditionally. Switching to app-level ContentNegotiation plus `call.respond(status, ErrorResponse(...))` would shorten `respondMasked` by a few characters but would put error bodies back through Accept negotiation (406 risk reachable from a client's `Accept` header) and re-couple the frozen masked-body contract to converter scoping. The current split is the simpler guarantee.
- **Three 400 registrations repeating `respondMasked(400, INVALID_BODY_MESSAGE)`** — not a DRY violation: the three exception types are unrelated (verified above) and each registration is a distinct type mapping, like separate `catch` clauses. Not foldable.
- **`ChatFailure.databaseFailure` / `providerFailure` used only by tests today** — not dead: they pin the frozen 500/502 error table for T12 and give the route tests the only legitimate way to exercise the status mapping. `ChatFailure.cause` is a logs-only seam field, consistent with the masking pipeline.

## YAGNI / duplication / dead-code summary

- YAGNI: nothing beyond the ACs. Request/response DTOs are field-exact; no streaming, no history, no service logic; the placeholder binding is explicitly mandated (T12 replaces it); the `() -> ChatService` provider is the minimal way to keep the placeholder out of the boot path.
- Duplication: none found. `jsonFormat` and the two message constants are shared, not copied; the test file already factors `chatApp` / `postChat` / `maskedErrorOf` / `FakeChatService`.
- Over-abstraction: none. `ChatService` / `ChatTurn` / `ChatFailure` are the pinned seam; `validatePrompt` / `validateProvider` are named-intent micro-functions feeding one masked-error path (route 400s and service failures both flow through the single `ChatFailure` handler — no second error mechanism).
- Dead code: only the unreachable `ContentConvertException` handler (finding 1); all imports used, no unused helpers, no orphaned DTOs.

## Verdict

`T11-simplicity-reviewer: PASS | verified: ok` — no blocking findings; three non-blocking observations, with recommendation 1 (defence-in-depth annotation or removal) and 2 (strict parsing or pinned leniency) best folded into T12.
