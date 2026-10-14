# T15-block2

**Repo:** `/Users/Slava/fueling_chat_front` (branch `final`). All paths below are frontend-repo-relative.

## Goal
Create `src/api/chat.test.ts`: assert both payload shapes (`{prompt, provider}` exactly, plus `fueling_id` when present), the `VITE_API_URL` base (D-13 default `http://localhost:8080`), the masked JSON error-body mapping, and the non-JSON error-body fallback (never a raw `SyntaxError`).

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- Tests stub `fetch` via `vi.stubGlobal`/`vi.unstubAllGlobals` — no real network.
- No App.tsx or component edits (the existing App send-flow tests already stay green; App-level checks are integrator-step).
- Runs after T15-block1 (tests its hardened client).
- No default exports; no `EventSource`/`WebSocket`/`setInterval`.
- No new dependencies without explicit permission.

## Files to create/modify
- `src/api/chat.test.ts` — create, cases:
  1. POST `http://localhost:8080/chat` (VITE_API_URL default, D-13) with `Content-Type: application/json` and a body that deep-equals exactly `{prompt, provider}` (two keys via `JSON.parse(options.body)`).
  2. When `fueling_id` is present in the `ChatRequest`, the body includes it: `{prompt, provider, fueling_id}`.
  3. Non-200 JSON body `{error: "Provider unreachable"}` → rejects with a `ChatApiError` carrying exactly that masked message.
  4. Non-JSON error body (stubbed `json()` rejects with a `SyntaxError`) → rejects with `ChatApiError("request failed with status <n>")` — the rejection is never a raw `SyntaxError`.

## Public API
None — test-only block. Consumes T15-block1's `sendChat`/`ChatApiError` from `src/api/chat.ts` and the `ChatRequest`/`ChatResponse` types in `src/types.ts` (read-only).

## Invariants
- `pnpm build && pnpm lint && pnpm test` all exit 0.
- ESLint caps (eslint.config.js): max-lines 300, max-lines-per-function 40, max-depth 3.
- No default exports for components (final invariants §2.1).
- `src/App.tsx` holds no `fetch(` call — all requests go through `src/api/chat.ts`.
- Error bubbles render masked bodies; a non-JSON error body falls back to masked status text (CONTRACT-UI-002).
- No streaming/polling primitives (D-7): no `EventSource`, `WebSocket`, `setInterval`.
