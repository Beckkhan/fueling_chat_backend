# Incidents — `first` branch run

Intervals during this run (T12–T15, 2026-10-13) when the pipeline was blocked on something outside agent work: operator decision waits, infrastructure incidents, environment failures. Normal agent execution and review-gate time are excluded.

| Task | Block reason | Blocked at | Resumed at | Duration |
|---|---|---|---|---|
| none — pipeline ran without external blocks | — | — | — | 0 |

Notes:
- All wait intervals were subagent execution time (owner agents implementing, quality gates reviewing) — excluded per definition.
- ES-001 (Koog tool-loop exceptions below the seam) was escalated to the Specification team mid-run but did not block the pipeline: T14 and T15 proceeded against their literal acceptance criteria; the escalation remains open in `docs/escalations/ES-001.md`.
- The two handbacks (T13 dead import, T15 Prettier) were review-gate outcomes handled inside the pipeline — not external blocks.

TOTAL BLOCKED TIME = 0
