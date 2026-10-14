# T11 Meta-Review — Ktor Chat Route

- **Commit:** `27563028ecb6e30e8c9ade21f6f0e1fcac49e82e` ("T11: Ktor Chat Route")
- **Branch:** `final`
- **Date:** 2026-10-14
- **Verification:** `./gradlew --offline ktlintCheck detekt test --rerun-tasks` → BUILD SUCCESSFUL (all 13 tasks executed, incl. concurrent T12 files present in the tree)

## 1. Invariants (scaffold rules)

| Rule | Status | Evidence |
|---|---|---|
| ktlintCheck / detekt / test 0 errors | OK | Forced re-run green; detekt `maxIssues: 0` |
| LongMethod ≤ 40 | OK | `handleChat` ~20 lines (ChatRoutes.kt:44-61) |
| LongParameterList ≤ 5 | OK | `runTurn` 3 params + receiver; tests within caps |
| TooManyFunctions ≤ 15 | OK | ChatRoutesTest: 9 tests + 1 helper = 10 |
| NestedBlockDepth ≤ 3 | OK | detekt green |
| MaxLineLength ≤ 120 | OK | ktlint/detekt green |
| No wildcard imports | OK | detekt `WildcardImport` active, green |
| Masked errors (no SQL/creds/provider text) | OK | Fixed constants + generic 500; test asserts no leak |
| D-7 no streaming primitives | OK | `grep -rEn "text/event-stream|chunked|webSocket|EventSource" src/main/kotlin/com/fuelingchat/routes` → no matches |
| Placeholder never resolved at install/boot | OK | `singleton { error(...) }` is lazy; route lambda invokes `chatService()` only per request; BootFailFastTest + DiSmokeTest green (DiSmokeTest does not resolve ChatService) |
| No N+1 / non-empty tool set (D-8) | N/A | Service-layer concerns (T12); route performs no queries |

## 2. Logic / Correctness

- **Route holds no business logic**: `ChatRoutes.kt` only parses, validates, delegates, responds. Validation of `prompt`/`provider` is explicitly assigned to the API layer by the scaffold (block2). OK.
- **Lazy seam**: `chatRoutes { di.direct.instance<ChatService>() }` captures the lambda; `handleChat` calls `chatService()` per request, after validation (ChatRoutes.kt:55). Install/boot never resolves the binding — proven by BootFailFastTest `serverStartsAndAnswersHttp` (404 for `/`) staying green.
- **CancellationException rethrown** before the catch-all (ChatRoutes.kt:62-63). OK.
- **Masking chain** (known deviation: StatusPages not on classpath — verified, `build.gradle.kts` has no `ktor-server-status-pages`):
  - `BadRequestException` catches `UnsupportedMediaTypeException` (wrong content type) — test-proven.
  - Bytecode-verified: Ktor 3.3.3's `ExperimentalJsonConverter.deserialize` throws `JsonConvertException`, and `JsonConvertException extends ContentConvertException` — so every decode-level failure (missing field, wrong type, truncated JSON) hits the `is ContentConvertException` branch → masked 400.
  - `SerializationException` backstops any unwrapped kotlinx failure; `ContentTransformationException` covers conversion plumbing.
  - Unmapped exceptions → generic `{"error": "internal server error"}`.
- **ChatFailure mapping**: `HttpStatusCode.fromValue(error.status)` with `error.maskedMessage` — 500/502/400 all pass through as contracted; status value is service-controlled (contract fixes 400/500/502). OK.
- **elapsed_ms**: `TimeSource.Monotonic.markNow()` as the first statement of `handleChat`; `inWholeMilliseconds.toInt()` — integer ≥ 0, monotonic. Measured just before `respond()` (serialization microseconds excluded) — negligible, standard interpretation of "route entry → response ready". Informational only.

## 3. API Contract (CONTRACT-API-001/002)

- DTOs match field-for-field: `ChatRequest(prompt, fueling_id, provider)`, `ChatResponse(summary, provider, elapsed_ms)` — exactly three 200 fields (G-6), enforced by the test's exact key-set assertion.
- 400 cases: blank prompt (empty + whitespace-only — verified empirically that the whitespace-only body with literal `\t\n` parses under Ktor's lenient `DefaultJson` and reaches the `isBlank()` branch, not the malformed-JSON branch), invalid provider, malformed JSON / wrong content type — all masked.
- Absent `provider` → `null` to the seam (D-10 default is the service's concern). OK.
- Error bodies: exactly `{"error": ...}`; no SQL/credentials/provider text (test asserts sentinel + `SELECT` absent).

## 4. Tests (ChatRoutesTest, 9/9 green — forced run; pre-rerun XML: tests=9 failures=0 errors=0)

All 8 scaffold cases covered by 9 tests (case 8 split into two):

| Scaffold case | Test |
|---|---|
| 1. 200 exact shape + elapsed_ms | `successReturnsExactlyTheThreeContractKeys` |
| 2. 400 blank prompt (empty + whitespace) | `blankPromptIsRejectedWithMasked400` |
| 3. 400 invalid provider | `invalidProviderIsRejectedWithMasked400` |
| 4. 400 malformed JSON / wrong content type | `malformedJsonIsRejectedWithMasked400` |
| 5. ChatFailure 500 masked | `chatFailure500MapsToHttp500WithItsMaskedMessage` |
| 6. ChatFailure 502 masked | `chatFailure502MapsToHttp502WithItsMaskedMessage` |
| 7. Unmapped exception → generic masked 500 | `unmappedExceptionIsMaskedAsGeneric500` |
| 8. fueling_id/provider passthrough; absent provider → null | `fuelingIdAndProviderReachTheServiceUnchanged` + `absentProviderReachesTheServiceAsNull` |

Assertions are live, not tautological: exact status codes, exact key sets, message contents, leak-absence checks (sentinel, `Exception`, `kotlin.`, `io.ktor.`, `java.lang.`), and the recording fake asserts both seam arguments and that rejected requests never reach the service (`fake.calls.isEmpty()`). Tests build their own application module; no production DI, no network, no DB. OK.

## 5. Style / Complexity

ktlint + detekt green with `maxIssues: 0`. `TooGenericExceptionCaught` suppressed with a documented justification (last-resort masking layer). No wildcard imports; lines ≤ 120.

## 6. Findings

| # | Severity | Location | Finding |
|---|---|---|---|
| 1 | Low | `docs/tasks/T11.md:3` | Task file `**Status:**` still says `todo` while `docs/tasks/INDEX.md:19` (updated by this commit) says `in progress`. Completed siblings (e.g. T10) keep both in sync; the T11 commit flipped only INDEX. Stale task-file status. Also all acceptance checkboxes remain unchecked — expected until acceptance, but the Status line should read `in progress`. |
| 2 | Low | `src/test/kotlin/com/fuelingchat/routes/ChatRoutesTest.kt:61` | Comment "The JSON escapes below decode to a tab and a newline" is inaccurate: the raw string embeds literal control characters (not escapes); they are accepted because Ktor's `DefaultJson` runs `isLenient = true` (verified empirically). The test still correctly exercises the blank-prompt branch, so this is a comment-only nit. |
| 3 | Info | `src/main/kotlin/com/fuelingchat/routes/ChatRoutes.kt:58` | `elapsed_ms` is computed immediately before `respond()`; body serialization microseconds are excluded from "route entry → response ready". Negligible and the standard interpretation — noted for completeness, not a defect. |
| 4 | Info | `src/main/kotlin/com/fuelingchat/routes/ChatRoutes.kt:68` | `HttpStatusCode.fromValue` trusts the service-supplied status (contract restricts it to 400/500/502); an out-of-range value would surface verbatim. Service-controlled input, acceptable. |

No correctness, contract, or test-coverage defects found. The malformed-JSON coverage gap for `{}`/wrong-type bodies is closed by construction (bytecode-verified `JsonConvertException : ContentConvertException`), not by an explicit test — acceptable.

VERDICT: PASS
