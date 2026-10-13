# T14 — Simplicity Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T18:22:20Z

## Scope

Commit `143066f` ("T14: Koog LlmClient 'remote' (DeepSeek)", 6 files, +341/−1) reviewed against `docs/tasks/T14.md` and CONTRACT-LLM-002 (spec §3 pinned `LlmClient` seam; D-8 tool-set contract; D-16 Koog-surface deviations), with gates 1–2 PASS (`reviews/T14-test-engineer.md`, `reviews/T14-correctness-reviewer.md`). Reviewed paths: `build.gradle.kts`, `docs/tasks/T14.md`, `ClientModule.kt`, `DeepSeekLlmClient.kt` (main) and `DiSmokeTest.kt`, `DeepSeekLlmClientTest.kt` (tests), cross-read against `OllamaLlmClient.kt`, `OllamaLlmClientTest.kt`, `KoogChatRunner.kt`, `LlmClient.kt`, `ServiceModule.kt`, `FakeEchoTool.kt`. Working tree matches the commit for all reviewed paths; only out-of-scope `metrics/cto-ledger.md` is modified and the stray `.env.example.swp` is untracked.

Context: architect-scaffold → implementer-fill pipeline; structure was pinned before implementation, so this gate judges what the implementer could change (YAGNI, duplication, over-abstraction, dead code), not the scaffolded shape.

## Simplicity checks performed

- **No unnecessary interfaces/abstractions.** The diff adds no new interface, no new seam, no new module. `DeepSeekLlmClient` implements the pinned `LlmClient`; `KoogChatRunner` (pre-existing, T13) is reused rather than wrapped or re-derived. `KoogHttpClient.Factory` injection is the same T13 seam, not a new one. `ClientModule` swaps one binding body; no new helper type. Nothing speculative was added.
- **No duplicated run logic (DRY, production).** `DeepSeekLlmClient.run` is a one-line delegation to the shared `KoogChatRunner`; prompt assembly, history mapping, tool-registry building and the agent loop live only in `KoogChatRunner`. The remaining constructor overlap with `OllamaLlmClient` is provider-specific wiring (executor construction, model id/capabilities) that cannot be factored further without inventing an abstraction; the two bodies differ in the parts that must differ (`addClient(DeepSeekLLMClient(...))` + capabilities list vs `.ollama(...)`). This is the correct amount of duplication.
- **No dead code.** Greps confirm: no `TODO`/`FIXME`/scaffold markers in `infra/llm` main or test; zero `error()` `LlmClient` stubs remain in `src/main` (the only hit is the stale comment, observation 1); `koogHttpClientFactory()` is used by both bindings; every import, companion constant and helper in `DeepSeekLlmClientTest.kt` is referenced; every import in `DeepSeekLlmClient.kt` is used. No unused-private-member finding survived detekt (gates 1–2 ran it fresh with 0 findings).
- **YAGNI.** Every element maps to an acceptance criterion: the client + both config keys (AC1), the KDoc deviation block and `build.gradle.kts` comment (AC2/D-16), the `remote` binding (AC3), the two `DiSmokeTest` assertions (AC4), the MockEngine tests' host/path/Bearer/model/tool-loop assertions (AC5). No extra features, no configuration surface, no convenience overloads, no logging/telemetry added. The four-point KDoc is mandated by the AC's deviation-documentation requirement and the capabilities inline comment is load-bearing (the list is mandatory for the OpenAI-compatible base client — verified by gate 2 against the jars).
- **Forced dependency, not a simplification miss.** `prompt-executor-deepseek-client-jvm:1.3.0-beta` is the only published DeepSeek module (verified against Maven Central by gate 2); it was added with a comment naming the D-16 deviation. There is no simpler alternative on the pinned surface.

## Pre-flagged candidates adjudicated

1. **Stale comment `ServiceModule.kt:21-23`** — confirmed false after this commit ("bindings stay error() stubs until T13/T14"; "DiSmokeTest additions … land in T14"): both bindings are real and the test addition landed. Dead documentation, comment-only, **outside the commit's diff** — the implementer-fill pipeline limits changes to scaffolded artifacts, so this is a carry-forward, not a missed in-diff cleanup. Non-blocking; delete/update in the next commit that touches `ServiceModule`.
2. **`DiSmokeTest` asserts non-null, not concrete types** — not a simplicity concern; asserting non-null is the simpler form and the AC's stub-detection purpose is served. No simplicity action; type-strength was already weighed by gate 1.
3. **Test-helper repetition between `OllamaLlmClientTest` and `DeepSeekLlmClientTest`** — real (~60 lines: `requestBodyText`, `assertOutgoingTurn`, near-identical `assertToolSchema`/`assertToolResultEchoedBack`, seven shared constants, the client-factory helper shape). **Accepted as-is:** (a) per-task test locality is a stated pipeline convention — each task's tests are reviewed self-contained; (b) the provider-specific parts (reply envelopes: Ollama native vs OpenAI-compatible, and the URL/header assertions) genuinely differ and would stay separate anyway; (c) the frozen backlog has exactly two providers (T13/T14, INDEX.md), so there is no rule-of-three trigger — extraction would create a shared test-support abstraction with a two-file lifespan and cross-test coupling for a marginal line saving. Revisit only if a third provider test ever appears.
4. **`koogHttpClientFactory()` "one-use" helper** — no longer one-use: both bindings call it, so the abstraction earns its keep. Promoting it to a Kodein singleton binding would add DI indirection without a need; the current private-function shape is the simplest correct form. The two-factory-instance resource question is lifecycle, already recorded by gate 2 (observation 5), not simplicity.
5. **`1.3.0-beta` dependency** — forced by publication reality and documented in place (see "Forced dependency" above). Not a simplicity violation.

## Observations (non-blocking)

1. **Comment drift, minor and cosmetic.** `ClientModule.kt:18-19` says the MockEngine-backed factory is injected "directly in OllamaLlmClientTest" — after T14 it is injected in `DeepSeekLlmClientTest` too; `FakeEchoTool.kt:13` says its schema is "asserted in OllamaLlmClientTest" — now also asserted in the DeepSeek test (as an OpenAI-compatible schema). One-line touch-ups; no behavioral impact. (Echo of the pre-existing T13 observation on `ClientModule.kt:18` — this commit makes the second half outdated.)
2. **Carry-forward cleanup queue.** `ServiceModule.kt:21-23` stale comment (above); grooming it in a follow-up commit (or T15's status commit touching this repo) keeps the trail honest. Consistent with gates 1–2, not a gate condition.

## Verdict

`T14-simplicity-reviewer: PASS | verified: ok`
