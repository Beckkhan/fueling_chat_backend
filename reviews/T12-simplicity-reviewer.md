# T12 — Simplicity Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T23:24:23Z
**Target:** integrated commit `d1aa8b7b8432a518ccdf14f2f23cf57a30d76c36` (T12: integrate ChatService & DialogHistory), diff vs parent `d1aa8b7^` = `b7889fb`. 9 files, +732/-5: 5 new source/test files, `ChatMessage.kt` (+10/-2), `ServiceModule.kt` (+29/-4), `docs/tasks/T12.md` (new, scaffold). `build.gradle.kts` untouched, as the task expected.
**Reviewer:** simplicity-reviewer (third gate)

## Findings

All non-blocking; none block the gate.

1. `src/main/kotlin/com/fuelingchat/service/ChatServiceImpl.kt:96-98` — the `else -> { throw cause }` arm of `classifyFailure` is unreachable at runtime: the only four call sites (`:35-43`) are catch clauses filtered to exactly the four handled classes. It is not deletable dead code — Kotlin requires the `when` used as an expression to be exhaustive, and the arm is documented (`// no other class reaches here`). Note only. The related double dispatch (four pinned catch clauses that re-dispatch in the `when`) is the task-mandated catch-clause structure and stays.
2. `src/main/kotlin/com/fuelingchat/service/DialogHistory.kt:39` — `if (limit <= 0) emptyList()` is behavior beyond the acceptance criteria (which pin only `recent(limit)` = last N) and is covered by no test. A one-expression defensive guard that keeps `takeLast` from throwing on a negative limit; deleting it would tighten the surface to the AC, keeping it is defensible. Either is fine; noted, no change requested.
3. `src/test/kotlin/com/fuelingchat/service/DialogHistoryTest.kt:13-14` and `src/test/kotlin/com/fuelingchat/service/ChatServiceTest.kt:24-25` — the fixture constants `JOURNAL_FILE_NAME = "history.jsonl"` and the limit `20` are declared privately in both test files (and `20` also exists as the AppConfig default). File-private, self-contained test fixtures are the repo's convention and create no cross-file coupling; not worth a shared fixture. Noted only.
4. Cross-task observation (not a T12 defect): the provider literals `"local"`/`"remote"` and the null→`"local"` normalization now exist in two places — `routes/ChatRequest.kt:33,38` (T11, `ALLOWED_PROVIDERS` / `effectiveProvider()`) and `service/ProviderRouter.kt:35,41-42` (`LOCAL_PROVIDER`/`REMOTE_PROVIDER`, null arm). Both files' shapes are pinned by their respective task scaffolds, T11 files have no owner in T12, and having the service layer reference the routes package would invert the dependency — so this duplication is accepted as-is.

## Notes (gate checks)

- **YAGNI:** the diff maps 1:1 onto the acceptance criteria and the pinned scaffold — no extra config, no caching layer, no streaming, no provider negotiation. The only two things beyond the literal AC are finding 2 and the parent-directory creation at `DialogHistory.kt:24`, the latter required for the documented default relative path `data/dialog-history.jsonl` (D-11). The scaffold-mandated structure (seam implementation carrying the pinned constructor shape, `ProviderRouter` with its defensive unknown-provider branch, `ProviderSelection`, the `MASKED_*` and role constants, `toolSet()`) is task-mandated and out of scope for YAGNI, including `ChatServiceImpl.historyLimit`, which duplicates `DialogHistory`'s own limit only because the T12 scaffold pins both the constructor parameter and the DI wiring that way.
- **No duplicated code (production):** the system-prompt builder exists once (`ChatServiceImpl.kt:55-71`); each masked message is defined once (companion `:103,:106`; `ProviderRouter.kt:45`); each masked message funnels through `classifyFailure`; the journal write path is one `appendLine` helper (`DialogHistory.kt:62-69`). No copy-pasted blocks in main sources.
- **No dead code:** every new constant and member has a live call site (`ProviderSelection.name` at `ChatServiceImpl.kt:45`, `.client` at `:34`; `MASKED_INVALID_PROVIDER` at `ProviderRouter.kt:37`; both masked companion constants at `:89,:93`; `ROLE_USER`/`ROLE_ASSISTANT` at `DialogHistory.kt:52-53`; `recent`'s default at test call sites). All imports in the five new/changed files resolve to a use; `ChatServiceTest.RecordingLlmClient` is instantiated at `:49-50`.
- **No unnecessary abstraction:** no new interface or wrapper was introduced. `buildSystemPrompt`, `toolSet`, `classifyFailure`, `appendLine` are single-purpose privates with one or two locally adjacent call sites; `ProviderSelection` is scaffold-pinned. `grep` finds no second implementation of `ChatService`/`LlmClient`/history in main sources.

## Evidence (this round, live)

- `git show d1aa8b7 --src-prefix... --stat` → 9 files, +732/-5, exactly the T12 Artifact/scaffold file set; no stray edits to T11 seam files (`ChatService.kt`, `ChatTurn.kt`, `ChatFailure.kt`, `LlmClient.kt` unchanged in the diff).
- `./gradlew detekt --rerun-tasks` → BUILD SUCCESSFUL; `build/reports/detekt/detekt.txt` 0 bytes and 0 `ruleId` entries in `detekt.sarif` — detekt's unused-import/unused-private-member rules report clean over HEAD, and `git status --porcelain` shows no source file differs from HEAD (only `metrics/cto-ledger.md` is dirty, outside this commit).
- `./gradlew test --rerun-tasks --tests 'com.fuelingchat.service.ChatServiceTest' --tests 'com.fuelingchat.service.DialogHistoryTest' --tests 'com.fuelingchat.service.ProviderRouterTest'` → BUILD SUCCESSFUL; reports: ChatServiceTest 9/0 failures, DialogHistoryTest 4/0, ProviderRouterTest 4/0.
- Manual use-site sweep for every new symbol (`grep -rn` over `src`): `MASKED_INVALID_PROVIDER`, `LOCAL_PROVIDER`, `REMOTE_PROVIDER`, `ProviderSelection`, `MASKED_DATABASES_UNAVAILABLE`, `MASKED_PROVIDER_UNREACHABLE`, `ROLE_USER`, `ROLE_ASSISTANT`, `recent(` — each resolves to production and/or test call sites as noted above.
