VERDICT: PASS

# T12 — Simplicity Review

**Timestamp:** 2026-10-13T13:37:59Z
**Target:** commit `47c0d2d` `T12: ChatService & DialogHistory`, diff vs parent `1506332`.
Both prerequisite gates are PASS (`reviews/T12-test-engineer.md`, `reviews/T12-correctness-reviewer.md`). Working tree differs from the commit only in `metrics/cto-ledger.md` and the two untracked review files, so the reviewed code equals the committed code.

## Checks

- **YAGNI / speculative T13–T14 work — clean.** `ClientModule` bindings are still `error()` stubs, no `DiSmokeTest` additions were made (both deferred by the AC), and no journal-rebuild/recovery path was built even though design §4 describes one — correctly omitted. Everything in the diff traces to a T12 acceptance bullet, with the single exception noted in Finding 3.
- **Dead code — clean.** No TODO/placeholder markers remain in the service layer. All new imports in `AppModule.kt`, `ChatMessage.kt`, `ChatService.kt`, `DialogHistory.kt`, `ProviderRouter.kt`, `ServiceModule.kt`, and the three test files are used. All constructor parameters (`history`, `router`, `fuelingInfoTool`, `historyLimit`) and all new functions/constants are referenced (`ChatFailure.BAD_PROVIDER` from `ProviderRouter` + test; `ProviderSelection` from `ChatServiceImpl` + tests).
- **Over-abstraction — none found.** `ChatService`/`ChatServiceImpl` is the T11 pinned route seam, not new abstraction. `ProviderSelection` (Finding 4), the one-line `systemPrompt`/`attachedFuelingIdLine` helpers, and the `Mutex` are proportionate. `ProviderRouter(di)` resolving the *named* binding lazily matches the task text ("selects the named `LlmClient` binding") and is what keeps boot working while both clients are `error()` stubs.
- **Duplication — one real instance (Finding 1) and two nits; nothing blocking.** Failure classification lives only in `ChatServiceImpl.classifiedFailure`; the route merely passes `ChatFailure` through, so there is no second copy of the 500/502 table.

## Findings

1. **Provider-name knowledge duplicated across layers (minor, non-blocking).** The masked literal `"provider must be \"local\" or \"remote\""` and the `"local"`/`"remote"`/default-local rule exist twice: `ChatRoutes.kt:24-26,77-78` (`DEFAULT_PROVIDER`, `INVALID_PROVIDER_MESSAGE`, `validatedProvider`) and `ProviderRouter.kt:18-26` (`LOCAL_PROVIDER`, `REMOTE_PROVIDER`, `INVALID_PROVIDER_MESSAGE`, `providerName`). The *behavior* duplication is contract-mandated — T11's AC rejects an invalid provider at the route, T12's AC requires the same at the router — so the fix is only to give the literal a single owner (e.g. have the route reuse the router's constant/message instead of redeclaring it). Small, cosmetic, no behavioral risk; not grounds for rework.
2. **Over-wide visibility of new constants (nit).** `ProviderRouter.LOCAL_PROVIDER`/`REMOTE_PROVIDER` and `DialogHistory.USER_ROLE`/`ASSISTANT_ROLE` are `public const` but referenced only inside their own files (tests use literals). `private const` would narrow the surface; if Finding 1 is ever addressed, the provider constants would instead become the shared owner. No dead code — all are used in-file.
3. **`AppModule.historyFailure()` is outside the AC bullet list (considered, accepted).** It is production code added by this diff: `DialogHistory` now touches the filesystem on construction, and the design's fail-fast posture ("crashes at boot with a clear message — never at first request", design §3) makes the check consistent with `configFailure`/`databaseFailure`/`toolSetFailure`. Not speculative work for a later task. It is untested; the test-engineer already assigned that test to T14 (its note 1), so it is not a simplicity defect here.
4. **`ProviderSelection` value object (considered, accepted).** A two-field data class whose only job is returning the resolved name alongside the client so `ChatTurn.provider` can echo it. A `Pair` or re-deriving the name would be less clear, and the echo is an AC. Not over-abstraction.
5. **Injected clock in `DialogHistory` (considered, accepted).** `clock: () -> Long = System::currentTimeMillis` is never varied in production; tests vary it to pin exact timestamps (`{ now++ }`, `{ 1L }`). It is a single defaulted constructor parameter, not a wrapper type or interface, and it hardens the serialization/journal assertions; the AC could technically be met without it (compare `recent()` to decoded lines), but the extra surface is negligible and idiomatic. Not a YAGNI violation.
6. **Test-helper duplication (nit, tests only).** The tagged-binding `testDi(local, remote)` fixture is byte-identical in `ChatServiceTest.kt` and `ProviderRouterTest.kt`; a shared test fixture would remove it. Also `ChatServiceTest.dialogHistory()` appends a `System.nanoTime()` suffix that `@TempDir` already makes unnecessary (already noted by the test-engineer). Both are cosmetic and optional.

## Verdict

`PASS` — no blocking YAGNI, duplication, over-abstraction or dead-code findings. Findings 1 and 6 are optional cleanups; Findings 3–5 were examined against the caller's specific concerns and judged proportionate.
