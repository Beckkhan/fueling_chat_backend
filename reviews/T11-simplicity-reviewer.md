# T11 — Simplicity Reviewer

**Verdict:** PASS
**Timestamp:** 2026-10-13T12:45:00Z

Review target: HEAD `1331d9a` on branch `first` — full task diff `d73bf57` (T11: Ktor Chat Route) + `1a3d476` (gate-3 handback fix: dead `import com.fuelingchat.service.ChatTurn` removed from `ChatRoutes.kt`) + `1331d9a` (task-file marker). Round-2 re-verification after the round-1 FAIL; this report supersedes the round-1 verdict file. Round-2 gates 1–2 PASS (`reviews/T11-test-engineer.md`, `reviews/T11-correctness-reviewer.md`). Working tree is clean for `src/` and `build.gradle.kts` (`git status --porcelain` shows only docs/metrics/reviews), so the reviewed code equals the committed code.

## Blocking finding from round 1 — resolved

1. **Dead import `ChatRoutes.kt:5` (`import com.fuelingchat.service.ChatTurn`) — FIXED.** At HEAD the imports are `ChatFailure` (line 3) and `ChatService` (line 4); no `ChatTurn` import remains. `git show 1a3d476` confirms the fix commit is exactly that one-line deletion, no other source change. Re-ran the repo-wide unused-import scan across all 42 Kotlin files (`src/**`, body-vs-import matching): **no unused imports found anywhere**, so the repo is back to a clean state for this criterion.

## Checks that passed (re-verified this round)

- **No dead code.** Repo-wide import scan clean (above). No unused functions/declarations in the T11 diff; the only additions are the import removal since round 1. `ChatFailure.DB_FAILURE` / `PROVIDER_FAILURE` remain referenced from `ChatRoutesTest` (lines 119, 134). `./gradlew build ktlintCheck detekt --rerun-tasks --no-build-cache` → **BUILD SUCCESSFUL** (16 tasks executed, tests included); `build/reports/detekt/detekt.txt` is 0 bytes and `detekt.md` reads `Findings (0)`; ktlint check reports empty (AC8).
- **No unnecessary interfaces/abstractions.** `ChatService`, `ChatTurn`, `ChatFailure` are mandated by AC5 and the CONTRACT-SERVICE-001-style seam the task declares; `ChatService` is one suspend entry point. `ErrorResponse` is the single contracted `{"error": ...}` body. `InvalidRequest` is a 3-line private control-flow exception, thrown twice and caught once — an idiom, not over-abstraction. `AppModule.deferredChatService()` is a 10-line lazy proxy, justified because the AC-mandated DI provider is `error("…")` while `boot()` needs an instance and `BootFailFastTest` boots the real server.
- **No duplication.** No duplicated logic across files; masking is not re-implemented (`SecretMasker` untouched, route emits constants or the service's already-masked `ChatFailure.maskedMessage`). The three identical `respond(BadRequest, ErrorResponse(INVALID_BODY_MESSAGE))` lines for three distinct Ktor exception types are mild repetition, readable as-is, not worth a helper. Test file uses `postChat` / `errorMessage` helpers to remove repetition.
- **No YAGNI.** DTO fields match CONTRACT-API-001/002 field-for-field with no extras (`ChatRequest{prompt, fueling_id?, provider?}`, `ChatResponse{summary, provider, elapsed_ms}`); statuses 400/500/502, the absent→`local` default (D-10) and wall-clock timing all trace to the contract and ACs. No streaming primitives (`grep -rE "text/event-stream|chunked|webSocket|EventSource" src/main/kotlin/com/fuelingchat/routes` → no matches, exit 1). `build.gradle.kts` adds only the AC-mandated `ktor-server-test-host-jvm` test dependency.
- **Fix introduced nothing new.** The only delta from the round-1-reviewed state is the import deletion plus the task-file marker; no new code paths, dependencies, or abstractions.

## Non-blocking notes (carry forward to T12)

1. `AppModule.kt:54-63` — `deferredChatService()` exists only because the T11 placeholder binding throws on resolution. Once T12 replaces `bind<ChatService>() with singleton { error(…) }`, delete the proxy (`configureChat(di.direct.instance())`). `AppModule.kt` is not in T12's Artifact list — carry this item explicitly or it will linger as dead indirection.
2. `ChatRoutes.kt:41` — `invalid.message ?: INVALID_BODY_MESSAGE`: the elvis branch is unreachable since `InvalidRequest` is only ever constructed with a non-null message. A non-null `val message: String` property would say the same without a dead fallback.
3. `ChatFailure.kt:8-9` — `DB_FAILURE` / `PROVIDER_FAILURE` are referenced only from `ChatRoutesTest`; T12's constructor calls should use them or they become dead declarations.

## Verdict

`T11 done | simplicity gate PASS` — the round-1 blocking finding (dead `ChatTurn` import) is resolved at HEAD, build/ktlint/detekt are green with 0 findings, and no new simplicity issues were introduced. The three notes above are non-blocking and belong to T12 or a future cleanup.
