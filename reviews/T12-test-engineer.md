# T12 — Test Engineer Review (first quality gate)

**Verdict:** PASS
**Timestamp:** 2026-10-13T23:24:23Z
**Target:** commit `d1aa8b7b8432a518ccdf14f2f23cf57a30d76c36` (T12: integrate ChatService & DialogHistory), diff vs parent `b7889fb` (9 files, +732/-5; 3 new test files, 352 lines)
**Working tree:** `git diff d1aa8b7 -- src/ build.gradle.kts` is empty (only `metrics/cto-ledger.md` is dirty) — reviewed state matches the commit.

## Gate checks

### Tests run
- `./gradlew test --rerun-tasks` (cache bypassed, forced): **BUILD SUCCESSFUL, exit 0 — 67 tests / 0 failures / 0 errors / 0 skipped**. Per class: `ChatServiceTest` 9/9, `DialogHistoryTest` 4/4, `ProviderRouterTest` 4/4, plus the pre-existing 50 (routes, boot, DB, logging, config, tool) all green.
- Repeat forced run of the three new classes alone (`--tests …ChatServiceTest --tests …DialogHistoryTest --tests …ProviderRouterTest --rerun-tasks`): green again — no flakiness observed.

### Gate item → test mapping
- **MockK / recording fake `LlmClient`, no network, no DB** — `ChatServiceTest.kt:196-215` `RecordingLlmClient` implements the pinned `LlmClient` seam field-for-field and records every `run` call (`RunCall`); MockK is used for the four exception instances and for the `GetFuelingInfoTool` mock (`ChatServiceTest.kt:47`, `mockk<ExposedSQLException>()` etc.). Grep over the three new test files for `Thread.sleep|delay|random|Instant.now|nanoTime|localhost|http://|DbPools|transaction|jdbc` → no matches. `ProviderRouterTest` mocks both `LlmClient`s. No real LLM/network/DB anywhere in the new tests.
- **D-9 injection present/absent on the system-prompt argument** — `ChatServiceTest.attachedFuelingIdInjectedIntoSystemPromptWhenPresent` asserts `local.calls.single().systemPrompt.contains("attached fueling_id: F-10001 — use exactly this value for get_fueling_info")` (exact line, `:36-37`); `attachedFuelingIdLineAbsentWhenFuelingIdNull` asserts no `"attached fueling_id"` substring. Both are asserted on the argument actually passed to `run`, not on a call-existence check.
- **History passed = last N, correct roles** — `historyPassedIsLastLimitMessagesExcludingCurrentTurn` (`:98-113`): seeds `HISTORY_LIMIT + 2` turns, asserts recorded history size == 20, equals `recent(HISTORY_LIMIT)`, last two roles `[user, assistant]`, and the current turn's prompt is not included (snapshot-before-append). Last-N/oldest-first semantics are independently pinned with literal bodies in `DialogHistoryTest.recentReturnsLastLimitMessagesOldestFirst` (`q3","a3"`) and `recentDefaultsToHistoryLimit` (`a2","q3","a3"`).
- **Journal = two JSONL lines matching cache** — `DialogHistoryTest.appendTurnWritesTwoJsonlLinesMatchingCache` (`:31-46`): decodes each line with `Json.decodeFromString(ChatMessage.serializer(), …)`, asserts 2 lines, roles `[user, assistant]`, bodies `["q","a"]`, equal timestamps within the turn, and `decoded == history.recent(2)` (file matches cache). `ChatServiceTest.successfulRunAppendsTurnAndReturnsChatTurn` additionally asserts exactly 2 lines after one service turn, plus `ChatTurn(summary, provider)`.
- **Truncate-on-restart observable** — `DialogHistoryTest.constructionTruncatesJournalAndCacheStartsEmpty` (`:49-58`): pre-writes two stale lines, constructs `DialogHistory` on the same path, asserts the journal is `""` and `recent()` is empty (D-1 restart semantics observable).
- **Provider routing incl. default and unknown-masked** — `ProviderRouterTest`: local → local client, remote → remote client, `null` → local (default, D-10), `"openai"` → `ChatFailure(PROVIDER, "Invalid provider")` with an explicit `assertFalse(maskedMessage.contains("openai"))` (masking). Service-level routing is pinned in `ChatServiceTest.userMessageAndProviderPassThrough`: local request lands only on `local.calls`, remote only on `remote.calls` (`assertEquals(1, local.calls.size)`), and `ChatTurn.provider` echoes the selection. The HTTP 400 for an unknown provider remains pinned by the pre-existing `ChatRoutesTest.invalidProviderReturns400MaskedError` (still green); the router's defensive branch is unit-pinned as the scaffold requires.
- **500/502 classification** — `ChatServiceTest.exposedSqlFailureClassifiedAsDatabasesUnavailable`: `ExposedSQLException` → `ChatFailureStatus.DATABASE` + `"Databases unavailable"` on both `maskedMessage` and `message`, and asserts nothing was appended to history on failure. `providerAndToolLoopFailuresClassifiedAsProviderUnreachable`: each of `LLMClientException`, `KoogHttpClientException`, `AIAgentException` → `PROVIDER` + `"Provider unreachable"` (masked constants from the companion — no raw provider text). DATABASE→500 / PROVIDER→502 status mapping is route-level and already pinned by T11's `ChatRoutesTest`.
- **Never-empty tool set (D-8)** — `toolSetPassedIsNeverEmptyAndContainsFuelingTool`: recorded `tools` size == 1 and `assertSame(tool, tools.single())`.
- **`./gradlew test` passes** — yes, see above.

### Isolation and determinism
- Every test class gets a fresh `@TempDir` per test method (`ChatServiceTest.kt:41-42`, `DialogHistoryTest.kt:25-26`) and fresh `DialogHistory` / `RecordingLlmClient` / service instances in `@BeforeEach`; the journal path is per-test. No `@BeforeAll`/`@AfterAll`, no companion/static state, no shared mutable state, no order dependence (grep clean).
- No sleeps, no `Instant.now`/`nanoTime`, no random; the only clock use is production `System.currentTimeMillis()` inside `appendTurn`, and the one timestamp assertion compares two lines from the same turn — deterministic, cannot flake. Assertions are data-class equality/list equality on values produced by the same calls.
- No JUnit parallel-execution config exists (`junit-platform.properties` absent; `tasks.test { useJUnitPlatform() }` only) — suite runs sequentially; two forced runs identical.
- New tests use no DB. The DB-backed tests elsewhere (pre-existing, unchanged by this diff) ran against the local Docker `postgres:15-alpine` container on 25432 (observed `Up (healthy)`), never a production DB.

### Coverage of new/changed units
Every new/changed function is covered: `DialogHistory.recent`/`appendTurn` (+ construction truncation), `ProviderRouter.resolve` (all four branches), `ChatServiceImpl.run` / `buildSystemPrompt` (present + absent D-9) / `toolSet` / `classifyFailure` (all four catch branches), `ChatMessage` `@Serializable` + role constants (via journal round-trip). `ServiceModule` wiring has no new test — per the task scaffold this is intentional (`DiSmokeTest` additions land in T14; both named `LlmClient` bindings are `error()` stubs, so `ProviderRouter` cannot be resolved through the graph yet). No route-level integration test with the real service is possible until T13/T14, as the scaffold states.

## Findings

None — no blocking findings. Verdict is PASS.

## Non-blocking notes

1. `ChatServiceTest.kt:98-113` — the last-N assertion compares the recorded history against `history.recent(HISTORY_LIMIT)` (production vs production). A "return first N instead of last N" regression in `recent()` would not be caught by this test alone; it is caught by `DialogHistoryTest.recentReturnsLastLimitMessagesOldestFirst` / `recentDefaultsToHistoryLimit`, which pin bodies literally. Acceptable as-is; a literal expected-body assertion here would make the service test self-sufficient.
2. `DialogHistory.kt:32` — "cache is the only read source" holds structurally (`recent()` never touches the file), but no test proves the negative (e.g., append a stray journal line mid-life and assert `recent()` ignores it). The AC's enumerated test list does not require it.
3. `ChatServiceTest.kt:170-190` — provider-failure paths assert status/message but not the no-partial-append invariant (only the DB case asserts `history.recent()` is empty). All four catch branches share the same post-try `appendTurn` structure, so the invariant is covered once.
4. `ChatServiceTest.kt:76-88` — no test asserts the "answer fueling questions only via `get_fueling_info`" sentence in the system prompt; the D-8 tool-set argument is pinned instead (`toolSetPassedIsNeverEmptyAndContainsFuelingTool`). Not required by the AC test list.
