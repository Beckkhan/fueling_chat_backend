---
name: frontend-implementer-skill
---
# Frontend Implementer Skill (Second Branch Only)
## Workflow
1. Read docs/tasks/T<n>.md, the scaffold in /Users/Slava/fueling_chat_front/src/, and docs/project-specification.md.
2. Fill TODO markers with real React components, hooks, and API calls. Write tests with Vitest + React Testing Library.
3. Run: pnpm test && pnpm lint && pnpm build.
4. If fails, fix and retry (max 3 times).
5. Append "STATUS: DONE" to docs/tasks/T<n>.md and commit.
## Contracts
- CONTRACT-API-001: send flow payloads carry the exact request fields; the provider switcher sends the active model id as `provider`.
- CONTRACT-API-002: 200 renders exactly {summary, provider, elapsed_ms}; non-200 renders masked error bodies.
- CONTRACT-UI-002: muted error bubble per design §5.
## Acceptance Criteria
- [ ] pnpm test && pnpm lint && pnpm build pass.
- [ ] TODO markers filled; structure/types unchanged from scaffold.
- [ ] No mock fetch left in App.tsx (T15); API calls go through src/api/.
- [ ] Mockup parity preserved.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
