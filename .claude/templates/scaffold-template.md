# Scaffold template — T<n>-block<id>

Use this template for every block scaffold. Replace all `<...>` placeholders. Copy exactly one invariants block (backend for backend blocks, frontend for frontend blocks) — never edit its contents.

## Goal
<What this block accomplishes, 1-3 sentences. Scope strictly to this block — nothing from other blocks.>

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- <Block-specific, e.g. "no business logic in routes", "infrastructure.llm never imports service classes", "no DI changes outside ClientModule">
- No new dependencies without explicit permission.

## Files to create/modify
- `<path/File.kt>` — <exact change: create or modify, and what goes in>

## Public API
<The seam this block exposes to other blocks: signatures, exports, DI keys. Must match the task contract field-for-field.>

## Invariants
Copy ONE of the two literal blocks below (backend or frontend) verbatim into this section.

### Backend invariants (literal — copy verbatim)
- `./gradlew ktlintCheck detekt test` must pass with 0 errors (detekt maxIssues: 0).
- Complexity caps (detekt.yml): LongMethod ≤ 40 lines, LongParameterList ≤ 5 params (functionThreshold), TooManyFunctions ≤ 15 per class, NestedBlockDepth ≤ 3.
- Style caps (detekt.yml): MaxLineLength ≤ 120, no wildcard imports.
- Masked errors: no raw SQL, credentials, or provider text in response bodies or logs (CONTRACT-API-001).
- No streaming/polling primitives (D-7): no `text/event-stream`, `chunked`, `webSocket`, `EventSource`.
- Tool set passed to `LlmClient.run` is never empty (D-8).
- Exposed queries: no N+1 (single joined/batched query per turn path).

### Frontend invariants (literal — copy verbatim)
- `pnpm build && pnpm lint && pnpm test` all exit 0.
- ESLint caps (eslint.config.js): max-lines 300, max-lines-per-function 40, max-depth 3.
- No default exports for components (final invariants §2.1).
- `src/App.tsx` holds no `fetch(` call — all requests go through `src/api/chat.ts`.
- Error bubbles render masked bodies; a non-JSON error body falls back to masked status text (CONTRACT-UI-002).
- No streaming/polling primitives (D-7): no `EventSource`, `WebSocket`, `setInterval`.
