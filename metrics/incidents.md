# Incidents — third branch, CTO run 2026-10-14 (T11–T15)

External-block log: intervals during the run when the pipeline was blocked on something outside agent work (operator decision waits, infrastructure incidents, environment failures — watchdog stalls, computer sleep, network issues, API balance errors). Normal agent execution, review-gate time, and handback rework cycles are in-pipeline outcomes and are NOT logged here.

| Task | Block reason | Blocked at | Resumed at | Duration |
|---|---|---|---|---|
| none — pipeline ran without external blocks | | | | |

TOTAL BLOCKED TIME = 0 min

---

## Wall-clock vs sum(duration_ms) note

On this branch agents run in parallel within a task (multiple implementers, five reviewers), so the sum of per-agent `duration_ms` in `metrics/cto-ledger.md` deliberately EXCEEDS the task wall-clock. For cross-branch comparison use task wall-clock (recorded in each task's closeout message), not the sum of `duration_ms`.

Per-task wall-clock as recorded in the CTO closeout messages of this run:

- T11 — ≈ 36 minutes (01:26 → 02:02 MSK)
- T12 — ≈ 48 minutes (02:13 → ~03:01 MSK)
- T13 — ≈ 44 minutes (02:56 → ~03:40 MSK)
- T14 — not recorded (closeout message did not state wall-clock)
- T15 — not recorded (closeout message did not state wall-clock)

(One environmental event occurred inside the T14 test gate — a spurious test-XML write failure caused by a concurrent `ktlintFormat` from a parallel gate. It was absorbed by the gate's own re-run on a quiet tree and blocked no pipeline interval, so it is not logged in the table above.)
