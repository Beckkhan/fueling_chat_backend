---
name: frontend-architect-skill
---
# Frontend Architect Skill
## Workflow
1. Read docs/tasks/T<n>.md and docs/project-specification.md.
2. Write scaffold in src/ of /Users/Slava/fueling_chat_front: file layout (components/, hooks/, api/, types/), component structure and props interfaces, state-management approach, API client types from the backend contracts, mock data structure, and TODO comments. No implementation logic.
3. Run: pnpm build && pnpm lint (TODOs are valid TypeScript comments; the scaffold must compile and lint clean).
4. If fails, fix and retry (max 3 times).
5. Append "STATUS: SCAFFOLD_DONE" to docs/tasks/T<n>.md and commit.
## Contracts
- CONTRACT-API-001 / CONTRACT-API-002: API client types and calls must match the POST /chat request/response shapes.
- CONTRACT-UI-002: error rendering per spec §2 (masked bodies, muted error bubble).
- D-4: toolchain pinned — Vite 8, React 19, TS 5.7 strict, Tailwind v4, pnpm; never change package.json.
## Acceptance Criteria
- [ ] pnpm build && pnpm lint pass.
- [ ] Only structure and TODO comments — no real implementations.
- [ ] TODO comments are specific (what each component/hook/function must do).
- [ ] Mockup parity: Figma export classes/tokens/fonts carried into the scaffold.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
