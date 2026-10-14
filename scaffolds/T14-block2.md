# T14-block2

## Goal
DeepSeek MockEngine test (no network) plus the deferred `DiSmokeTest` extension: both named `LlmClient` bindings resolve through `AppModule`.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- MockEngine-backed factory only — no network, no DeepSeek account, no DB, no service classes.
- `DiSmokeTest.kt` extension is additive: the existing foundation assertions stay untouched; only the two `LlmClient` resolution assertions are added.
- Sequencing: the `local` resolution assertion requires the T13 integrator's `ClientModule.kt` edit to have landed, the `remote` one requires the T14 integrator's edit — coordinate before running the full suite.
- Runs after T14-block1. T13-owned files are read-only here.
- No new dependencies without explicit permission.

## Files to create/modify
- `src/test/kotlin/com/fuelingchat/infra/llm/DeepSeekLlmClientTest.kt` — create:
  - the outgoing request targets the DeepSeek API base (`https://api.deepseek.com`) with Bearer `api_key` auth (`config.deepseekApiKey`) and the configured model id;
  - a tool-call reply drives the same fake-tool loop as T13's test — the tool-set contract holds for the remote provider (D-8).
- `src/test/kotlin/com/fuelingchat/DiSmokeTest.kt` — modify: extend `graphResolvesAllFoundationBindings` (or add a sibling test in the same file) with `assertNotNull(di.direct.instance<LlmClient>(tag = "local"))` and `assertNotNull(di.direct.instance<LlmClient>(tag = "remote"))` — deferred from T12. Keep the existing assertions intact.

## Public API
None — test-only block. Consumes T13-block1's `KoogChatRunner` (via T14-block1) and the DI bindings landed by the T13/T14 integrators.

## Invariants
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).
