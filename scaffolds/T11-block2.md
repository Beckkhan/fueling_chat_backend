# T11-block2

## Goal
Implement `POST /chat` on the `usual` branch per CONTRACT-API-001: kotlinx-serialization DTOs matching the contract field-for-field, validation (blank prompt → 400, `provider` not in {local, remote} → 400, malformed JSON → 400 masked), `elapsed_ms` measured route entry → response ready, masked error bodies, and no streaming (D-7). The route holds no business logic (design §2): it delegates to the T11-block1 `ChatService` seam.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- No business logic in the route: no provider routing beyond validation, no history, no tools — everything goes through `ChatService.runTurn`.
- The route is exposed as a `Route` extension taking a lazy `() -> ChatService` provider — this block never edits AppModule.kt (integrator step).
- No streaming primitives anywhere in the route package: no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Runs after T11-block1 (imports its seam).
- No new dependencies without explicit permission (content-negotiation + kotlinx-json are already in build.gradle.kts).

## Files to create/modify
- `src/main/kotlin/com/fuelingchat/routes/ChatRequest.kt` — create: `@Serializable data class ChatRequest(val prompt: String, @SerialName("fueling_id") val fuelingId: String? = null, val provider: String? = null)` — field-for-field with CONTRACT-API-001.
- `src/main/kotlin/com/fuelingchat/routes/ChatResponse.kt` — create: `@Serializable data class ChatResponse(val summary: String, val provider: String, @SerialName("elapsed_ms") val elapsedMs: Int)` (exactly three fields, G-6) and `@Serializable data class ErrorBody(val error: String)`.
- `src/main/kotlin/com/fuelingchat/routes/ChatRoutes.kt` — create: `fun Route.chatRoutes(chatService: () -> ChatService): Route` per the Public API below.

## Public API
```kotlin
package com.fuelingchat.routes

fun Route.chatRoutes(chatService: () -> ChatService): Route
```
- Registers `POST /chat`; each request calls `chatService()` lazily, so the T11 placeholder in DI is never resolved at install time (keeps `BootFailFastTest.serverStartsAndAnswersHttp` green until T12).
- Behavior (CONTRACT-API-001 / CONTRACT-API-002):
  - 200 body is exactly `{summary, provider, elapsed_ms}`: `summary`/`provider` from the returned `ChatTurn`; `elapsed_ms` = integer ≥ 0, server wall-clock route entry → response ready (the route's concern — T12 must not duplicate it).
  - 400 `{"error": "<masked message>"}` for: blank/whitespace-only `prompt`; `provider` present but not `"local"`/`"remote"`; malformed/unparseable JSON. Malformed JSON is masked via StatusPages handling of Ktor's bad-request/content-transformation/serialization exceptions (exact exception types verified at implementation time) — never echo parse internals.
  - Absent `provider` is passed to the service as `null` (the D-10 default is the service's concern).
  - `ChatFailure` from the service maps to `failure.status` with `{"error": failure.maskedMessage}` (500 = four-DB failure class, 502 = provider/tool-loop failure class; 400 passes through).
  - Any other exception → generic masked 500 (`{"error": "Internal error"}`); no SQL, credentials, or provider text ever in a body.
- Integrator-step items (not this block): AppModule.kt — install `ContentNegotiation { json() }` and `routing { chatRoutes { di.direct.instance<ChatService>() } }` inside `boot()`'s `embeddedServer` lambda, without changing the boot fail-fast checks; build.gradle.kts — `testImplementation("io.ktor:ktor-server-test-host-jvm:3.3.3")`.

## Invariants
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).
