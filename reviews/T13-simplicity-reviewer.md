# T13 — Simplicity Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T17:50:25Z

## Scope

Commit `1c678db` ("T13: Koog LlmClient 'local' (Ollama)", 10 files) reviewed against `docs/tasks/T13.md` and CONTRACT-LLM-001 (spec §3 pinned `LlmClient` seam + D-8, D-16; mapping in `docs/tasks/INDEX.md`). Gates 1–2 are PASS (`reviews/T13-test-engineer.md`, `reviews/T13-correctness-reviewer.md`). `git diff HEAD --stat -- src/ .env.example build.gradle.kts docs/tasks/T13.md` is empty — the reviewed tree is the commit; only out-of-scope `metrics/cto-ledger.md` is modified and the stray `.env.example.swp` is untracked. Build/test status is relied on from gates 1–2 (forced `./gradlew build ktlintCheck detekt`, 0 errors; empty detekt/ktlint reports), which ran on this identical tree; I additionally re-read the detekt/ktlint configuration to confirm which dead-code rules those runs actually enforce.

Branch context: architect-scaffold → implementer-fill pipeline, so class/function shape (`KoogChatRunner` + thin provider wrapper, `LlmClient` conformance, the injectable factory seam) was pinned before implementation and is judged as-is; findings below focus on what a body-fill could change.

## Verification performed

- Read the full diff of `1c678db`, the final content of all touched files, and the surrounding types (`LlmClient`, `ChatMessage`, `ChatService`, `ProviderRouter`, `ServiceModule`, `AppConfigTest`, `.env.example`).
- Manual import audit of all four Kotlin files (main and test): every import is referenced. Detekt runs with `buildUponDefaultConfig` + the project `detekt.yml` — in 1.23.8 `UnusedPrivateMember`/`UnusedParameter`/`UnusedPrivateProperty` are active by default while `UnusedImports` is off, but ktlint's standard `no-unused-imports` covers imports; both gates' forced re-runs were clean.
- Grepped `src/main` for the commit's symbols and literals (`KoogChatRunner`, `OllamaLlmClient`, `HttpClient(`, `Apache5`, `localhost:11434`, `ollama`): the only occurrences are the reviewed files plus `AppConfig` holding URL/model. No orphaned declaration, no second turn assembler, no duplicated HTTP-client factory or endpoint knowledge.
- Verified against the pinned Koog 1.3.0 sources jar (`agents-core-jvm-1.3.0-sources.jar`) that `AIAgentConfig`'s primary constructor has **no** default for `maxAgentIterations` (only the unrelated `withSystemPrompt` factory defaults to 3), so `MAX_AGENT_ITERATIONS = 50` is a required, deliberately named field — not an invented knob.
- Checked `FakeEchoArgs.echoId`: not read by `FakeEchoTool.execute()` (which returns the fixed result) but schema-load-bearing — it defines the `echo_id` string property asserted on the wire in `OllamaLlmClientTest`; not dead.
- Re-checked the T12 simplicity recommendation (single source for provider names / "Invalid provider"): untouched by this commit, and `ChatRoutes.kt`/`ProviderRouter.kt` are outside T13's artifact list — it stays open for T14/T15, not a T13 matter.

## Findings (non-blocking)

1. **Plain-reply test re-serializes a parsed object.** `OllamaLlmClientTest` calls `assertOutgoingTurn(turn.toString(), ...)` on a `JsonObject` it just parsed, and the helper parses it again; the sibling helper takes raw body text, so the `String` signature keeps the two uniform. Trivial cost in test code (one re-parse per assertion); judged acceptable, no change requested.
2. **`KoogChatRunner : LlmClient` is not consumed polymorphically today.** `OllamaLlmClient` holds the runner as a concrete type, so the conformance is not required for the delegation to compile. It adds no member and no indirection — it aligns the shared runner with the pinned seam and with the "T14 stays thin" contract. Keep.
3. **`koogHttpClientFactory()` is used once today.** A two-line named factory with a comment is neutral for readability, and T14's DeepSeek binding is the plausible second consumer; if T14 instead needs a different engine, folding it back into the `local` binding is a two-line change. Nothing to do now.

## Judged non-issues (prior gates' candidates)

- **`else -> Unit` in `appendHistoryMessage`.** `ChatMessage.role` is a `String`, so Kotlin requires an `else` branch; the alternatives (throw/require) would add an unreachable failure mode for a role `DialogHistory` never emits. One line, the simplest legal form — not a YAGNI or dead-code violation.
- **`AppConfigTest` `/v1` fixtures (lines 82/101).** In `allKeysAreReadFromEnvFile` the input value is asserted back literally — the test proves pass-through of an arbitrary value, and `.../v1` is a legitimate such value; the default is separately asserted with the corrected value at line 59. Deliberately minimal diff, self-consistent.
- **`ClientModule` Apache5 comment.** For simplicity the comment earns its two lines by preventing a spurious production dependency addition; the naming imprecision (arrives via `koog-agents-jvm`, not `http-client-ktor`) is a factual nit already in gate 2's trail, with no simplicity action.
- **Test `HttpClient` instances not closed.** `MockEngine` holds no OS resources; closing would add teardown boilerplate with no benefit. Standard Ktor test pattern.
- **`remote` `error()` stub retained.** T14's binding; removing it now would break the eager `ProviderRouter` resolution path's intended compile-time shape without simplifying anything. Not dead code introduced by this commit.

## YAGNI / duplication / over-abstraction / dead-code summary

- **YAGNI:** every added declaration maps to an AC line (client class + injectable factory, shared turn assembly, `local` binding, corrected default, `.env.example` line, one test-only dependency, MockEngine tests). No speculative API, no unused parameter, no configuration knob beyond the required `maxAgentIterations`.
- **Duplication:** none. The turn assembly exists exactly once (`KoogChatRunner`); `ChatService` reaches it only through the pinned seam; the tool set stays single-sourced in `ChatService.toolSet()` per D-8; endpoint/model knowledge lives only in `AppConfig`. Test helpers (`plainTextReply`, `toolCallReply`, `appConfig`, assertion helpers) are local to one class and have no pre-existing equivalent to share; `AppConfigTest.envFile` vs `OllamaLlmClientTest.appConfig()` overlap nominally (both write a temp `.env`) but merging them would add shared-fixture infrastructure, not remove duplication.
- **Over-abstraction:** none introduced within implementer scope. The two-layer `OllamaLlmClient` → `KoogChatRunner` shape and the `: LlmClient` conformance are the task-pinned structure ("keep the turn assembly reusable so T14's DeepSeek client stays thin"); the wrapper carries only construction plus one-line delegation.
- **Dead code:** none. No unused imports, private members, helpers, constants or tests; no leftover scaffold `TODO`/`FIXME`; the only non-obvious unread value (`FakeEchoArgs.echoId`) is schema-load-bearing and asserted on the wire.

## Verdict

`T13-simplicity-reviewer: PASS | verified: ok` — no blocking findings; findings 1–3 are noted only for the trail, and the T12 provider-name de-duplication remains open for T14/T15.
