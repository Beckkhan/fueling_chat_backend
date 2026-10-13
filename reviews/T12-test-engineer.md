VERDICT: PASS

# T12 — Test Engineer Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T13:27:10Z

Review target: HEAD `47c0d2d` (`T12: ChatService & DialogHistory`, diff vs parent `1506332`). Working tree clean for `src/` and `build.gradle.kts` (only `metrics/cto-ledger.md` modified), so the reviewed code equals the committed code.

## Test evidence (live, this round)

- `./gradlew test --rerun-tasks --no-build-cache` — **BUILD SUCCESSFUL** (30s, `:test` executed, not cached). 12 suites, **68 tests, 0 failures, 0 errors, 0 skipped**. T12 suites: `ChatServiceTest` 10/10, `DialogHistoryTest` 3/3, `ProviderRouterTest` 3/3.
- Second, independent run of only the T12 classes (`cleanTest test --tests com.fuelingchat.service.{ChatService,DialogHistory,ProviderRouter}Test`) — **BUILD SUCCESSFUL**, 16/16 green, confirming no order dependence on the rest of the suite.
- Docker Postgres on `localhost:25432` up; DB-backed suites (`DbInfrastructureTest`, `GetFuelingInfoToolIntegrationTest`, `BootFailFastTest`) green against it. None of the T12 tests touch a DB or the network (grep for `http|jdbc|localhost|Thread.sleep|Random|Instant.now|currentTimeMillis` over the three T12 test files matches only the `ai.koog.http.client.KoogHttpClientException` import — the exception type under test, not a call).

## Acceptance criteria — test coverage (AC8)

All required unit tests from the task's final acceptance criterion exist and are meaningful:

- **D-9 injection present/absent on the system prompt** — `ChatServiceTest.injectsAttachedFuelingIdLineWhenFuelingIdPresent` asserts the exact line `attached fueling_id: F-10001 — use exactly this value for get_fueling_info` is contained in the captured `systemPrompt` argument; `omitsAttachedFuelingIdLineWhenFuelingIdAbsent` asserts no `attached fueling_id` text and that the `get_fueling_info` instruction is still there (D-6 language instruction asserted in the present case). Verified byte-level: implementation and test literal both use U+2014 em dash.
- **History = last N with correct roles** — `passesOnlyTheLastHistoryLimitMessagesWithRoles` seeds 3 turns (6 messages) with `historyLimit = 4`, captures the `run` arguments via MockK slots, and asserts exactly `[user q1, assistant a1, user q2, assistant a2]` plus the new `userMessage` is passed separately. `recent` returns a copy (`takeLast`), so the captured list is the pre-turn context — the assertion is sound.
- **Tool set never empty / pinned seam** — `passesExactlyTheFuelingInfoToolNeverEmpty` asserts `tools.size == 1` and `assertSame` identity with the injected `GetFuelingInfoTool`.
- **Journal = two JSONL lines matching cache** — `DialogHistoryTest.appendsUserAndAssistantLinesMatchingCache` injects a deterministic clock, then compares both `recent(10)` and the JSON-decoded file lines (each line decoded independently, so a single line containing two objects would fail) to the expected `ChatMessage` list.
- **Truncate-on-restart** — `restartTruncatesJournalAndClearsCache` appends on a first instance, asserts the journal is non-empty, constructs a second `DialogHistory` on the same file, and asserts the cache is empty and `Files.readAllLines` is empty (D-1 observable).
- **Provider routing incl. default and 400** — `ProviderRouterTest`: `"local"`/`"remote"` resolve the correctly tagged Kodein bindings (`assertSame` on the mock identities), absent → `local` (D-10), unknown `"cloud"` → `ChatFailure(400, "provider must be \"local\" or \"remote\"")` with an explicit assertion that the offending provider value does not leak into the masked message. `ChatServiceImpl` returns `ChatTurn(summary, selection.name)`, asserted for the `remote` path.
- **500/502 classification** — `dbFailureMapsToMasked500` (`ExposedSQLException` → `ChatFailure(500, "Databases unavailable")`, plus cache left empty so the failed turn is not journaled), and three 502 cases (`LLMClientException`, `KoogHttpClientException`, `AIAgentException` → `ChatFailure(502, "Provider unreachable")`). `unclassifiedExceptionPropagatesUnmasked` confirms non-listed exceptions are not swallowed into a masked failure.
- **Success path** — `appendsBothMessagesAndReturnsTurnWithEchoedProvider` asserts the returned `ChatTurn("the answer", "remote")` and that both user and assistant messages land in `DialogHistory`.

Changed/new main functions and their coverage: `DialogHistory.init/appendTurn/recent` covered; `ProviderRouter.resolve` covered (all four branches); `ChatServiceImpl.chat` and its `systemPrompt`/`classifiedFailure` helpers covered; `ChatMessage` `@Serializable` exercised through a real encode/decode round-trip. **Gap (non-blocking, see Findings):** `AppModule.historyFailure()` (new boot helper in this diff) has no test. `ServiceModule`'s real bindings are untested by design — the task explicitly defers `DiSmokeTest` additions and app-graph `ProviderRouter` resolution to T14 because both `LlmClient` bindings remain `error()` stubs until T13/T14.

## Isolation, determinism, mocking

- No shared mutable state: each test builds its own `ChatServiceImpl` on fresh MockK mocks and its own `DialogHistory`; `@TempDir` is per-test-method. Journal files are additionally disambiguated by `System.nanoTime()` within `ChatServiceTest` — harmless since `@TempDir` is already unique per method, and it is used only for a filename, never in an assertion.
- No order dependence — proven by the filtered second run of only the T12 classes, green.
- Deterministic: time is injected everywhere it is asserted (`DialogHistory`'s `clock` lambda; `{ 1L }` / incrementing lambdas in tests). No sleeps, no wall-clock assertions, no randomness in T12 tests.
- Mocking quality: the `LlmClient` seam (spec §3 pinned surface) is mocked with MockK in every path; no real network or provider call is reachable. `GetFuelingInfoTool` is mocked. Kodein test DI mirrors production tags (`local`/`remote`) exactly. `ExposedSQLException` is a MockK mock — no DB connection is opened by these tests (DB suites elsewhere use the local Docker container).
- Masking discipline is asserted, not assumed: 400 message excludes the raw provider value; 500/502 carry the exact masked strings.

## Findings

None blocking.

## Notes (non-blocking, for T13/T14)

1. `AppModule.historyFailure()` has no fail-fast boot test. The precedent exists — `BootFailFastTest` covers the other three boot helpers; a fourth case using e.g. `DIALOG_HISTORY_FILE=/dev/null/dialog.jsonl` (parent is not a directory → `IOException` from `Files.createDirectories`) would pin the exit-1 + message path. Recommend landing it in T14 together with the deferred `DiSmokeTest` additions.
2. The 400 produced by `ProviderRouter` is asserted at router level only; there is no `ChatServiceImpl`-level test that the router's `ChatFailure` passes through unmasked. Route-level invalid providers are rejected before the service (T11), so this only affects direct service callers. Low risk.
3. Append-only across turns within one session (two turns → four lines) is not asserted; the journal line count is pinned only for the single-turn case. The restart test does show a written line surviving into the second construction before truncation.
4. `unclassifiedExceptionPropagatesUnmasked` asserts non-null but not instance identity or message; a `assertSame`/message assertion would be marginally stronger.
5. Journal format is pinned at the data-class level only: the tests decode lines back into `ChatMessage`, so a field rename (`body` → `text`) would still pass. An explicit raw-key assertion on one line would pin the spec's `{role, body, timestamp}` wire shape.

## Verdict

`T12 done | verified: ok`
