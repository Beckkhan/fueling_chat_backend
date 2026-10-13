# T12 — Simplicity Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T17:14:30Z

## Scope

Commit `f032e39` ("T12: ChatService & DialogHistory", 9 files) reviewed against `docs/tasks/T12.md` and CONTRACT-SERVICE-001 / CONTRACT-HISTORY-001 (mapped in `docs/tasks/INDEX.md` onto spec §2/§3 and D-1, D-6, D-8, D-9, D-10, D-11). Gates 1–2 are PASS. `git diff f032e39 --stat -- src docs` is empty — the reviewed tree is the commit; only out-of-scope `metrics/cto-ledger.md` is modified. Build/test status is relied on from gates 1–2 (forced `./gradlew build ktlintCheck detekt`, 0 errors), which ran on this identical tree.

Branch context: architect-scaffold → implementer-fill pipeline, so class/function signatures were pinned before implementation. Findings below focus on what a body-fill could change; scaffold-pinned structure is judged as-is.

## Verification performed

- Read the full diff of `f032e39`, the current content of all touched main/test files, and the supporting types (`ChatFailure`, `ChatTurn`, `LlmClient`, `ChatRoutes`, `ClientModule`, `AppConfig`, `ServiceModule`).
- Grepped the whole `src` tree for every symbol the commit introduces (`ProviderSelection`, `DefaultChatService`, `DialogHistory`, `ProviderRouter`, `ChatMessage.ROLE_*`, `recent(`, `appendTurn(`) and for the literal strings the commit adds ("attached fueling_id", "Invalid provider", the 500/502 messages, the prompt sentences): no orphaned declaration, no unused import, no second copy of the prompt text. All main-code call sites are the DI wiring plus `DefaultChatService`.
- Dead-code sweep: no leftover scaffold `TODO`/`FIXME` in `service/`, `infra/llm/` or the new tests; no unused private member (would have been flagged by detekt `UnusedPrivateMember`, gate 1: 0 findings) and no unused import (ktlint, 0 errors).
- Cross-checked the only pre-existing string overlap — `ChatRoutes.validateProvider` vs `ProviderRouter.route` — and against branch `first`'s T12 to establish it is a decomposition artifact, not a new copy.

## Findings (non-blocking)

1. **Provider-name set and 400 message duplicated across layers.** `ChatRoutes.kt:98-101` (T11) and `ProviderRouter.kt:24-27` (T12) each spell out `"local"`/`"remote"` and the literal `"Invalid provider"`. The router's own mapping is AC-mandated defense-in-depth and the route file is outside T12's artifact list (T11, already committed), so the implementer could not de-duplicate it here; branch `first` carries the same two-copy shape. Recommendation for T13/T14 or the T15 wiring pass: expose the names/message once on `ProviderRouter` and have the route call it (or drop the route-level check, since the router now raises the identical masked 400).
2. **Test-fixture data repeated across the two new test classes.** The 4-element `expectedRoles` list and `listOf("q2","a2","q3","a3")` appear in both `ChatServiceTest` (last-N pass-through) and `DialogHistoryTest` (`recent` semantics), and the three failure-classification tests are three near-identical bodies. This is deliberate per-unit isolation — each class pins its own unit's behavior — so I judge it acceptable; the shared role constants already live in `ChatMessage` so no *role* literal is duplicated.
3. **`historyLimit` is threaded to two constructors from one config value.** `ServiceModule.kt:18,32` binds `AppConfig.historyLimit` into both `DialogHistory` and `DefaultChatService`; production only ever uses the explicit path (`recent(historyLimit)`), so `DialogHistory.historyLimit`'s sole consumer is the default argument of `recent()`. Both paths are explicitly in the AC ("History passed = `DialogHistory.recent(historyLimit)`" and `recent(limit)` with the HISTORY_LIMIT default), and the constructors are scaffold-pinned — the redundancy is AC-level, not an implementer choice. Non-blocking.
4. **`recent()`'s non-positive guard** (`DialogHistory.kt:33`) is behavior beyond the AC: without it, a negative limit would throw `IllegalArgumentException` from `takeLast`. One line plus one test, defensible hardening of a public API (the limit is config-fed). Judged acceptable, not a feature.

## Judged non-issues (prior gates' candidates)

- **Classification branches whose reachability depends on T13/T14** — all four mappings are mandated verbatim by the AC (500 for `ExposedSQLException`; 502 for `LLMClientException`/`KoogHttpClientException`/`AIAgentException`), so they are not YAGNI and not dead code; verifying reachability with real clients belongs to T13/T14. The untested `KoogHttpClientException` branch is contract-required code, and gate 1 already filed the coverage note.
- **Journal truncation at first `ChatService` resolution rather than in `AppModule.boot()`** — the AC says "truncated at construction (startup)", which this matches; construction-time truncation is the simpler of the two (no extra boot hook, no lifecycle wiring), and the singleton binding guarantees exactly one truncation per process.
- **`recent()` not under the mutex; cache-before-file ordering in `appendTurn`** — correctness-domain questions already adjudicated non-blocking by gate 2; locking reads would add code, not remove it, so there is no simplicity action here.
- **`ChatService` interface with a single `DefaultChatService` implementation** — the pinned T11 seam the AC explicitly says to implement; the route depends only on the interface. Not an unnecessary abstraction.
- **`toolSet()` and `truncateJournal()` as one-call private helpers** — scaffold structure where each name states a contract invariant (never-empty tool set, truncate-on-startup); they add naming, not indirection. `buildString` with four appends versus a constant is a wash; no duplication either way.
- **Three separate 502 `catch` clauses** — Kotlin's multi-type catch is separate clauses; each maps a distinct Koog class, matching the AC field-for-field. Not foldable DRY-wise without a `when`-over-Exception indirection that would be longer.

## YAGNI / duplication / dead-code summary

- YAGNI: nothing beyond the ACs other than the `recent()` guard (finding 4). No unused parameter, no speculative API. The 400 message, default-provider fallback and D-9 line each map to an AC line.
- Duplication: one cross-layer copy of the provider names/message (finding 1, inherited from T11's route) and test-local fixture repetition (finding 2, accepted). No duplicated production logic or literals within T12's scope.
- Over-abstraction: none introduced. `ProviderSelection` (name + client) exists to echo the provider actually used, as the AC requires; everything else is the pinned seam.
- Dead code: none — no unused imports, private members, helpers or tests; the journal/`recent` API surface is exercised from production and tests.

## Verdict

`T12-simplicity-reviewer: PASS | verified: ok` — no blocking findings. Recommendation 1 (single source for the provider names / "Invalid provider") is the only actionable cleanup, best folded into T13/T14 or T15.
