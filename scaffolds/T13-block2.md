# T13-block2

## Goal
MockEngine tests for `OllamaLlmClient` (no Ollama running): a plain-text reply yields the final text; a tool-call reply drives the agent loop through the fake tool and returns the final answer (proving the service-supplied tool set is actually wired into the run, D-8); the outgoing request asserts the model id and the tool schema.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- MockEngine-backed HTTP-client factory only — no network, no Ollama process, no DB, no service classes.
- This file is T13-owned; T14's test files must not modify it (T13 and T14 blocks may run in parallel).
- Runs after T13-block1. Requires the T13 integrator's build.gradle.kts edit (`io.ktor:ktor-client-mock-jvm`) before `./gradlew test` can run.
- No new dependencies without explicit permission.

## Files to create/modify
- `src/test/kotlin/com/fuelingchat/infra/llm/OllamaLlmClientTest.kt` — create:
  - plain-text reply → `run` returns the final text;
  - tool-call reply → the agent loop executes the fake tool (fixed text) and returns the final answer built on it — the tool set is non-empty by construction (D-8);
  - outgoing request assertions: the model id from `config.ollamaModel`, the tool schema (name `get_fueling_info`, `input_schema` with `fueling_id` required), and the endpoint base from `config.ollamaBaseUrl`.

## Public API
None — test-only block. Consumes T13-block1 (`KoogChatRunner`, `OllamaLlmClient`).

## Invariants
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).
