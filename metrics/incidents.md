# Incidents — third branch, CTO run 2026-10-14 (T11–T15)

External-block log: intervals during the run when the pipeline was blocked on something outside agent work (operator decision waits, infrastructure incidents, environment failures — watchdog stalls, computer sleep, network issues, API balance errors, ktlintFormat XML write collision). Normal agent execution, review-gate time, and handback rework cycles are in-pipeline outcomes and are NOT logged here.

| Task | Block reason | Blocked at | Resumed at | Duration |
|---|---|---|---|---|
| T14 | ktlintFormat XML write collision: a concurrent `ktlintFormat --rerun-tasks` from a parallel quality gate caused a spurious test-XML write failure in the test-engineer gate's first forced test run; the gate re-ran on a quiet tree and passed | ≈ 03:23 MSK (approx) | ≈ 03:24 MSK (approx) | ≈ 1 min |

Footnote: the collision is placed in T14 per this session's transcript (reported by the T14 test-engineer gate). It idled no pipeline interval — the lost time was absorbed by the gate's own re-run, and T14's five gates still finished within the task window.

TOTAL BLOCKED TIME = ≈ 1 min (absorbed inside a gate's own re-run; the pipeline itself never idled)

---

## Wall-clock vs sum(duration_ms) note

On this branch agents run in parallel within a task (multiple implementers, five reviewers), so the sum of per-agent `duration_ms` in `metrics/cto-ledger.md` deliberately EXCEEDS the task wall-clock. For cross-branch comparison use task wall-clock, not the sum of `duration_ms`.

Authoritative per-task wall-clock (reconciled in this wrap-up):

T11: 36.1 min; T12: 24.0 min; T13: 35.7 min; T14: 25.3 min; T15: 17.9 min

Method: End = mark-done commit timestamp (git `%ci`: b7889fb 02:02:07, c6f34f1 02:26:07, 9950619 03:01:49, c91ccd9 03:27:08, fb6b586 03:45:03 MSK). Start = the previous task's mark-done commit timestamp (each task's Phase 0 architect was spawned immediately after), except T11, whose start (≈ 01:26 MSK) is approximate — the session's first concrete transcript timestamp is a Phase-1 file edit at 01:29 MSK.

Source adjudication: the closeout messages for T12 ("≈ 48 min", 02:13 → ~03:01) and T13 ("≈ 44 min", 02:56 → ~03:40) are SUPERSEDED — their stated ranges contradict the git-anchored commit times (e.g., T12's mark-done at 02:26 is well before the closeout's claimed 03:01 end, and T13's integrator commit at 02:55 predates the closeout's claimed 02:56 start). The final summary table (~36/~35/~24/~23/~19) is also superseded by the git-anchored figures above. T14/T15 had no closeout wall-clock (marked "not recorded" in the previous wrap-up) and are now supplied from the same git-anchored method.
