# T11 — Simplicity Review (round 2)

**Verdict:** PASS
**Timestamp:** 2026-10-13T23:00:14Z
**Target:** amended integrated commit `c5b691b85d92c5f20c4c08035e97ae13bcbb06ae` (T11: integrate Ktor Chat Route), diff vs parent `7b92eef` (11 files, +639/-1). Rework delta vs round-1 commit `00276443` is confined to 2 files (+96/-52): `ChatRoutes.kt` decomposition and `ChatRoutesTest.kt` method extractions, exactly as the style-gate handback (docs/tasks/T11.md Handback 1) mandated.
**Reviewer:** simplicity-reviewer (third gate)

## Findings

All non-blocking; none block the gate.

1. `src/main/kotlin/com/fuelingchat/routes/ChatRoutes.kt:67-68` (plus `import kotlinx.serialization.SerializationException` at :20) — the `catch (_: SerializationException)` arm is unreachable in the pinned Ktor 3.3.3, i.e. 2 lines of dead defensive code. Carried from round 1; the decomposition only moved its lines. Re-verified this round against the cached jars: `io.ktor.server.plugins.contentnegotiation.RequestConverterKt.convertBody` bytecode wraps all converter failures into `io.ktor.server.plugins.BadRequestException`; `BadRequestException extends java.lang.Exception` while `CannotTransformContentToTypeException extends ContentTransformationException extends java.io.IOException` — so the :69-70 and :71-72 arms are each independently reachable, and only the SerializationException arm can never fire. Kept as cheap insurance against a future Ktor change; deleting the 2-line catch + that import is the entire fix if minimal surface is wanted.
2. `src/main/kotlin/com/fuelingchat/routes/ChatRoutes.kt:39` — `encodeDefaults = true` remains a no-op: no type this `Json` instance serializes (`ChatResponse`, `ChatErrorResponse`) has a property with a default value, and `ChatRequest` is receive-only. Carried from round 1; harmless.
3. `src/test/kotlin/com/fuelingchat/routes/ChatRoutesTest.kt:191-193` and `:219-221` — the two failure-class tests keep near-identical 3-line inline leak-check blocks; the new `assertNoInternalDetailsLeak` (:267-275) covers a related but different assertion set (raw detail / credentials / stack trace vs class name / package), so extracting these would not be clean reuse. Both methods are ≤ 26 lines. Noted only, no change requested.

## Notes (re-verification of round-1 points on the amended diff)

- **Decomposition kept the code simple.** `chatRoutes` is now :34-48 (15 lines, was 47); helpers `handleChat` :50-58 (9), `receiveValidRequest` :63-80 (18), `runTurn` :86-102 (17), `respondMaskedFailure` :104-111 (8), `respondMaskedError` :117-122 (6) — all ≤ 30, all private, each single-purpose with a live call site. No new abstraction was introduced: no interfaces, wrappers, config objects, or DI indirection beyond the task-mandated seam. The only protocol the extraction added is the contained "respond + return null" signal, and each null return sits adjacent to the error it responded.
- **Single-response-per-request shape preserved:** parse/validation failure → masked 400 + null (runTurn skipped); service failure → mapped masked error + null (200 skipped); success → exactly one 200. No path can double-respond.
- **No duplication introduced:** every masked body still funnels through `respondMaskedError` (3 call sites); the enum→status map exists once (`respondMaskedFailure` :105-109); each `MASKED_*` constant has one use.
- **No dead code besides finding 1:** every new helper/constant has a live call site (`handleChat` :45; `receiveValidRequest` :52; `runTurn` :53; `respondMaskedFailure` :94; `respondMaskedError` :78, :99, :110; `MASKED_MALFORMED_JSON` :78; `MASKED_INTERNAL_ERROR` :99); every import in both files resolves to a use except the `SerializationException` import tied to finding 1; both test helpers are used (`assertFrozenSuccessShape` :65, `assertNoInternalDetailsLeak` :248) and the `JsonObject` import (:16) is used by the former's signature.
- **YAGNI:** nothing beyond T11's acceptance criteria; `ContentNegotiation` is installed exactly once repo-wide (grep: only `ChatRoutes.kt`), no streaming primitives (grep clean); the single added test dependency is the one the Artifact section lists. The test-side extractions are single-use private helpers, but they are the style-gate remedy mandated by Handback 1 — not speculative abstraction or YAGNI.
- **Test extractions match the handback:** `ok200ReturnsExactlyThreeFieldsAndNonNegativeElapsed` is now :42-67 (26 lines) and `unmappedExceptionReturnsGenericMasked500` :226-250 (25 lines); both exceeded the 30-line cap only together with the assertion blocks now extracted.

## Evidence (this round, live)

- `./gradlew test --rerun-tasks --console=plain` → BUILD SUCCESSFUL in 33s; fresh report (01:58-01:59 local): 50 tests / 0 failures / 0 ignored overall, `ChatRoutesTest` class page 8 / 0 / 0 (0.784s) — the decomposed route and the extracted test helpers all execute green.
- `./gradlew detekt --rerun-tasks` → BUILD SUCCESSFUL, `build/reports/detekt/detekt.txt` 0 bytes (0 findings, fresh 01:58:31), covering detekt's default unused-import / unused-private-member rules.
- `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL; working tree matches HEAD for all reviewed sources (only `docs/tasks/T11.md` differs from HEAD, carrying the handback text).
- Ktor hierarchy re-check: `javap` on `ktor-server-core-jvm-3.3.3.jar` for `BadRequestException` / `ContentTransformationException` / `CannotTransformContentToTypeException`, and `RequestConverterKt.convertBody` bytecode from `ktor-server-content-negotiation-jvm-3.3.3.jar` (see finding 1).
