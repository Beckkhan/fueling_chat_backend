VERDICT: PASS

# T12 — Correctness Review

**Timestamp:** 2026-10-13T13:34:52Z
**Target:** commit `47c0d2d` `T12: ChatService & DialogHistory`, diff vs parent `1506332`.
Tree matches the commit (only `metrics/cto-ledger.md` modified; `reviews/T12-test-engineer.md` untracked), so the reviewed code equals the committed code. Independent forced re-run of the three T12 test classes (`cleanTest test --rerun-tasks --no-build-cache`): 16/16 green (ChatService 10, DialogHistory 3, ProviderRouter 3).

## Contract verification

**CONTRACT-HISTORY-001 (D-1, D-11) — PASS**
- In-memory cache (`messages`) is the only read source; no method reads the journal on the hot path (`DialogHistory.kt`).
- Write-through: `appendTurn` writes the user line + assistant line (one `Files.writeString(..., APPEND)` call, both JSONL lines in one write) and updates the cache only after the write succeeds, all under a `Mutex` (`recent` takes the same lock); a failed write leaves the cache unchanged.
- Journal format is the spec's `{role, body, timestamp}` via `@Serializable ChatMessage` (field names/types unchanged by the diff); `role` values are `"user"`/`"assistant"`.
- Truncate-on-restart: constructor `Files.newBufferedWriter(..., CREATE, TRUNCATE_EXISTING).use {}` (after `Files.createDirectories`) with the cache initialized empty; resolved once at boot via `AppModule.historyFailure()` and reused as a Kodein singleton, so truncation happens exactly once per startup and never mid-session (a mid-session truncation would be a real defect; single-instance semantics verified).
- Defaults match D-11: `DIALOG_HISTORY_FILE` → `data/dialog-history.jsonl`, `HISTORY_LIMIT` → 20 (`AppConfig.kt`); `recent(limit)` returns `messages.takeLast(limit)`.

**CONTRACT-SERVICE-001 (spec §3 seam, spec §2 error table, D-6, D-8, D-9, D-10) — PASS**
- Pinned seam respected: `LlmClient.run(systemPrompt, history, userMessage, tools)` called with exactly those four named arguments and types (system prompt built in service, history `= history.recent(historyLimit)`, tools assembled in service); `infra.llm` untouched apart from `@Serializable` on `ChatMessage`, no infra→service dependency.
- D-8: `tools = listOf(fuelingInfoTool)` — never empty by construction (`GetFuelingInfoTool : SimpleTool`) .
- D-6/D-9: system prompt contains the language-of-latest-message instruction plus the tool-only instruction; the `attached fueling_id: <id> — use exactly this value for get_fueling_info` line is appended only when `fuelingId != null`, byte-identical to the AC/design text (verified U+2014 em dash, not a hyphen).
- D-10: `null`/`"local"` → local, `"remote"` → remote, anything else → `ChatFailure(400, "provider must be \"local\" or \"remote\"")`; the offending value never appears in the message (asserted in tests). The used name is echoed via `ProviderSelection.name` → `ChatTurn.provider`. Route-level validation (T11) already normalizes absent→`local`, so both layers satisfy D-10.
- Failure classification matches the AC exactly: `ExposedSQLException` → 500 `"Databases unavailable"`; `LLMClientException`/`KoogHttpClientException`/`AIAgentException` → 502 `"Provider unreachable"`; everything else rethrown unmasked (so the router's `ChatFailure(400)` passes through and `CancellationException` propagates untouched — `throw error` rethrows the same instance). Journaling happens only after a successful run.
- Masking: all failure messages are compile-time constants; grep of the main-source diff for `password|apiKey|secret|jdbc:|SELECT|INSERT|UPDATE|DELETE|DROP|create table` → no matches; no logging added in service/DialogHistory/ProviderRouter.
- DI wiring: `ServiceModule` binds `DialogHistory`, `ProviderRouter`, `ChatService` (T11 placeholder removed). `ProviderRouter(di)` inside the singleton is correct — verified against the pinned `kodein-di-jvm:7.33.0` sources: the singleton creator's receiver is a `NoArgBindingDI` (`DirectDI`) whose `di`/`lazy` is backed by the container that resolved the binding, so in production it resolves the `client` module's tagged `LlmClient` bindings and in tests the local tagged DI.
- Read-only DB guarantees untouched: no DB code in the diff; `DbPools` keeps `isReadOnly = true` + `connectionInitSql = "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY"`; no DDL/DML introduced.
- `AppModule.historyFailure()` catch semantics follow the established boot pattern (`BootFailFastTest` proves DI singleton-creator exceptions propagate unwrapped), so an unwritable journal path fails fast with a named message instead of crashing with a stack trace.

## Findings

None blocking. No contract violations, logic errors, unmasked leaks, or edge-case failures found in the reviewed diff.

## Non-blocking observations (for T13/T14 and the record)

1. **Blank `fueling_id`** (`fueling_id: ""`): the route does not normalize blank to null, so a present-but-empty id injects `attached fueling_id:  — …` (empty value). Literally follows the contract ("if present, injected"; the blank rule exists only for `prompt`), so not a violation — only cosmetically odd, and only reachable by direct API callers.
2. **`HISTORY_LIMIT` ≤ 0 unvalidated**: `AppConfig` accepts any integer; a negative value makes `takeLast` throw per request (generic masked 500). Zero degrades to an empty history. A boot-time positivity check would fit the fail-fast posture; the default (20) is correct.
3. **DB-failure classification completeness**: the 500 mapping is keyed on `ExposedSQLException`. Statement-level SQL errors are wrapped by Exposed, but some pool/connection-acquisition failures may surface as plain `SQLException` and would then hit the route's generic 500 `"internal server error"` (still 500 and masked, so the spec §2 table is honoured; only the AC's exact string would differ). Worth pinning when the real tool loop is exercised in T13/T14.
4. **Boot ordering**: `historyFailure()` runs before `toolSetFailure()`, so a boot that fails on the tool descriptor still truncated the previous journal (the design's crash-debuggability window). Cosmetic; D-1 requires truncation at startup and the order is a judgement call.
5. The design §3 wording mentions "table structure per the DDL" in the system prompt; T12's AC enumerates the prompt without it, and spec §4's normative phrase constrains the LLM to *only* DDL-derived structure (carried by CONTRACT-TOOL-001's description). Judged conformant — recorded so the omission is a decision, not an accident.
