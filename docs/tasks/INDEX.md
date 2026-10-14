# Task Index — docs/tasks/

Phase 3 of 6: DECOMPOSITION. Foundation backlog for `main` — 10 atomic tasks (T01–T10, all `done`) plus the `usual`-branch feature backlog (T11–T15, decomposed in this phase; all `todo`, implementation not started), each small enough to be implemented, reviewed (4 gates, design §8) and merged independently in one commit. Specification frozen: `docs/project-specification.md`; tasks reference contracts by ID only.

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
| [T11](T11.md) | Ktor Chat Route | `backend-coder` | — | CONTRACT-API-001 | `done` |
| [T12](T12.md) | ChatService & DialogHistory | `backend-coder` | T11 | CONTRACT-SERVICE-001, CONTRACT-HISTORY-001 | `done` |
| [T13](T13.md) | Koog LlmClient 'local' (Ollama) | `backend-coder` | T12 | CONTRACT-LLM-001 | `done` |
| [T14](T14.md) | Koog LlmClient 'remote' (DeepSeek) | `backend-coder` | T12 | CONTRACT-LLM-002 | `done` |
| [T15](T15.md) | Frontend API Integration | `frontend-coder` | T11 | CONTRACT-API-001, CONTRACT-UI-002 | `done` |

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
| CONTRACT-API-001 | spec §2 — `POST /chat` strict request/response: request fields and rules, 200 = exactly `{summary, provider, elapsed_ms}`, masked error table (400/500/502), no streaming (D-7) |
| CONTRACT-SERVICE-001 | spec §3 pinned `LlmClient` seam + spec §2 error table + D-6, D-8, D-9, D-10 — orchestration and failure classification of one chat turn |
| CONTRACT-HISTORY-001 | D-1, D-11 — write-through `.jsonl` journal + in-memory cache as the only read source; both cleared on restart |
| CONTRACT-LLM-001 | spec §3 pinned `LlmClient` seam + D-8, D-16 — the `local` (Ollama) binding; tools passed in from the service, never empty |
| CONTRACT-LLM-002 | spec §3 pinned `LlmClient` seam + D-8, D-16 — the `remote` (DeepSeek) binding; same tool-set contract as `local` |
| CONTRACT-UI-002 | spec §2 (request/error bodies) + design §5 (muted error bubble) + D-13 — frontend integration behavior on `usual` |

`CONTRACT-SERVICE-001`, `CONTRACT-HISTORY-001`, `CONTRACT-LLM-001`, `CONTRACT-LLM-002` and `CONTRACT-UI-002` are introduced by this decomposition as task-level IDs mapping onto the frozen spec content above; `docs/project-specification.md` itself is unchanged. For the `usual`-branch tasks, `CONTRACT-API-001` covers the whole endpoint (request **and** response of spec §2).

- **`usual`-branch execution order** (dependency order): `T11 → T12 → T13 → T14 → T15`. T13 and T14 both depend only on T12 and are independent of each other. T15 depends on T11 and can start once the route lands, but end-to-end behavior needs T12–T14, so it is dispatched last in practice.
- **Seam note:** T11 lands first and therefore declares the `ChatService` seam it consumes (interface + `ChatTurn`/`ChatFailure` types + a placeholder DI binding); T12 replaces the placeholder. The `LlmClient` named bindings remain `error()` placeholders from the foundation until T13/T14 — the app boots and tests run without resolving them.
- **Koog surface:** T13/T14 verify the exact Koog 1.3.0 client surface against the pinned jars at implementation time (D-16); any wording deviation from D-16 is documented in the correctness review.
- Tasks land in dependency order, one commit each, merged independently. Backend implementation commits are `T<n>: <title>` in this repo; T15's implementation + reviews commit lands in the frontend repo, followed by a `T15: mark … done` status commit here.
- Review gates per task, in order (design §8): correctness → simplicity → git → code-style; each reviewer writes `reviews/T<n>-<reviewer>.md` in the repo that owns the task's code (T11–T14 backend, T15 frontend).
- Metrics (spec §7): the CTO appends one row per task to `metrics/usual.md` / `metrics/alternative.md` in both repos; findings are parsed from the review files, never hand-logged.
- Hard gate: `usual`-branch scope is the single-agent implementation only — no `alternative`-branch or dual-coder work.

## Not yet decomposed (planned)

Remaining for later phases of the `usual` branch:

- **Backend:** `README.md` refresh per spec §8 (created in the foundation, refreshed at the end of the implementation branch).
- **Frontend:** `README.md` refresh per spec §8 (same rule).
- **Both repos:** the `alternative` branch implementation (design §7), metrics ledgers (`metrics/usual.md` / `metrics/alternative.md` per repo), the measurement phase and `comparison-report.md` (backend repo root, spec §7).
