# T11 — Correctness Reviewer

**Verdict:** PASS
**Timestamp:** 2026-10-13T12:41:29Z

Review target: HEAD `1331d9a` on branch `first` — full task diff `d73bf57` (T11: Ktor Chat Route) + `1a3d476` (gate-3 handback fix: dead `import com.fuelingchat.service.ChatTurn` removed from `ChatRoutes.kt`) + `1331d9a` (task-file marker). Round-2 re-verification after the simplicity handback (task file Handback 1); round-1 verdict for this gate was PASS and is superseded by this run. Working tree is identical to HEAD for `src/` and `build.gradle.kts` (`git diff HEAD --quiet -- src build.gradle.kts` clean), so the reviewed code equals the committed code.

Gate evidence: `reviews/T11-test-engineer.md` round-2 PASS. Independent re-runs this round: `./gradlew test --tests "com.fuelingchat.routes.ChatRoutesTest" --rerun-tasks --no-build-cache` → BUILD SUCCESSFUL, XML report `tests="10" skipped="0" failures="0" errors="0"`; `./gradlew ktlintCheck detekt --rerun-tasks --no-build-cache` → BUILD SUCCESSFUL (8 tasks executed); `./gradlew build ktlintCheck detekt` → BUILD SUCCESSFUL (AC8).

## Delta since round 1

`git diff d73bf57..HEAD -- src build.gradle.kts` touches only `ChatRoutes.kt`: one deleted import line. The removed import was genuinely unreferenced (no other use of `ChatTurn` in the file); import removal cannot alter behavior. Test sources are byte-identical to round 1.

## Contract conformance (CONTRACT-API-001 / project-specification §2)

- Request: `ChatRequest(prompt: String, fueling_id?, provider?)` — field-for-field per contract, `@SerialName("fueling_id")` on the wire name; missing `prompt` fails deserialization → masked 400.
- Response: `ChatResponse` is exactly `{summary, provider, elapsed_ms}`, no defaulted properties, so no extra keys can appear. `provider` is `turn.provider` — the provider that served the turn, not the request echo. `elapsed_ms: Long` serializes as a JSON integer; measured `TimeSource.Monotonic` from the first statement of the handler (route entry) to just before `respond`; monotonic + `inWholeMilliseconds` cannot be negative.
- Validation: blank/whitespace-only prompt → 400 (`isBlank()` ≡ "empty after trim"); `provider == null` → `"local"` (D-10); `"local"`/`"remote"` pass; anything else (including `""`) → 400. Validation runs before the service call, so no side effects on invalid input.
- Errors: `ChatFailure(status, maskedMessage)` → that HTTP status with `{"error": "<masked message>"}`; constants `DB_FAILURE = 500` / `PROVIDER_FAILURE = 502` match the frozen table. `HttpStatusCode.fromValue` is non-throwing, so a malformed status cannot escape the catch. Malformed JSON / missing field / wrong content type → masked 400 (`BadRequestException`, `SerializationException`, `ContentTransformationException` catch chain; hierarchies as verified in round 1). Unmapped service exceptions → generic `500 {"error": "internal server error"}`; `CancellationException` is re-thrown, so cancellation propagates.

## Logic / edge cases

- D-7 (no streaming): `grep -rEn "text/event-stream|chunked|webSocket|EventSource" src/main/kotlin/com/fuelingchat/routes` → no matches (exit 1), re-verified this round. One request → one `respond` on every path.
- D-9: `fueling_id` is passed verbatim to the seam (`chatService.chat(request.prompt, request.fuelingId, provider)`) with no route-side branching or extraction — the branching belongs to `ChatService` (design §3 step 6, T12). Correct for this task: the route holds parse → validate → delegate → respond only, no LLM/SQL/prompt/history logic.
- Seam: `ChatService` has the single suspend entry point returning `ChatTurn(summary, provider)` and throwing `ChatFailure`; DI binds a lazy placeholder `error("ChatService lands with T12")`, resolved only on first request via `by di.instance()` in `AppModule.deferredChatService()` — boot fail-fast is unaffected, and until T12 the path yields the masked generic 500, never internals.
- Wiring: `AppModule.boot()` installs the route (`configureChat(deferredChatService())`); single `install(ContentNegotiation)` and single `routing { route("/chat") { post { … } } }` across `src/main` — no duplicate-plugin or route conflict.

## Masking / secrets

Error bodies contain only route constants or the seam's `maskedMessage`; the exception message is never echoed (generic constant for the catch-all). No credentials, SQL, or provider internals in code, logs, or responses: the T11 sources contain no DB access, no logging of exception details, and a credential-pattern grep (`password|secret|token|api_key|jdbc|postgres`) over the new route/service files is clean.

## Cross-cutting invariants

- Read-only DB: untouched by this diff — `DbPools.kt:38-39` still has `isReadOnly = true` + `connectionInitSql = "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY"` on all four pools; the route performs no DB access.
- Tool array never empty: T11 builds no LLM request; the placeholder fails before any client invocation, so the invariant cannot be violated on this path.

## Findings

None blocking. No new defects introduced by the handback fix; the round-1 non-blocking notes (provider-provenance discrimination in the fake, `ChatFailure` accepting arbitrary status ints for T12, no server-side logging of swallowed exceptions) remain valid for T12+ and are unchanged by this delta.

## Verdict

`T11 done | verified: ok`
