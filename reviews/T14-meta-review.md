# T14 Meta-Review — Koog LlmClient 'remote' (DeepSeek)

**Commit:** `0f3898a639f8e63a1212ffa54f3f1f3a22aa380d` ("T14: Koog LlmClient 'remote' (DeepSeek)")
**Branch:** `final` — reviewed at commit state; working tree carries only unrelated T13-wave edits (T13.md, metrics, incidents) and does not touch T14 files.
**Method:** single-pass meta-review: invariants + logic + API contract + tests. All D-16 claims independently re-verified against the pinned Koog 1.3.0 jars/sources in the Gradle cache (javap + extracted sources), not taken on trust.

## 1. Invariants

| Invariant | Status | Evidence |
|---|---|---|
| `LlmClient.run` seam identical to T13 (pinned §3 signature, unchanged) | OK | `DeepSeekLlmClient.kt:57-65` overrides the exact 4-param `run`; `LlmClient.kt` untouched by the commit |
| Turn assembly delegated to T13's `KoogChatRunner`, no duplicated run logic | OK | `DeepSeekLlmClient.kt:35-46` builds `KoogChatRunner(DeepSeekLLMClient(...), LLModel(...))` and delegates; `KoogChatRunner.kt` not in the diff |
| `infrastructure.llm` business-free | OK | imports only Koog types + `ChatMessage` + `AppConfig` — no service classes |
| D-8: tool set never empty | OK | test passes a one-tool list; service assembles non-empty per T12; `assertToolSetCarriesTheFakeTool` asserts non-empty on the wire (`DeepSeekLlmClientTest.kt:79`) |
| D-7: no streaming/polling primitives | OK | precise grep for `\.sse(`, `event-stream`, `chunked`, `webSocket`, `EventSource` over both new files: clean. Non-streaming `getResponse` path used (capability check below proves it) |
| CONTRACT-API-001 masked errors | OK | no logger/println in the class; no provider text echoed |
| Complexity/style caps (detekt.yml: LongMethod 40, LongParameterList 5, TooManyFunctions 15, NestedBlockDepth 3, MaxLineLength 120, maxIssues 0) | OK | `detekt` + `ktlintCheck` green; no line >120 chars in either new file |
| Exposed N+1 | N/A | no DB code in T14 |
| `KoogChatRunner` / `OllamaLlmClient` / `LlmClient` / `ChatMessage` untouched | OK | none appear in the commit diff |

## 2. D-16 deviations — verified real and sound

Each claimed deviation re-verified directly against the pinned artifacts (javap on `prompt-executor-deepseek-client-jvm-1.3.0-beta.jar`; sources from the `-sources` jars):

**(a) No `baseUrl` constructor parameter — DEVIATION CONFIRMED.**
javap on `DeepSeekLLMClient` shows the constructors `(DeepSeekClientSettings, KoogHttpClient, ...)`, `(String, DeepSeekClientSettings, KoogHttpClient.Factory, ...)` and overloads — no constructor accepts a plain base-URL string. D-16's `DeepSeekLLMClient(apiKey, baseUrl = …)` wording does not exist in the pinned jars. The implementation correctly pins `DeepSeekClientSettings(baseUrl = "https://api.deepseek.com")` (`DeepSeekLlmClient.kt:39`), and the Koog source default for `DeepSeekClientSettings.baseUrl` is exactly that same URL — consistent with D-16's intent. The deviation is documented in the class KDoc (`DeepSeekLlmClient.kt:16-19`), satisfying the "document wording deviations in the correctness review" criterion.

**(b) Capabilities Completion+Tools mandatory — VERIFIED at source level.**
`AbstractOpenAILLMClient.getResponse` (the non-streaming path the agent's `execute` uses) calls `model.requireCapability(LLMCapability.Completion)` unconditionally (source line 244) and `model.requireCapability(LLMCapability.Tools)` when `tools.isNotEmpty()` (line 246); `requireCapability` throws `IllegalArgumentException` via Kotlin `require` (line 516-519). `LLModel.supports(capability)` returns `capabilities?.contains(capability) ?: false` — so an unset capability list fails every turn. Both `LLMCapability.Completion` and `LLMCapability.Tools` exist in the enum. Declaring both on the `LLModel` (`DeepSeekLlmClient.kt:43-46`) is therefore required, not optional — the implementer's empirical claim upgrades to source-verified fact.

**(c) Artifact `prompt-executor-deepseek-client-jvm:1.3.0-beta` — VERIFIED.**
The `koog-agents-jvm:1.3.0` POM (cache) lists `prompt-executor-openai-client-jvm` and `prompt-executor-openai-client-base-jvm` but NOT the DeepSeek client — it is not pulled transitively, so the explicit `build.gradle.kts:31` line is necessary. Gradle's module metadata for the artifact lists only `1.1.1-beta`, `1.2.0-beta`, `1.3.0-beta` — no plain `1.3.0` exists; `1.3.0-beta` resolved and compiled.

**(d) `httpClientFactory` has no default — VERIFIED.**
The secondary constructor source is `(apiKey, settings = DeepSeekClientSettings(), httpClientFactory: KoogHttpClient.Factory, clock, toolsConverter)` — the factory has no default value. The injectable `KoogHttpClient.Factory` seam is identical to T13's `OllamaLlmClient`, and `ClientModule.kt` passes `KtorKoogHttpClient.Factory()` for both tags. The `Authorization: Bearer <key>` header is added by `createConfiguredHttpClient` in `AbstractOpenAILLMClient` (source line 135), matching the test's wire assertion.

## 3. Logic / API contract

- Wire surface: single POST to `<base>/chat/completions`, Bearer auth, OpenAI-compatible tool-call loop — all asserted at the wire level by the MockEngine test and consistent with the Koog source (`chatCompletionsPath = "chat/completions"`).
- DI (`ClientModule.kt:10-11`): `local` binding untouched; `remote` bound to `DeepSeekLlmClient(instance(), KtorKoogHttpClient.Factory())`. Grep confirms no `LlmClient` stub/`error()` placeholder remains anywhere in `src/`.
- D-10 routing: `ChatServiceImpl` resolves `remote` via `ProviderRouter.REMOTE`; binding tag matches.
- `AppConfig.deepseekApiKey` / `deepseekModel` pre-existed (required env values `DEEPSEEK_API_KEY`, `DEEPSEEK_MODEL`); not changed by this commit.
- Error handling: no local exception mangling — errors surface to the T12 masking layer, consistent with the seam contract.

## 4. Tests

- `DeepSeekLlmClientTest` — exactly 2 cases, `MockEngine` only (no network, no DB, no service classes):
  1. `theOutgoingRequestTargetsTheDeepSeekApiBaseWithBearerAuthAndTheConfiguredModelId` — asserts `https`, host `api.deepseek.com`, path `/chat/completions`, `Authorization: Bearer <key>`, body `model` id; single round-trip (`requests.single()`).
  2. `aToolCallReplyDrivesTheFakeToolLoopAndTheServiceSuppliedToolSetReachesTheProvider` — tool-call reply triggers exactly one more round-trip (D-8), tool set present in request 1 with the fake tool name, request 2 carries the tool message with matching `tool_call_id` and the fake tool's fixed output; final text returned.
  - The MockEngine handler serves `replies[minOf(size-1, lastIndex)]`; an unexpected third request would still fail the count assertions — the loop is bounded in the test as in the runner.
- Credentials synthetic only: `test-deepseek-key`, `test-model`, env written to a `@TempDir` file; the Bearer assertion compares against the synthetic key.
- `DiSmokeTest.graphResolvesAllFoundationBindings` extended additively: `LlmClient` tags `local` and `remote` both resolve through `AppModule`; all pre-existing foundation assertions intact; env credentials synthetic.

## 5. Build/verification runs (performed by reviewer)

- `./gradlew --offline ktlintCheck detekt test --rerun` — GREEN; test XML totals **75 tests, 0 failures, 0 errors** across 14 classes.
- `./gradlew --offline build` — BUILD SUCCESSFUL.
- Note (environmental, not a finding): the very first combined `--rerun` invocation failed with "Could not write XML test results" for 7 classes (0-byte XMLs). It did not reproduce in two subsequent runs (test alone, and the combined command again); permissions are owner-writable. Treated as a transient daemon/filesystem race artifact, not a code defect.

## 6. Status sync

- `docs/tasks/INDEX.md` — T14 `in progress`. Correct until verdict.
- `docs/tasks/T14.md` — `in progress`. Correct until verdict.
- (INDEX.md's "Seam note" about `error()` placeholders is a historical plan note; both bindings are now real, so it is satisfied, not violated.)

## Findings

No blocking or major findings. Minor observations (non-blocking):

1. `DeepSeekLlmClientTest.kt:61` — `assertEquals(config.deepseekModel, model)` duplicates line 60 (`MODEL_ID == config.deepseekModel` by construction). Harmless belt-and-braces; could be dropped.
2. `DeepSeekLlmClient.kt:16-19` KDoc says the capabilities requirement was "empirically proven by implementer"; this review now confirms it at source level, so the wording could be strengthened, but the claim itself is accurate.

## Verdict

VERDICT: PASS
