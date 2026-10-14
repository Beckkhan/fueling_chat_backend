# Incidents — final branch run

External-block log: intervals when pipeline was blocked on something outside agent work.

| Task | Block reason | Blocked at | Resumed at | Duration |
|---|---|---|---|---|

TOTAL BLOCKED TIME = 0 min

## Per-task wall-clock

| Task | Wall-clock (min) | Wave |
|---|---|---|
| T11 | 22 | Wave 1 |
| T12 | 23 | Wave 2 |
| T13 | 28 | Wave 3 |
| T14 | 30 | Wave 3 |
| T15 | 8 | Wave 2 |

## Wall-clock vs sum(duration_ms) note

On this branch agents run in parallel within waves and across tasks, so the sum of per-agent duration_ms in metrics/cto-ledger.md deliberately EXCEEDS the task wall-clock. For cross-branch comparison use task wall-clock from the table above, not the sum of duration_ms.
