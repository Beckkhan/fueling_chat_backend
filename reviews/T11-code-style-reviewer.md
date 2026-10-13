# T11 — Code Style Review (round 2 — re-verification of the round-1 FAIL)

**Verdict:** PASS
**Timestamp:** 2026-10-13T22:59:11Z

Review target: amended integrated T11 commit `c5b691b` ("T11: integrate Ktor Chat Route") on branch `third`, diff vs parent `7b92eef`, plus `docs/tasks/T11.md`. Tree verified equal to the commit (`git diff HEAD -- src build.gradle.kts` empty; `git hash-object` equals `git rev-parse HEAD:<path>` for `ChatRoutes.kt`, `ChatRoutesTest.kt`, `ChatRequest.kt`).

## Linter evidence (live, this gate)

- `./gradlew ktlintCheck detekt --rerun-tasks` → **BUILD SUCCESSFUL**, 8 tasks executed, exit 0. Final result line: `BUILD SUCCESSFUL in 21s / 8 actionable tasks: 8 executed`. Fresh artifacts from this run: `build/reports/detekt/detekt.txt` 0 bytes, `detekt.md` reads `Findings (0)`, `ktlintMainSourceSetCheck.txt` / `ktlintTestSourceSetCheck.txt` / `ktlintKotlinScriptCheck.txt` all 0 bytes. detekt config unchanged (`maxIssues: 0`). → **0 linter errors: PASSES.**
- Auto-fix: `./gradlew ktlintFormat --rerun-tasks` → BUILD SUCCESSFUL and **changed nothing** (`git status --porcelain -- src build.gradle.kts` empty afterwards). Nothing pending to auto-fix.
- **Zero suppressions**: `grep -rn "@Suppress\|ktlint-disable\|@file:Suppress" src/` returns nothing; no detekt baseline file exists. The round-1 lone suppression is gone.

## Round-1 blocking findings — re-verified on the rework

Counting convention unchanged from round 1: signature line → closing brace inclusive, nested lambdas counted in the enclosing function.

1. **RESOLVED — `chatRoutes` 47 lines.** `ChatRoutes.kt` now decomposes into private helpers, all ≤ 30: `chatRoutes` 34–48 = 15, `handleChat` 50–58 = 9, `receiveValidRequest` 63–80 = 18, `runTurn` 86–102 = 17, `respondMaskedFailure` 104–111 = 8, `respondMaskedError` 117–122 = 6. The nested `post("/chat")` lambda is 3 lines (44–46). Max in file = 18.
2. **RESOLVED — `unmappedExceptionReturnsGenericMasked500` 34 lines.** Now `ChatRoutesTest.kt:226–250` = 25; the leak checks moved into `assertNoInternalDetailsLeak` (267–276 = 10).
3. **RESOLVED — `ok200ReturnsExactlyThreeFieldsAndNonNegativeElapsed` 31 lines.** Now `ChatRoutesTest.kt:42–67` = 26; the shape assertions moved into `assertFrozenSuccessShape` (253–264 = 12).
4. **RESOLVED (was non-blocking) — the lone `@Suppress`.** Removed; the unused catch bindings are now `_` (`ChatRoutes.kt:67,69,71,98`) and detekt stays green with no function-level suppression. Matches the sibling-branch zero-suppression precedent.

## Findings — non-blocking (carried over from round 1; not part of the formal handback)

1. **KDoc line budget** (spec §5: function ≤ 2 content lines): `ChatRoutes.kt:24–33` `chatRoutes` KDoc 6 (over by 4), `ChatRequest.kt:22–27` `validationError` KDoc 4 (over by 2), `ChatService.kt:19–23` `run` KDoc 3 (over by 1, architect's TODO(T12) marker text). Content is contract/decision documentation (the explicitly allowed category) and the branch-`second` gate recorded the same class of deviation non-blocking. All KDoc added by the rework conforms (helper KDocs 1–2 content lines each; test-helper KDocs 1 each).
2. **Comment volume** (noise-level): the rework added 3 helper KDocs + 2 one-line test-helper KDocs, so T11's comment delta vs branch `first`'s zero grows slightly. Every comment cites a contract/decision; nothing restates a name or explains syntax. Trim candidates remain as in round 1 (`ChatRequest.kt:35–37` on the self-evident `effectiveProvider`, `build.gradle.kts:43`).

## Conforming checks

- **Functions ≤ 30 lines** — every function in the T11 diff, mechanical cross-check: max = `invalidProviderReturns400MaskedError` = 30 (at cap); next `ok200ReturnsExactlyThreeFieldsAndNonNegativeElapsed` / `databaseFailureClassReturnsMasked500` / `providerFailureClassReturnsMasked502` = 26; all private helpers ≤ 18; `AppModule.kt` max `boot` = 16; `ChatRequest.kt` max `validationError` = 6. PASS.
- **≤ 4 parameters** — maximum 3 (`FakeChatService` and DTO constructors; `assertFrozenSuccessShape` 3); `runTurn` 2, `respondMaskedError` 2. PASS.
- **No trivial KDoc** — no KDoc on a trivial class/type, no comment before an obvious function, no name repetition, no syntax explanation. The rework's new helper KDocs document behavior not in the signatures (masked-400 side effect, failure mapping, CancellationException propagation). PASS.
- **DTO shape** — `ChatRequest`, `ChatResponse`, `ChatErrorResponse`, `ChatTurn`, `ChatServiceRequest` are `data class`; no hierarchy in this diff. PASS.

## Verdict

`T11-code-style-reviewer: PASS` — all four round-1 findings resolved on the amended commit; `./gradlew ktlintCheck detekt` green with 0 errors on a forced fresh run, `ktlintFormat` a no-op, zero suppressions, and every function ≤ 30 lines / ≤ 4 parameters with no trivial KDoc. Two non-blocking should-fixes remain for a future touch of these files (KDoc budget, comment volume).
