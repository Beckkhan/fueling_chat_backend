VERDICT: PASS

# T14 — Test Engineer Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T15:12:11Z
**Reviewed commit:** `6aae421` (`T14: Koog LlmClient 'remote' (DeepSeek)`), diff vs parent `3c9f4c0`.
**Working tree:** clean for `src/` and `build.gradle.kts` (only `metrics/cto-ledger.md` modified), so reviewed code equals committed code.

## Files under review

- `src/main/kotlin/com/fuelingchat/infra/llm/DeepSeekLlmClient.kt` (new)
- `src/main/kotlin/com/fuelingchat/infra/llm/ClientModule.kt` (`remote` binding replaced the T12 stub)
- `build.gradle.kts` (adds `ai.koog:prompt-executor-deepseek-client-jvm:1.3.0-beta`)
- `src/test/kotlin/com/fuelingchat/infra/llm/DeepSeekLlmClientTest.kt` (new, 2 tests)
- `src/test/kotlin/com/fuelingchat/infra/llm/FakeEchoTool.kt` (extracted from `OllamaLlmClientTest`)
- `src/test/kotlin/com/fuelingchat/DiSmokeTest.kt` (extended with both named `LlmClient` bindings)

## Test evidence (live, this round)

- `./gradlew test --rerun --no-build-cache` — **EXIT=0**, 14 suites, **72 tests, 0 failures, 0 errors, 0 skipped** (fresh execution, not cache).
  - `DeepSeekLlmClientTest` 2/2 green (0.124s), `OllamaLlmClientTest` 2/2 green, `DiSmokeTest` 1/1 green, plus all pre-T14 suites (routes, service, tool, DB infra) still green.
- `./gradlew build ktlintCheck detekt` — **EXIT=0** (AC "0 errors").

## Acceptance-criteria coverage

- **AC1 (constructed from `AppConfig`, shared runner):** `DeepSeekLlmClient` reads `config.deepseekApiKey` / `config.deepseekModel` and delegates `run` to the shared `KoogChatRunner`; the runner is unchanged by this commit (no duplicated run logic). Test coverage is env-driven, not hardcoded: the temp env file sets `DEEPSEEK_MODEL=test-deepseek-model` and `DEEPSEEK_API_KEY=test-deepseek-key`, and the wire assertions check exactly those values, so the config wiring itself is under test.
- **AC "no `LlmClient` stub remains":** `ClientModule` binds tag `remote` to `DeepSeekLlmClient`; `DiSmokeTest` now resolves `LlmClient` tag `local` and `remote` through `AppModule`. Had the T12 `error(...)` stub survived, resolution would throw and the test would fail — the deferral from T12 is closed.
- **AC5 (MockEngine, no network):** verified below in "Mocking quality".
- **AC2 (Koog 1.3.0 surface / deviation documentation):** correctness-reviewer scope, but a test-relevant dependency check: the dependency report on `testRuntimeClasspath` shows **only** `prompt-executor-deepseek-client-jvm` at `1.3.0-beta`; every other `ai.koog` artifact (including `prompt-executor-openai-client-base`, on which the DeepSeek module depends) resolves to **1.3.0**. Tests exercise core Koog 1.3.0; the beta module is the DeepSeek transport only. The deviation is noted in a `build.gradle.kts` comment.

### MockEngine contract assertions (AC5), test by test

`plainTextReplyYieldsFinalText`:
- Request host `api.deepseek.com`, path `/chat/completions` (asserted from the captured `Url`).
- `Authorization: Bearer test-deepseek-key` (exact header equality).
- Body `model == "test-deepseek-model"`, `stream == false`, role sequence `system, user, assistant, user` with the prior history bodies and the current user message content checked.
- Tool schema: `tools[0].type == "function"`, `function.name == "fake_echo"`, `parameters.type == "object"`, `parameters.properties.echo_id.type == "string"`, `required == ["echo_id"]` — i.e. the D-8 tool-set contract is asserted on the request, not merely that `run` returns text.

`toolCallReplyDrivesAgentLoopAndReturnsFinalAnswer`:
- First reply is an OpenAI-style `tool_calls` response (`finish_reason: "tool_calls"`); second request is asserted to contain the `tool`-role message with `content == FakeEchoTool.FIXED_RESULT` and `tool_call_id == "call-test-1"`, and the final `run` result equals the second reply's text. Exactly 2 requests asserted.
- This proves the full loop for the remote provider: tool descriptor serialized out, model tool call parsed, the same `KoogChatRunner`/tool registry executed the tool, result echoed back, final answer returned — parity with T13's accepted `local` coverage.

## Isolation and determinism

- JUnit 5 default per-test instance + per-test `@TempDir`; each test builds its own `MockEngine`, `HttpClient`, and request-recording lists (`outgoing`, `urls`, `authHeaders`) that are method-local. No companion/static mutable state (only `const val`), no shared fixtures between tests, no order dependence.
- No sleeps, no clock/locale/randomness in the new tests; `System.nanoTime()` is used only to make temp env filenames unique (same pattern as the pre-existing Ollama test), never in an assertion.
- No test reaches a real endpoint: all traffic is served by `MockEngine` installed on the `HttpClient` handed to `DeepSeekLlmClient`'s factory. If the factory had bypassed the mock, the run would have failed fast (401 against `api.deepseek.com`); instead the suite completes in 0.124s with the asserted mock bodies.
- No real secrets: only `test-deepseek-key` / `test-db` literals written to temp files. No `.env` read, no production DB, no API keys.

## Mocking quality

- Where the `LlmClient` seam is the unit under test, it is mocked with MockK (`ChatServiceTest`, `ProviderRouterTest` — T12 scope, untouched and still green).
- The new DeepSeek tests mock at the HTTP transport (`MockEngine` injected through `KtorKoogHttpClient.Factory`), which is the only seam that can prove the wire-level contract AC5 demands (base URL, auth header, model id, tool JSON). This is the same pattern T13 was accepted with, and it keeps the real Koog DeepSeek client in the loop, so schema generation/serialization is exercised rather than stubbed away.

## Regressions / cross-checks

- `FakeEchoTool` extraction is a pure move: the class body is byte-identical, same package (`com.fuelingchat.infra.llm`), test source set only; the `OllamaLlmClientTest` diff removes only the class and its now-unused imports. `grep` confirms no references outside the `infra.llm` test package and none in `src/main`. Both T13 tests still pass, so the extraction did not break them.
- The only source change to the Ollama path is none (main diff touches `ClientModule`'s `remote` line, the new file, and `build.gradle.kts`).
- No test file depends on the real `data/dialog-history.jsonl` or the repository `.env`.

## Findings

None blocking. Non-blocking observations for downstream reviewers:

1. `DiSmokeTest` asserts non-nullity only, so it does not pin the concrete binding types (e.g. `remote` is a `DeepSeekLlmClient`); the AC is still met because the T12 stub would throw on resolution. Consider `is`-instance assertions on a future touch.
2. The DeepSeek test asserts host and path but not the scheme (`https`); Koog's default base is `https://api.deepseek.com` per D-16, so this is a minor tightening opportunity.
3. Tool-argument round-trip is verified implicitly (a broken `echo_id` deserialization would surface as a non-`FIXED_RESULT` tool message and fail the assert), not by asserting the tool received `{"echo_id":"F-1"}`. Same coverage level as T13; acceptable.
4. Bookkeeping: `docs/tasks/T14.md` keeps `**Status:** in progress` and unchecked AC boxes while appending `STATUS: DONE` — process note only, outside this gate.
