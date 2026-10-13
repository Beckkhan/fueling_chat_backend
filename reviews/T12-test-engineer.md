# T12 — Test Engineer Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T17:06:00Z

## Scope

Commit `f032e39` ("T12: ChatService & DialogHistory", 9 files) reviewed against `docs/tasks/T12.md`, CONTRACT-SERVICE-001 / CONTRACT-HISTORY-001 (as mapped in `docs/tasks/INDEX.md` onto spec §2/§3 and D-1, D-6, D-8, D-9, D-10, D-11). Working tree clean for all reviewed paths — only out-of-scope `metrics/cto-ledger.md` is modified.

## Verification performed

- Read the full diff of `f032e39`: `ChatService.kt`, `DialogHistory.kt`, `ProviderRouter.kt`, `ServiceModule.kt`, `ChatMessage.kt` (main) and the three new test files. Committed test sources match what the tests ran against (no uncommitted test edits).
- `./gradlew test ktlintCheck detekt --rerun-tasks` — BUILD SUCCESSFUL, 13 tasks executed (compile, test, all ktlint source-set tasks, detekt). `./gradlew build --rerun-tasks` — BUILD SUCCESSFUL, 16 tasks executed. The first, cached `./gradlew build ktlintCheck detekt` run was inconclusive, so the gate result rests on the forced re-runs. Fresh detekt report: 0 findings, 0 code smells; `detekt.xml` has an empty `<checkstyle>` element.
- JUnit XML (fresh): 68 tests, 0 failures, 0 errors, 0 skipped. T12 classes: `ChatServiceTest` 9/9, `DialogHistoryTest` 5/5, `ProviderRouterTest` 4/4.
- Isolation / flakiness audit of the three test files: JUnit per-method lifecycle (no `@TestInstance`, no `@BeforeAll`/`@AfterAll`, no companion state, no ordering annotations); `@TempDir` journal per test; fresh mocks per test in `@BeforeEach`; no sleeps, delays or wall-clock assertions (timestamps asserted only `> 0`); assertions on exact values, not tolerances.
- Mocking audit: `LlmClient` mocked with MockK and stubbed via `coEvery` on the suspend seam — no network; `ExposedSQLException` inline-mocked and thrown, no DB; `GetFuelingInfoTool` `mockk(relaxed = true)` is sound because the service only carries it in the tool list and never calls it. `coVerify(exactly = 0) { localClient.run(...) }` in the success test pins that only the routed client is invoked.
- No test instantiates `DialogHistory` on the real default path: no `data/` directory exists in the repo after the full suite, so nothing writes outside `@TempDir` (checked by `ls data` and `git status`).
- `grep` for `TODO` / `@Disabled` / `Assume` in the T12 sources and tests: none.

## Acceptance criteria check (test view)

- [x] D-9 system-prompt line: exact substring `attached fueling_id: F-10001 — use exactly this value for get_fueling_info` asserted on the captured `systemPrompt` argument when the id is present; absence asserted (`assertFalse(contains("attached fueling_id"))`) when `fuelingId` is null.
- [x] D-6 / tool-only instruction: captured system prompt asserted to contain "language" and "get_fueling_info".
- [x] History passed = `DialogHistory.recent(historyLimit)`: with three prior turns and `historyLimit = 4`, the captured history is exactly `[q2, a2, q3, a3]` with roles `[user, assistant, user, assistant]`, and does not contain the current prompt.
- [x] Never-empty tool set: captured `tools` is exactly `listOf(tool)` (size 1, `assertSame` on the injected `GetFuelingInfoTool`), passed as the 4th `LlmClient.run` argument.
- [x] Successful turn: `ChatTurn("summary", "remote")` returned (provider echoed from the router), both messages appended to the cache with correct roles/bodies, journal file has exactly 2 lines after one turn, and the non-routed client was never called.
- [x] Journal semantics (CONTRACT-HISTORY-001): one turn writes two JSONL lines that decode to the same `ChatMessage` objects as the cache (data-class equality, including roles/bodies/timestamp > 0); construction truncates a pre-existing journal to size 0 and starts with an empty cache (truncate-on-restart observable); `recent(limit)` returns the last N oldest-first with correct roles; `recent()` defaults to the construction `historyLimit`; non-positive limit returns empty (cache, not file, is the read source throughout — the tests never read the file for history).
- [x] Provider routing: `"local"` → local binding, `"remote"` → remote binding, `null` → local (default), unknown → `ChatFailure(400, "Invalid provider")` — all four branches asserted, plus the 400 carries only the masked message.
- [x] Failure classification: `ExposedSQLException` → `ChatFailure(500, "Databases unavailable")`; `LLMClientException` → 502 "Provider unreachable"; `AIAgentException` → 502 "Provider unreachable". Masked-message equality pins raw SQL/provider text out of the messages; no history is appended on any failure path. (The service layer contains no logging at all, so no raw text can leak via logs from T12 code.)
- [x] `./gradlew build ktlintCheck detekt` — 0 errors (forced re-runs; test, ktlint and detekt freshly executed).
- [x] `ServiceModule` and `DiSmokeTest`: out of the unit-test scope per the AC (DiSmokeTest additions land in T14, and `DiSmokeTest` does not resolve `ProviderRouter`/`ChatService`, so the `error()` stubs are never touched in tests); verified by inspection that the new bindings compile and stay lazy.

## Observations (non-blocking)

1. The `KoogHttpClientException` catch branch (one of the three 502 mappings in the AC) has no dedicated test. The mandated "500/502 classification" list is satisfied by the other three exception tests; the branch is trivially testable with one more `coEvery { … } throws` case. Natural home: T13/T14 when the real clients land.
2. The `userMessage` argument of `LlmClient.run` is matched with `any()` in all nine tests — a miswiring of that single argument would not fail. This is the residual of the T11 test-engineer observation #1; prompt / `fueling_id` / provider pass-through is otherwise covered (captured system prompt, router tests, `turn.provider`). One `slot<String>()` on the third argument would close it.
3. `Mutex` serialization of the journal is verified by inspection only (no concurrency test). This is acceptable — a real concurrency test would be inherently flaky, and the AC's test list requires per-turn JSONL content, which is asserted.
4. `ChatServiceTest` uses `runBlocking` in `assertThrows` blocks; this is the standard pattern for suspend seams in JUnit 5 and is deterministic (no dispatcher assumptions).

## Verdict

`T12-test-engineer: PASS | verified: ok`
