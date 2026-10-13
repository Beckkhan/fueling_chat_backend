# T12 — Correctness Review

**Verdict:** PASS
**Target:** commit `d1aa8b7b8432a518ccdf14f2f23cf57a30d76c36` (T12: integrate ChatService & DialogHistory), diff vs parent `b7889fb`
**Reviewer:** correctness-reviewer (second gate)
**Timestamp:** 2026-10-13T23:24:59Z

No sequential prerequisite artifact exists for this parallel fan-out (`reviews/T12-test-engineer.md` not yet present); the verdict is based on the reviewed diff, the contracts, and independent execution.

## Scope of the diff

9 files: `docs/tasks/T12.md` (scaffold, +120), `infra/llm/ChatMessage.kt` (annotations only), new `service/ChatServiceImpl.kt` / `service/DialogHistory.kt` / `service/ProviderRouter.kt`, `service/ServiceModule.kt` (placeholder → real bindings), and the three new service test classes. The pinned T11 seam files (`service/ChatService.kt`, `service/ChatTurn.kt`, `service/ChatFailure.kt`, `infra/llm/LlmClient.kt`) and `AppModule.kt` / `DiSmokeTest.kt` / `build.gradle.kts` are untouched — confirmed by `git diff --name-only`.

## Contract checks

### CONTRACT-HISTORY-001 (D-1, D-11) — `DialogHistory.kt`

- [x] **Journal truncated at construction, cache starts empty** — `init` writes `""` with `CREATE, TRUNCATE_EXISTING` (`DialogHistory.kt:23-31`); `cache` is a fresh empty list (`:20`). D-1 "both cleared on restart" holds: on process restart the singleton is reconstructed (new empty cache) and the journal is re-truncated.
- [x] **In-memory cache is the only read source** — `recent()` reads `cache.takeLast(limit)` under the mutex (`:37-40`); nothing in the class ever reads the file back (greps: no `readString`/`readAllLines`/`Files.lines` in `src/main`). `takeLast` returns a copy, so callers cannot mutate the cache.
- [x] **Write-through per turn, two JSONL lines, kotlinx-serialization, append-only, Mutex-serialized** — `appendTurn` appends user line then assistant line to the cache and to the journal with `StandardOpenOption.APPEND` inside one `journalMutex.withLock` (`:47-60`); `Json.encodeToString(ChatMessage.serializer(), msg) + "\n"` (`:62-69`). JSON encoding escapes control characters, so an embedded newline in a summary cannot split a record across lines.
- [x] **`recent(limit)` defaults to `historyLimit` and returns the last N oldest-first** — Kotlin default argument `limit: Int = historyLimit` (`:37`), `limit <= 0 → emptyList()` guard (`:39`).
- [x] **D-11 defaults** — `DIALOG_HISTORY_FILE` default `data/dialog-history.jsonl` and `HISTORY_LIMIT` default 20 are read through `AppConfig` (`AppConfig.kt:24-25`); the module injects both (`ServiceModule.kt:15-20`). Journal parent directory is created (`DialogHistory.kt:24`); `data/` is gitignored.
- [x] **`ChatMessage` seam unchanged** — field list `{role, body, timestamp}` identical; only `@Serializable` and the `ROLE_USER`/`ROLE_ASSISTANT` constants added (matches the scaffold's pinned export).

### CONTRACT-SERVICE-001 (D-6/D-8/D-9/D-10) — `ChatServiceImpl.kt`, `ProviderRouter.kt`

- [x] **Pinned turn order** — `router.resolve` → `buildSystemPrompt(fuelingId)` → `history.recent(historyLimit)` (snapshot taken *before* this turn is appended) → `client.run(systemPrompt, historyMessages, request.prompt, toolSet())` → `history.appendTurn` → `ChatTurn(summary, selection.name)` (`ChatServiceImpl.kt:28-46`). Matches the scaffold's pinned order exactly.
- [x] **Tool set never empty (D-8, spec §3 invariant)** — `toolSet(): List<Tool<*, *>> = listOf(getFuelingInfoTool)` (`:77`), non-empty by construction, passed as the 4th argument of the pinned seam. Test pins size 1 and identity (`ChatServiceTest.kt:117-124`).
- [x] **D-9 branching exact** — when `fuelingId != null` the prompt carries the line `attached fueling_id: $fuelingId — use exactly this value for get_fueling_info` (`ChatServiceImpl.kt:67`); byte-verified U+2014 EM DASH, identical to the task's pinned line and the test constant (`ChatServiceTest.kt:26-27`). When null, no such line (`:66`); nothing in the idle path invites extraction, per D-9's "absent → LLM may extract". No LLM-side extraction is implemented anywhere.
- [x] **D-6** — first prompt line instructs answering in the language of the user's latest message (`:58-61`).
- [x] **D-10 + router name echo** — `ProviderRouter.resolve`: `null`/`"local"` → local, `"remote"` → remote, else `ChatFailure(PROVIDER, "Invalid provider")` (`ProviderRouter.kt:35-39`); the returned `ProviderSelection.name` (never the raw request value) is what `ChatTurn.provider` carries, matching CONTRACT-API-002's "provider that served the turn".
- [x] **Router 400 wording vs behavior** — the AC bullet reads `ChatFailure(400, masked)`, which the pinned `ChatFailureStatus {DATABASE, PROVIDER}` enum cannot express; the scaffold's normative cross-block interface pins the defensive branch to `ChatFailure(PROVIDER, MASKED_INVALID_PROVIDER)`. The HTTP-observable contract still holds: the T11 route pre-validates and returns a masked 400 before delegating (`ChatRequest.kt:28-33`, `ChatRoutesTest.kt:124`), so the router branch is unreachable over HTTP. Not a contract violation.
- [x] **Success path appends both messages** — append happens only after a successful run (`:44`); `elapsed_ms` is not produced here (route concern, T11) — `ChatTurn` carries exactly `{summary, provider}`, as the route expects.

### Failure classification (500/502, masked)

- [x] **`ExposedSQLException` → `ChatFailure(DATABASE, "Databases unavailable")`** (`ChatServiceImpl.kt:35-36, 88-90`) → route maps `DATABASE → 500` (`ChatRoutes.kt:107`).
- [x] **`LLMClientException` / `KoogHttpClientException` / `AIAgentException` → `ChatFailure(PROVIDER, "Provider unreachable")`** (`:37-42, 92-94`) → route maps `PROVIDER → 502` (`ChatRoutes.kt:108`).
- [x] **Every other exception propagates unclassified** — no broad `catch (e: Exception)` in the service; the route's generic branch masks it as `500 "Internal server error"` (`ChatRoutes.kt:98-100`). `CancellationException` is not intercepted, so the route's rethrow path is preserved. The catch order is safe: all four classes classify to PROVIDER except `ExposedSQLException`, so no subclass shadowing can misroute a DB failure to 502.
- [x] **No raw SQL / credentials / provider text in messages or logs** — both message constants are literals (`:103, :106`); the router message is a literal that never echoes the offending value (asserted by `ProviderRouterTest.unknownProviderThrowsMaskedFailure`); `ChatFailure` is constructed only with these constants. No logging statements exist in any file of this diff, so prompts, summaries and exception text cannot reach logs.
- [x] **D-9/failure interaction** — a failed turn appends nothing (asserted: `ChatServiceTest.exposedSqlFailureClassifiedAsDatabasesUnavailable` checks the cache is empty), so a failed turn cannot poison the next prompt's history.

### DI wiring — `ServiceModule.kt`

- [x] Placeholder replaced with the three pinned bindings, exactly as the scaffold's integrator section specifies (constructor-injection form, `instance<AppConfig>()` for both D-11 values, named `instance(tag = "local"/"remote")`).
- [x] Boot is unaffected: every binding is a lazy `singleton`, and the named `LlmClient` stubs stay `error()` (`infra/llm/ClientModule.kt:9-10`), so a request-time resolution of `ProviderRouter` is what would trip them — deliberately deferred to T13/T14 and masked as generic 500 by the route. `DiSmokeTest` (unchanged) resolves neither `ChatService` nor `ProviderRouter`, so it cannot break.
- [x] `AppModule.boot()`'s fail-fast checks (config / DB / non-empty tool set) are unchanged and still satisfied.

### Read-only DB / DDL-DML prohibition

- [x] No DB access, no `transaction { }`, no Exposed query API, no `SchemaUtils`/DDL/DML in any file of this diff. The single Exposed reference is `import org.jetbrains.exposed.exceptions.ExposedSQLException`, used purely as a caught exception type. Read-only guarantees (`readOnlyMode=always`, DDL-first description) are untouched and out of this task's scope.

## Independent verification

- `./gradlew build ktlintCheck detekt` → **BUILD SUCCESSFUL** (exit 0), all tasks up-to-date/from-cache against the current inputs (AC "0 errors" holds; integrator's run is reproducible).
- Test XMLs for this commit: `ChatServiceTest` **9 tests / 0 failures / 0 errors**, `DialogHistoryTest` **4 / 0 / 0**, `ProviderRouterTest` **4 / 0 / 0**. Full suite green (12 classes), including the untouched route, DB and tool suites.
- Greps over the diff: `SELECT|INSERT|UPDATE|DELETE|jdbc:|apiKey|password|secret|credential` in the new main files → no hits (one doc-comment mention); journal read-back calls in `src/main` → none; seam files / `build.gradle.kts` / `AppModule.kt` / `DiSmokeTest.kt` modifications in the commit → none.

## Findings

None.

## Notes (non-blocking, informational for the ledger)

1. `DialogHistory.cache` is never trimmed — it grows with the process lifetime while `recent()` only ever reads the tail. Not contract-violating (D-1 pins no bound, and the tail is all that is used), but on a long-lived process it is unbounded growth; the same applies to the append-only journal. Worth a look in a maintenance task, not a T12 defect.
2. In `appendTurn` the cache is updated before the two journal writes; if a write fails (disk full) the cache and file diverge and the `IOException` propagates unclassified to the route's generic 500. The journal is never read back, so the divergence is unobservable at runtime; a journal write failure after a successful LLM turn would however still surface as a 500 rather than a 200.
3. The D-1 truncation is deferred from boot to first request (lazy `singleton`). Semantically equivalent for restart clearing — the file is never a read source — but a stale `data/dialog-history.jsonl` can remain on disk until the first `/chat` arrives.
4. Carried forward from T11's review: `ChatRoutesTest.FakeChatService` still ignores its request, so prompt/`fueling_id`/effective-provider pass-through is unpinned at the route layer. T12 is not the place for it, but the service tests do pin the service-side arguments.

## Verdict

`T12 done | verified: ok`
