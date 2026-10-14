# T15-block1

**Repo:** `/Users/Slava/fueling_chat_front` (branch `final`). All paths below are frontend-repo-relative.

## Goal
Harden the real API client per CONTRACT-UI-002: a non-JSON error body never surfaces a raw `SyntaxError` — it falls back to the masked status text `request failed with status <n>`. JSON error bodies keep surfacing the masked `error` message.

## Boundaries
- Owned files: only the ones listed in "Files to create/modify". Never touch any other file.
- `sendChat` signature and the `ChatApiError` export stay unchanged (App.tsx already consumes them); the `VITE_API_URL` default (`http://localhost:8080`, D-13) logic stays unchanged.
- Only the error branch of `sendChat` changes; the 200 path and payload construction stay as-is.
- No App.tsx or component edits (App-level checks are integrator-step).
- No default exports; no `EventSource`/`WebSocket`/`setInterval`.
- No new dependencies without explicit permission.

## Files to create/modify
- `src/api/chat.ts` — modify: in the `!response.ok` branch, wrap the JSON parse in try/catch — if the parse succeeds, `throw new ChatApiError(body.error ?? fallback)`; if it fails (non-JSON body), `throw new ChatApiError(fallback)` where `fallback = \`request failed with status ${response.status}\``. The raw `SyntaxError` must never propagate to callers.

## Public API
Unchanged (must keep field-for-field compatibility):
```ts
export class ChatApiError extends Error {}
export async function sendChat(request: ChatRequest): Promise<ChatResponse>
```
- A non-200 outcome always rejects with a `ChatApiError` whose message is the masked `error` field or the masked status fallback.
- Integrator-step items (not this block): `grep -n "fetch(" src/App.tsx` returns nothing; `grep -rE "EventSource|WebSocket|setInterval" src` returns nothing; full `pnpm build && pnpm lint && pnpm test`.

## Invariants
- `pnpm build && pnpm lint && pnpm test` all exit 0.
- ESLint caps (eslint.config.js): max-lines 300, max-lines-per-function 40, max-depth 3.
- No default exports for components (final invariants §2.1).
- `src/App.tsx` holds no `fetch(` call — all requests go through `src/api/chat.ts`.
- Error bubbles render masked bodies; a non-JSON error body falls back to masked status text (CONTRACT-UI-002).
- No streaming/polling primitives (D-7): no `EventSource`, `WebSocket`, `setInterval`.
