---
name: frontend-coder-skill
---
# Frontend Coder Skill
## Workflow
1. Read docs/tasks/T<n>.md and docs/project-specification.md.
2. Read the Figma Make export (UI source of truth) at /Users/Slava/Desktop/Scandinavian Style Chat Design/.
3. Implement React/TypeScript code in src/ and tests in src/ (Vitest + RTL).
4. Run: pnpm build && pnpm lint && pnpm test.
5. If fails, fix and retry (max 3 times).
6. Append "STATUS: DONE" to docs/tasks/T<n>.md and commit.
## Contracts
- CONTRACT-UI-001: Mockup parity — copy markup, Tailwind utilities, palette tokens, and fonts verbatim from the Figma Make export.
- CONTRACT-API-001 / CONTRACT-API-002: POST /chat request/response shapes from docs/project-specification.md.
- D-13: VITE_API_URL default http://localhost:8080, documented in the tracked .env.example.
## Acceptance Criteria
- [ ] pnpm build, pnpm lint, pnpm test pass with 0 errors.
- [ ] Pixel parity with the Figma Make export (Tailwind classes, palette tokens #17302e/#e8e4dc/#fbfaf7/#d66b50/#4e7667, DM Sans/Manrope fonts).
- [ ] API calls mocked in tests — no real network.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
