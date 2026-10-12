# Task Index — docs/tasks/

Phase 3 of 6: DECOMPOSITION. Foundation backlog for `main`. Specification frozen: `docs/project-specification.md`; tasks reference contracts by ID only.

## Backlog

| ID | Title | Owner | Dependencies | Contracts | Status |
|---|---|---|---|---|---|
| [T01](T01.md) | Backend scaffold & DI wiring | `backend-coder` | — | CONTRACT-DI-001 | `todo` |
| [T02](T02.md) | Backend logging & fail-fast config | `backend-coder` | T01 | CONTRACT-LOG-001 | `todo` |
| [T03](T03.md) | Backend tool definition & Exposed DB setup (read-only, 4 pools) | `backend-coder` | T01, T02 | CONTRACT-TOOL-001, CONTRACT-DB-001 | `todo` |
| [T04](T04.md) | Frontend scaffold & UI parity setup | `frontend-coder` | — | CONTRACT-UI-001 | `todo` |
| [T05](T05.md) | CI & linting setup (both repos) | `git-reviewer / code-style-reviewer` | T01, T04 | — | `todo` |

## Conventions

- Every task is one file `T<n>.md` following the fixed template: Status / Owner / Dependencies / Contracts / Description / Acceptance Criteria / Artifact.
- Status lifecycle: `todo` → `in progress` → `done`; a task closes with the verdict `T<n> done | verified: ok` per the metrics protocol (spec §7).
- Contract IDs used by the backlog: CONTRACT-DI-001 (design §2 — Kodein module composition), CONTRACT-LOG-001 (design §2/§6 — masking, fail-fast), CONTRACT-DB-001 (spec §4 — read-only, 4 pools), CONTRACT-UI-001 (spec §1 + design §5 — Figma-export parity), plus the spec-defined CONTRACT-TOOL-001 (spec §3).
- Review gates per task, in order (design §8): correctness → simplicity → git → code-style; each reviewer writes `reviews/T<n>-<reviewer>.md`.
- Metrics (spec §7): the CTO appends one row per task to `metrics/usual.md` / `metrics/alternative.md` in both repos; findings are parsed from the review files, never hand-logged.
- Hard gate: task files only — no Kotlin/React/configuration implementation code until Phase 4.

## Not yet decomposed (planned)

The foundation backlog is defined through T05. Known upcoming work, to be decomposed before dispatch:

- **Backend:** `POST /chat` route (CONTRACT-API-001 / CONTRACT-API-002), `ChatService` + `ProviderRouter`, `DialogHistory` write-through journal (D-1, D-11), `GetFuelingInfoTool` handler + aggregation (CONTRACT-TOOL-001-OUT), Koog-backed `LlmClient` bindings `local`/`remote` (D-16), `README.md` per spec §8.
- **Frontend:** component decomposition of the Figma export (design §5 tree), chat state + `api/chat.ts` typed client, Vitest + RTL tests, `README.md` per spec §8.
- **Both repos:** `usual` / `alternative` implementation branches forked from the foundation commit (design §7), review gates per task, metrics ledgers.
