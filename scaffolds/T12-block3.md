# T12-block3

## Goal
Unit tests for `DialogHistory`, `ProviderRouter`, and `ChatServiceImpl` with a MockK fake `LlmClient` — no network, no DB: D-9 injection present/absent on the system prompt, history roles, journal file content, truncate-on-restart, provider routing incl. default and 400, and the 500/502 classification.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- No network, no DB: `DbPools` is MockK-mocked if a real `GetFuelingInfoTool` is constructed (`ToolDescriptorLoader().load()` reads only the classpath resource); no `transaction` ever runs.
- MockK fakes only — never real `LlmClient`s (both DI bindings are still `error()` stubs until T13/T14, so nothing resolves the app graph).
- ServiceModule.kt and DiSmokeTest.kt are NOT touched (DiSmokeTest extensions land in T14).
- Runs after T12-block1 and T12-block2.
- No new dependencies without explicit permission.

## Files to create/modify
- `src/test/kotlin/com/fuelingchat/service/DialogHistoryTest.kt` — create: append/recent order and roles; journal file after one simulated turn = two JSONL lines matching the cache; truncate-on-restart (a second construction on the same file → empty file + empty cache); `recent(limit)` caps at the last N with `historyLimit` as the default.
- `src/test/kotlin/com/fuelingchat/service/ProviderRouterTest.kt` — create: `null` → `local`; `"local"` → `local`; `"remote"` → `remote`; any other value → `ChatFailure(400, …)` with a masked message.
- `src/test/kotlin/com/fuelingchat/service/ChatServiceTest.kt` — create: MockK `LlmClient` fakes for local/remote, a real `GetFuelingInfoTool` (`ToolDescriptorLoader().load()` + `mockk<DbPools>()`), `DialogHistory` on a `@TempDir` file:
  - D-9: the captured system prompt contains the `attached fueling_id: <id>` line when `fuelingId` is present, and no such line when absent.
  - History passed to `LlmClient.run` = the last N cached messages with correct roles (pre-seed the cache via `append`).
  - Routing: `provider = null` → the local fake is called; `"remote"` → the remote fake is called; `ChatTurn.provider` echoes the resolved name.
  - The tool set passed is exactly `[getFuelingInfoTool]` — never empty (D-8).
  - Journal after one successful turn = user line + assistant line matching the cache.
  - Classification: fake throws `ExposedSQLException` (public constructor or `mockk`) → `ChatFailure(500, "Databases unavailable")`; fake throws the pinned Koog provider/tool-loop exception → `ChatFailure(502, "Provider unreachable")`; masked messages contain no raw internals.

## Public API
None — test-only block. Consumes T12-block1 (`DialogHistory` + `@Serializable ChatMessage`) and T12-block2 (`ProviderRouter`, `ChatServiceImpl`).

## Invariants
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).
