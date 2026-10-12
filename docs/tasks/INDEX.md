# Task Index — docs/tasks/

Phase 3 of 6: DECOMPOSITION. Foundation backlog for `main` — 10 atomic tasks, each small enough to be implemented, reviewed (4 gates, design §8) and merged independently in one commit. Specification frozen: `docs/project-specification.md`; tasks reference contracts by ID only.

## Backlog

| ID | Title | Owner | Dependencies | Contracts | Status |
|---|---|---|---|---|---|
| [T01](T01.md) | Backend build config & linters | `backend-coder` | — | — | `done` |
| [T02](T02.md) | Typed AppConfig & fail-fast validation | `backend-coder` | T01 | CONTRACT-CONFIG-001 | `done` |
| [T03](T03.md) | Logging infrastructure | `backend-coder` | T01 | CONTRACT-LOG-001 | `done` |
| [T04](T04.md) | DI wiring | `backend-coder` | T02, T03, T05, T06 | CONTRACT-DI-001 | `done` |
| [T05](T05.md) | Database infrastructure | `backend-coder` | T02 | CONTRACT-DB-001 | `done` |
| [T06](T06.md) | Tool definition & handler | `backend-coder` | T05 | CONTRACT-TOOL-001 | `done` |
| [T07](T07.md) | Frontend build config & linters | `frontend-coder` | — | — | `done` |
| [T08](T08.md) | UI components & state | `frontend-coder` | T07 | CONTRACT-UI-001 | `done` |
| [T09](T09.md) | API client & integration | `frontend-coder` | T08 | CONTRACT-API-001, CONTRACT-API-002 | `done` |
| [T10](T10.md) | CI workflows | `git-reviewer` | T01, T07 | — | `done` |

## Conventions

- Every task is one file `T<n>.md` following the fixed template: Status / Owner / Dependencies / Contracts / Description / Acceptance Criteria / Artifact. Acceptance criteria are strictly verifiable (an exit code, a diff, a grep, an observable crash).
- Status lifecycle: `todo` → `in progress` → `done`; a task closes with the verdict `T<n> done | verified: ok` per the metrics protocol (spec §7).
- Contract IDs and where they are frozen:

| Contract | Source |
|---|---|
| CONTRACT-CONFIG-001 | design §2 (config layer) + §6 (fail-fast at startup); keys in the tracked backend `.env.example` |
| CONTRACT-LOG-001 | design §2 (`infra.logging` mask-before-write) + §3 (credentials and secrets never in logs) |
| CONTRACT-DI-001 | design §2 (Kodein module table, `AppModule` composition, `Application` holds no logic) |
| CONTRACT-DB-001 | spec §4 (read-only guarantees, structure-only Exposed objects) + design §6 (Docker, D-2) |
| CONTRACT-TOOL-001 | spec §3 (descriptor JSON; handler output per CONTRACT-TOOL-001-OUT, query chain per spec §4) |
| CONTRACT-UI-001 | spec §1 (G-1 UI source of truth) + design §5 (mockup parity, component tree) |
| CONTRACT-API-001 / CONTRACT-API-002 | spec §2 (`POST /chat` request / response) |

- Tasks land in dependency order, one commit each, merged independently: backend `T01 → T02 → T05 → T06 → T04` (T03 in parallel with T02); frontend `T07 → T08 → T09`; T10 after T01 and T07.
- Review gates per task, in order (design §8): correctness → simplicity → git → code-style; each reviewer writes `reviews/T<n>-<reviewer>.md`.
- Metrics (spec §7): the CTO appends one row per task to `metrics/usual.md` / `metrics/alternative.md` in both repos; findings are parsed from the review files, never hand-logged.
- Hard gate: task files only — no Kotlin/React/configuration implementation code until Phase 4.

## Not yet decomposed (planned)

The foundation backlog is defined through T10. Known upcoming work, to be decomposed before dispatch:

- **Backend:** `POST /chat` route (CONTRACT-API-001 / CONTRACT-API-002), `ChatService` + `ProviderRouter`, `DialogHistory` write-through journal (D-1, D-11), Koog-backed `LlmClient` named bindings `local`/`remote` (D-16), `README.md` per spec §8.
- **Frontend:** `README.md` per spec §8.
- **Both repos:** `usual` / `alternative` implementation branches forked from the foundation commit (design §7), metrics ledgers, per-task review gates.
