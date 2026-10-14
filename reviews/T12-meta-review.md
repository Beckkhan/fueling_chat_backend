# T12 Meta-Review — ChatService & DialogHistory

- **Commit:** `6ba8051983ee557151d5b4d54dad07553c546e59` ("T12: ChatService & DialogHistory")
- **Branch:** `final`
- **Date:** 2026-10-14
- **Verification:** `./gradlew --offline ktlintCheck detekt test --rerun-tasks` → BUILD SUCCESSFUL (13 tasks executed, 0 failures). Test XML: ChatServiceTest 10/0/0/0, DialogHistoryTest 5/0/0/0, ProviderRouterTest 4/0/0/0; all other suites green (BootFailFastTest 4, ChatRoutesTest 9, DiSmokeTest 1, …). No T13/T14 files were present in the tree at review time.

## 1. Invariants (scaffold rules)

| Rule | Status | Evidence |
|---|---|---|
| ktlintCheck / detekt / test 0 errors | OK | Forced re-run green; detekt `maxIssues: 0` |
| CONTRACT-HISTORY-001: cache is the only read source | OK | `recent()` reads only the `CopyOnWriteArrayList` cache (DialogHistory.kt:36); the journal is never read back |
| Journal append-only, Mutex-serialized | OK | Only construction writes without APPEND (truncate); `append` opens with `StandardOpenOption.APPEND` only, inside `journalMutex.withLock` (DialogHistory.kt:26-31) |
| Journal truncated at construction; cache starts empty | OK | `init` creates parent dirs and `Files.write(journalFile, ByteArray(0))` (CREATE+TRUNCATE_EXISTING); fresh cache per instance (DialogHistory.kt:20-23) |
| `recent(limit)` semantics | OK | `takeLast(limit.coerceAtLeast(0))`, default `historyLimit` (DialogHistory.kt:36); negative limit handled |
| Restarts clear context (D-1) | OK | Test `aSecondConstructionTruncatesTheJournalAndClearsTheCache` |
| ChatMessage fields unchanged + `@Serializable` | OK | Diff adds only the import, annotation and KDoc; `{role, body, timestamp}` intact |
| D-10 default = local | OK | `null`/`"local"` → `local`, `"remote"` → `remote`, else masked 400 (ProviderRouter.kt:12-16) |
| System prompt D-6/D-9 | OK | Language line + tool line always; `attached fueling_id: <id> — use exactly this value for get_fueling_info` present exactly when `fuelingId != null` (ChatServiceImpl.kt:67-74) — character-exact vs scaffold |
| Tool set never empty (D-8) | OK | `private val tools: List<Tool<*, *>> = listOf(getFuelingInfoTool)` — one element by construction (ChatServiceImpl.kt:36) |
| Client picked by resolved provider | OK | `clientFor`: `remote` → remoteClient, else localClient; `resolve` only ever returns the two constants (ChatServiceImpl.kt:64-65) |
| Write-through append on success only | OK | `appendTurn` runs after `runResolved` returns; all failure paths throw before append; tests assert empty journal/cache on 400/500/502 |
| `ChatTurn(summary, resolved)` | OK | Provider echoed from the router, not from the raw request (ChatServiceImpl.kt:44) |
| Masked errors; no raw internals in bodies or logs | OK | Every catch maps to a fixed constant; `ChatFailure` carries no cause (deliberate, documented); service and route never log errors; tests assert sentinels absent |
| No streaming primitives (D-7) | OK | `grep -rEn "text/event-stream|chunked|webSocket|EventSource|Flow<|callbackFlow" src/` → no matches |
| No elapsed_ms duplication | OK | `elapsed_ms` only in routes/ChatResponse.kt (T11); service layer free of it |
| No N+1 / Exposed queries | N/A | T12 adds no queries; DialogHistory is memory+file |
| Complexity/style caps | OK | detekt `maxIssues: 0`, ktlint green; no wildcard imports; lines ≤ 120 |

## 2. Seam conformance

- `ChatService.kt` (T11 seam) is untouched by this commit — verified, not in the diff.
- Implementation lives in `ChatServiceImpl.kt` implementing the seam, bound as `ChatService` in ServiceModule.kt — the documented scaffold deviation (block2) is honored.
- `infra/llm` stays business-free: the only change is the `@Serializable` annotation on `ChatMessage`.
- No `elapsed_ms` in the service; the route keeps wall-clock timing (T11).

## 3. DI wiring

- `bind<ChatService>` singleton constructs `ChatServiceImpl(instance(), instance(), instance(), instance(tag = "local"), instance(tag = "remote"))`; `ProviderRouter` and `DialogHistory` bound as singletons; `GetFuelingInfoTool` binding unchanged. Kodein `singleton {}` is lazy.
- Boot never resolves the LlmClient stubs: `AppModule.boot` resolves only AppConfig, DbPools/DatabaseProbe and GetFuelingInfoTool; the route's `chatService()` lambda resolves per request. BootFailFastTest `serverStartsAndAnswersHttp` (GET `/` → 404, no POST /chat) stays green — proven live.
- DiSmokeTest still does not resolve `ChatService`/`ProviderRouter` — correct per T12.md (deferred to T14).

## 4. Failure classification (CONTRACT-SERVICE-001) — FQNs verified against pinned Koog 1.3.0 jars

- All three FQNs exist and compile against 1.3.0: `ai.koog.agents.core.agent.exception.AIAgentException` (agents-core-jvm-1.3.0), `ai.koog.http.client.KoogHttpClientException` (http-client-core-jvm-1.3.0), `ai.koog.prompt.executor.clients.LLMClientException` (prompt-executor-clients-jvm-1.3.0). Tests construct all three with real constructors.
- `ExposedSQLException` → `ChatFailure(500, "Databases unavailable")`; the three provider/tool-loop types → `ChatFailure(502, "Provider unreachable")`; router 400 propagates as-is. Catch order is irrelevant (no hierarchy overlap between Exposed and Koog types).
- `AIAgentMaxNumberOfIterationsReachedException extends AIAgentException` (javap) → the "tool loop breaks" case maps to 502 as the spec intends.
- **No path emits unmasked text** (verified structurally): every service catch maps to a fixed constant; `ChatFailure` deliberately carries no cause; the route's last-resort `else` is the fixed `internal server error`; neither the service nor the route logs errors.

## 5. API contract

- No DTO changes in this task; `ChatRequest`/`ChatResponse` (T11) already match CONTRACT-API-001/002. `provider` echo comes from the resolved provider (always `"local"`/`"remote"`), never from raw input.
- Error envelope stays `{"error": "<masked message>"}`; statuses 400/500/502 per the contract table.

## 6. Tests (19 new, all green)

| Scaffold block3 case | Test |
|---|---|
| DialogHistory: append/recent order + roles | `recentReturnsAppendedMessagesInOrderWithTheirRoles` |
| Journal after one turn = 2 JSONL lines matching cache | `journalAfterOneTurnHoldsTwoJsonlLinesMatchingTheCache` |
| Truncate-on-restart | `aSecondConstructionTruncatesTheJournalAndClearsTheCache` |
| (extra) parent dirs created | `constructionCreatesTheJournalAndItsMissingParentDirectories` |
| recent caps at last N; default = historyLimit | `recentCapsAtTheLastMessagesUsingHistoryLimitByDefault` |
| Router: null → local | `absentProviderDefaultsToLocal` |
| Router: "local" → local | `explicitLocalResolvesToLocal` |
| Router: "remote" → remote | `remoteResolvesToRemote` |
| Router: other → masked 400 | `anyOtherValueIsRejectedWithAMasked400` (incl. `"LOCAL"`, `""`) |
| D-9 line present | `attachedFuelingIdIsInjectedIntoTheSystemPrompt` |
| D-9 line absent | `absentFuelingIdLeavesNoAttachedLineOnTheSystemPrompt` |
| History passed = last N with roles | `theClientReceivesTheLastCachedMessagesInOrderWithTheirRoles` |
| Routing default + explicit local | `absentOrLocalProviderRunsTheLocalClientAndEchoesLocal` |
| Routing remote | `remoteProviderRunsTheRemoteClientAndEchoesRemote` |
| Tool set exactly `[getFuelingInfoTool]`, never empty | `theClientReceivesExactlyTheFuelingInfoToolAndNeverAnEmptySet` |
| Journal after successful turn matches cache | `aSuccessfulTurnJournalsTheUserAndAssistantMessagesMatchingTheCache` |
| ExposedSQLException → 500 | `aDatabaseFailureIsMaskedAs500DatabasesUnavailable` |
| 3 provider/tool-loop types → 502 | `providerFailuresAreMaskedAs502ProviderUnreachable` |
| Router 400 before any client runs | `anUnknownProviderFailsWith400BeforeAnyClientRuns` |

MockK only; the real `GetFuelingInfoTool` is built with `ToolDescriptorLoader().load()` + `mockk<DbPools>()` (permitted by block3) and never executes a transaction; no DI graph resolution; no network; no DB. Assertions are live (captured arguments, exact masked strings, journal byte-count checks, leak sentinels incl. the raw provider value and Koog error bodies).

## 7. Judged edge cases (flagged by implementers)

| # | Severity | Location | Judgment |
|---|---|---|---|
| 1 | Info | ChatServiceImpl.kt:53-63 | **(a)** Koog `AgentRuntimeException` subtypes (`ToolNotRegisteredException`, `UnexpectedServerException`, `AgentEngineException`) and `ToolException` subtypes extend `RuntimeException`/`Exception` — not `AIAgentException` (javap-verified) — so they fall through to the route's generic masked 500 `internal server error`, not 502. The frozen task text enumerates exactly the four types implemented; type-based classification is what was asked. Messages stay masked fixed constants. Acceptable; a candidate tightening for T13/T14, not a frozen-text violation. |
| 2 | Info | ChatServiceImpl.kt:53-63 | **(b)** Wrapped DB failures: an `ExposedSQLException` wrapped by Koog (e.g. inside a `ToolException`/`AIAgentException`) bypasses the 500 catch and lands on generic masked 500 or 502. 500 is the correct status bucket per CONTRACT-API-002 either way; the body is always a fixed masked constant — no unmasked path exists (see §4). Acceptable per frozen text. |
| 3 | Info | ChatRoutes.kt:37 vs ProviderRouter.kt:21 | **(c)** Route 400 body (`provider must be "local" or "remote"`, T11) vs service 400 body (`Unknown provider`) differ. The route validates provider before the service is invoked, so the service 400 is defense-in-depth and unreachable via HTTP. The spec freezes only the `{"error": "<masked message>"}` envelope, not the message text; both are masked constants. Acceptable. |
| 4 | Info | DialogHistory.kt:27-31 | Cache is mutated before the journal write inside the mutex; a journal write failure (e.g. disk full) would diverge cache/journal and surface as generic masked 500 with a partial history entry. Essentially unreachable after a successful construction-time truncate on the same file; no frozen-text violation. Noted for completeness. |
| 5 | Info | ChatServiceImpl.kt:79-83 | User and assistant journal lines share one `System.currentTimeMillis()` timestamp. Cosmetic; append order and fields are what the contract freezes. |
| 6 | Info | DialogHistory.kt:36 | A configured `HISTORY_LIMIT <= 0` yields an always-empty `recent()` (`coerceAtLeast(0)`). Default is 20 (D-11); no validation required by the frozen text. |

## 8. Status sync

- `docs/tasks/INDEX.md` T12 → `in progress`, `docs/tasks/T12.md` → `in progress`. Correct until this verdict.

No correctness, contract, seam, DI, or test-coverage defects found.

VERDICT: PASS
