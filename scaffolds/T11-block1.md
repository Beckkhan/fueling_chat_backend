# T11-block1

## Goal
Declare the service seam the route consumes: the `ChatService` interface with its single suspend entry point, the contract result/error types (`ChatTurn`, `ChatFailure`), and the placeholder DI binding in `ServiceModule.kt` that T12 replaces. This block lands first so later blocks compile against the seam.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- No business logic in the seam: one suspend entry point per turn, nothing beyond signatures; the DI binding is an `error("…")` placeholder that throws only when resolved.
- Do not touch routes, AppModule.kt, build.gradle.kts, ClientModule.kt, or any existing service/tool files. The existing `GetFuelingInfoTool` binding in ServiceModule.kt stays exactly as it is.
- The placeholder must not break app boot: `BootFailFastTest` and `DiSmokeTest` must keep passing without resolving `ChatService`.
- No new dependencies without explicit permission.

## Files to create/modify
- `src/main/kotlin/com/fuelingchat/service/ChatService.kt` — create: `interface ChatService` per the Public API below.
- `src/main/kotlin/com/fuelingchat/service/ChatTurn.kt` — create: `data class ChatTurn` per the Public API below.
- `src/main/kotlin/com/fuelingchat/service/ChatFailure.kt` — create: `class ChatFailure` per the Public API below.
- `src/main/kotlin/com/fuelingchat/service/ServiceModule.kt` — modify: add `bind<ChatService>() with singleton { error("ChatService lands with T12") }`; keep the existing `GetFuelingInfoTool` binding unchanged.

## Public API
```kotlin
package com.fuelingchat.service

// ChatService.kt — the seam; T12's implementation replaces the DI placeholder (integrator step).
interface ChatService {
    suspend fun runTurn(prompt: String, fuelingId: String?, provider: String?): ChatTurn
}

// ChatTurn.kt
data class ChatTurn(val summary: String, val provider: String)

// ChatFailure.kt
class ChatFailure(val status: Int, val maskedMessage: String) : Exception(maskedMessage)
```
- `provider` may be `null` (the route leaves the D-10 default to the service; invalid non-null values are already rejected by the route with 400).
- DI key: `bind<ChatService>()` (no tag). The placeholder is lazy — resolved only on first `instance()`.
- Integrator-step items (not this block): AppModule.kt route install, build.gradle.kts test-host dependency.

## Invariants
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).
