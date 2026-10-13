VERDICT: PASS

# T14 — Simplicity Review

**Verdict:** PASS (one non-blocking duplication finding, test-only, carried forward from T13)
**Timestamp:** 2026-10-13T15:20:54Z
**Target:** commit `6aae421` (`T14: Koog LlmClient 'remote' (DeepSeek)`), diff vs parent `3c9f4c0`.
**Working tree:** `git diff 6aae421 --stat -- src build.gradle.kts` is empty (only `metrics/cto-ledger.md` modified, two untracked review files), so the reviewed code equals the committed code.
**Input gates:** `reviews/T14-test-engineer.md` — PASS; `reviews/T14-correctness-reviewer.md` — PASS (both present, this round).

## Files under review

- `src/main/kotlin/com/fuelingchat/infra/llm/DeepSeekLlmClient.kt` (new, 41 lines)
- `src/main/kotlin/com/fuelingchat/infra/llm/ClientModule.kt` (1 line: `remote` stub → `DeepSeekLlmClient`)
- `build.gradle.kts` (1 dependency + 1 deviation comment)
- `src/test/kotlin/com/fuelingchat/infra/llm/DeepSeekLlmClientTest.kt` (new, 270 lines)
- `src/test/kotlin/com/fuelingchat/infra/llm/FakeEchoTool.kt` (new, extracted), `OllamaLlmClientTest.kt` (extraction side)
- `src/test/kotlin/com/fuelingchat/DiSmokeTest.kt` (2 assertions + import)

## Caller's specific concerns

### 1. `DeepSeekLlmClient` delegates to `KoogChatRunner` with no duplicated run logic — VERIFIED

- `DeepSeekLlmClient.run` is a verbatim `: String = runner.run(systemPrompt, history, userMessage, tools)` passthrough of the pinned seam signature; the turn assembly (`turnConfig`, history mapping, `ToolRegistryBuilder`, `maxAgentIterations`) exists only in `KoogChatRunner` and that file is not in the diff. The MockEngine tool-loop test proves the shared runner executes the loop for the remote transport (2 requests, tool result echoed back).
- No duplicated run logic, no second prompt-builder, no second tool registry.

### 2. `FakeEchoTool` extraction is proportionate — ACCEPTED

- 28-line test fixture with two consumers; the extracted block is byte-identical to the removed block (`git diff` shows a pure move), it sits in the same package/source set, and `OllamaLlmClientTest`'s diff removes exactly the class plus its 5 now-unused imports (`SimpleTool`, `LLMDescription`, `typeToken`, `SerialName`, `Serializable`). Proportionate and correctly scoped.

### 3. D-16 deviation workarounds are minimal, not speculative — ACCEPTED

- **`DeepSeekClientSettings`:** not referenced at all in `src/` (grep: 0 hits). The code takes Koog's default settings and passes only `apiKey` + `httpClientFactory`. No override, no configurable base-URL/path knob — the minimum surface; the exact version pin and the wire test hold the default in place.
- **Capability declarations:** exactly `listOf(LLMCapability.Completion, LLMCapability.Tools)` — the two capabilities the OpenAI-compatible path requires. No preset-style superset (no `Schema.JSON`, `Temperature`, `Thinking`), no configurability.
- **`PromptExecutor.builder().addClient(...).build()`:** the sanctioned path for a provider without a builder convenience method; no wrapper, factory or registry abstraction introduced.
- **`build.gradle.kts`:** one AC-named dependency plus a one-line comment explaining the beta pin. No dependency added beyond AC2.
- `AppConfig.deepseekApiKey`/`deepseekModel` are pre-existing T12 fields (unchanged by this diff); no new config surface.

### 4. Dead code / unused imports / unused parameters — CLEAN

- The removed `remote` `error(...)` stub left nothing: `grep -rn 'error('` over `src/main/kotlin` and `src/test/kotlin` → no matches; no `"lands with T14"` text anywhere in `src/` or `docs/`; no `TODO`/`FIXME` anywhere in `src/`.
- Mechanical import-usage scan (each import's simple name counted outside import lines) over all six changed files: **0 dead imports**. `DeepSeekLlmClient` uses every import it declares.
- All constructor/function parameters are used (`config`, `httpClientFactory`; test helpers `requestBody`, `expectedRoles`, `handler`, `text`). No unused parameter anywhere in the diff.
- No orphaned references: `DeepSeekLlmClient` is referenced only by `ClientModule` (plus its own test); `FakeEchoTool`/`FakeEchoArgs` only by the two test classes.
- No new abstraction: no interface, no factory type, no strategy/registry added by T14. The only new type is the concrete `DeepSeekLlmClient` implementing the pinned seam.
- YAGNI against the ACs: every production change traces to an AC bullet (client + binding + dependency). Nothing speculative for a future task.

## Findings

1. **Test-helper duplication carried forward, still present (non-blocking).** `DeepSeekLlmClientTest` duplicates three helpers from `OllamaLlmClientTest` byte-for-byte (verified by `diff`):
   - `assertOutgoingTurn` — 31 lines identical,
   - `assertToolSchema` — 19 lines identical (this is the D-8 tool-set wire assertion: `type == "function"`, `name`, `parameters.type`, `properties.echo_id.type`, `required == ["echo_id"]`),
   - `requestBodyText` — 2 lines identical,

   plus the MockEngine factory scaffolding (10 lines) differs only in the final `return DeepSeekLlmClient(appConfig(), factory)` line. Total ~52 lines reproduced verbatim.

   T13's simplicity review listed exactly these helpers (with `FakeEchoTool`) under "extract at T14 when there is something to share"; T14 extracted only `FakeEchoTool`. Extraction remains straightforward and low-risk: the duplicated helpers are provider-agnostic — they read only the request JSON and the companion constants `TEST_MODEL`/`SYSTEM_PROMPT` — so a shared top-level helper/object in the `infra.llm` test package (next to `FakeEchoTool.kt`) taking the expected model id as a parameter would remove the copies; only `TEST_MODEL` actually differs per class.

   Non-blocking for consistency with the established bar: the same class of finding was recorded non-blocking at T12 (byte-identical `testDi` fixture across `ChatServiceTest`/`ProviderRouterTest`, T12 finding 6) and was explicitly "not charged" at T13; it is test-only, behavior-neutral code, and both copies currently assert the same D-8 contract. Recommended at the next touch of these fixtures, not demanded as rework.

   Deliberately excluded from the recommendation: `assertToolResultEchoedBack` is **not** duplicated — DeepSeek's copy adds the provider-specific `tool_call_id` assertion (OpenAI wire shape) that Ollama's lacks. Keeping that helper per-class is correct, not a DRY violation.

2. **Thin passthrough duplication in production (non-blocking, accepted).** `OllamaLlmClient` and `DeepSeekLlmClient` share the `private val runner` property and the identical 6-line `run` override; T14 introduced the second copy. Accepted as-is: the provider-specific parts (executor construction, model, capabilities) are what differ, and removing the 5-line passthrough would require either making the T12/T13-pinned `KoogChatRunner` `open` (touching a frozen seam) or inserting a third wrapper type for two providers — both worse than the duplication. The delegation contract the AC asks for ("same run contract as T13") holds.

3. **Observation, pre-existing, not charged to this diff.** `ClientModule` now calls `koogHttpClientFactory()` once per binding, so a second Apache5 `HttpClient` is constructed and never closed. The helper itself predates T14 and the second call is the unavoidable consequence of the second named binding; already recorded by the T13 and T14 correctness reviews. A single shared `val` factory would remove the duplicate construction if the module is ever revisited.

4. **Bookkeeping note, outside this gate.** `docs/tasks/T14.md` flips `Status` to `in progress` and appends `STATUS: DONE` while the AC boxes stay unchecked — process, not simplicity (also raised by the test-engineer).

## Verdict line

T14 done | verified: ok — `DeepSeekLlmClient` delegates to the shared `KoogChatRunner` with no duplicated run logic; the `FakeEchoTool` extraction is a proportionate pure move; the four D-16 workarounds are minimal (default settings, two required capabilities, sanctioned `addClient` path, one pinned dependency); no dead code, dead imports, unused parameters or new abstraction; the removed `remote` stub left no residue. The only finding is the test-helper duplication (52 byte-identical lines) explicitly deferred by the T13 gate and only partly absorbed here — recorded non-blocking at the T12/T13 precedent, to be taken at the next touch of these fixtures.
