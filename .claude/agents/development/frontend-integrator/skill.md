# Frontend Integrator — condensed workflow (third branch)

1. Read scaffold OWNERSHIP map + cross-block interfaces + all blocks filled by frontend-implementers.
2. Wire shared files only: App.tsx composition, api client exports, type re-exports.
3. Resolve cross-block type errors.
4. Run pnpm build && pnpm lint && pnpm test.
5. Max 3 retries on integration-level failures; never rewrite block internals.
6. Commit in /Users/Slava/fueling_chat_front: "T<n>: integrate <title>"; append STATUS: INTEGRATED to docs/tasks/T<n>.md in the backend repo.
