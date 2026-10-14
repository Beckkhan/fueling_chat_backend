# Comparison Report: first vs second vs third

Generated 2026-10-14 on main. Aggregates metrics/cto-ledger.md and metrics/incidents.md from three experiment branches (backend T11-T14, frontend T15). Branch semantics: first = single-agent (deepseek-v4-pro); second = sequential dual-coder (v4-pro architect + v4-flash implementer); third = parallel dual-coder (block decomposition, parallel implementers, integrator, parallel reviewers). All three branches executed the same T11-T15 backlog from the same foundation on main. A fourth branch, final, is planned but not yet present in this report.

## Executive Summary

- Winner by time: third (139.0 min vs next best second 172.1 min, 19.2% faster)
- Winner by cost: second ($0.0812 vs next best third $0.1035, 1.27x cheaper)
- Winner by quality: second (0 handbacks vs third 1 — third's single handback is the T11 code-style FAIL, resolved in-pipeline; first had 3)
- Overall recommendation: use third (parallel dual-coder) as the default pipeline for a multi-component backlog — 139.0 min wall-clock, 19.2% faster than second and 28.3% faster than first, at $0.1035 total cost (1.27x second's cost floor), with its single handback resolved in-pipeline. Fall back to second (sequential dual-coder) when first-pass quality (0 handbacks) or the cost floor dominates, and to first only for small, tightly-coupled tasks (it won T11 and T14 individually).

## Wall-clock Time Comparison

| Task | first | second | third | Fastest |
|---|---|---|---|---|
| T11 | 33.1 | 43.3 | 36.1 | first |
| T12 | 43.4 | 28.0 | 24.0 | third |
| T13 | 69.2 | 35.7 | 35.7 | tie (second/third) |
| T14 | 23.7 | 34.5 | 25.3 | first |
| T15 | 24.5 | 30.7 | 17.9 | third |
| Total | 193.8 | 172.1 | 139.0 | third |

Note: first and second ran agents sequentially — per-task value is sum of agent duration_ms. third ran agents in parallel within a task — per-task value is the task wall-clock recorded in third:metrics/incidents.md (reconciled from mark-done commit timestamps).

## Cost Comparison

| Task | first ($) | second ($) | third ($) | Cheapest |
|---|---|---|---|---|
| T11 | 1.0992 | 0.0160 | 0.0289 | second |
| T12 | 1.4269 | 0.0149 | 0.0196 | second |
| T13 | 2.4970 | 0.0182 | 0.0208 | second |
| T14 | 0.9964 | 0.0184 | 0.0194 | second |
| T15 | 0.5582 | 0.0137 | 0.0148 | second |
| Total | 6.5777 | 0.0812 | 0.1035 | second |

## Quality Comparison

| Task | first | second | third | Cleanest |
|---|---|---|---|---|
| T11 | 1 | 0 | 1 | second |
| T12 | 0 | 0 | 0 | tie |
| T13 | 1 | 0 | 0 | tie (second/third) |
| T14 | 0 | 0 | 0 | tie (first/second/third) |
| T15 | 1 | 0 | 0 | tie (second/third) |
| Total | 3 | 0 | 1 | second (0 handbacks; third 1 behind) |

Handback events:

- first T11 — simplicity-reviewer gate — dead import (`ChatTurn` at `ChatRoutes.kt:5` never referenced); task re-opened for a fix plus full re-verification.
- first T13 — simplicity-reviewer gate — dead import (`AppConfig` at `ClientModule.kt:5`), the sole leftover of the removed `error()` stub edit.
- first T15 — code-style-reviewer gate — `src/api/chat.test.ts` not Prettier-clean (two lines over `printWidth: 100`), and `pnpm lint` has no prettier check wired in.
- third T11 — code-style gate (CTO) — `chatRoutes` at 47 lines vs the spec §5 cap of 30 plus two over-long test methods; blocks B3 and B4 were re-spawned with the findings.

In-phase block corrections (not handbacks): on third T14, the B2-implementer caught a null-capabilities defect in B1's `DeepSeekLlmClient` before integration; B1 reworked its own block, and integration then passed first-try with all 5 quality gates green on round 1. This is a block-scoped correction, not a reviewer FAIL, so it does not count as a handback.

## Token Usage Comparison

| Metric | first | second | third |
|---|---|---|---|
| Total input tokens | 5,364,293 | 6,715 | 21,445 |
| Total cache_read tokens | 177,104,512 | 2,978,176 | 4,135,588 |
| Total output tokens | 828,784 | 36,283 | 64,583 |
| Sum of duration_ms (min) | 193.8 | 172.1 | 222.2 |

Note: on the parallel branch (third) the sum of duration_ms intentionally exceeds wall-clock — 222.2 min of agent time inside a 139.0-min task window (parallel overlap).

## Pipeline Comparison

| Branch | Coding model(s) | Coordination | Review phase | Typical use |
|---|---|---|---|---|
| first | deepseek-v4-pro (single agent) | sequential single-agent | 5 sequential gates | small tightly-coupled tasks |
| second | v4-pro architect + v4-flash implementer (sequential handoff) | sequential dual-coder | 5 sequential gates | medium tasks with clean structure |
| third | v4-pro architect + N parallel v4-flash implementers + v4-flash integrator | parallel fan-out per block | 5 parallel gates | larger multi-component tasks |

## Key Findings

1. Time leverage: third finished the backlog in 139.0 min — 19.2% faster than second (172.1 min) and 28.3% faster than first (193.8 min). Parallel reviewers account for the largest share of the gain: on second, the five sequential gates consumed 94.9 min of its 172.1-min total (55%), and third's parallel gates collapse that phase to the slowest reviewer per task. Parallel implementers contribute the remainder — third's fan-out absorbs 89.4 min of serial implementer-plus-integrator work into the wall-clock overlap — and third wins the two widest fan-out tasks outright (T12, T15) while tying second on T13.
2. Cost leverage: second is the cheapest branch at $0.0812 for the whole backlog. The parallel branch does pay a token overhead over its sequential counterpart: third totals $0.1035 (1.27x second) with 3.2x the input, 1.4x the cache_read and 1.8x the output tokens — the extra implementer rows, the integrator, and T11's rework round. Both dual-coder branches remain 63.6x and 81.0x cheaper than first ($6.5777), which bills v4-pro rates against million-token single-agent transcripts.
3. Quality: second had 0 handbacks across all 25 gates. first had 3 — T11 and T13 dead imports caught by the simplicity gate and T15 Prettier violations caught by the code-style gate, all review classes the linters do not cover, each requiring owner rework plus gate re-runs. third had 1 handback — a T11 code-style FAIL (function-length violations against the spec §5 caps, which the T11 scaffold did not encode). A T14 block rework is recorded in the ledger but is an in-phase correction, not a gate FAIL, and is documented separately.
4. Cache efficiency: first consumed 177.1M cache_read tokens vs 2.98M (second) and 4.14M (third) — 59.5x and 42.8x more. The mechanism is per-agent context size: first's single agent carries one large transcript through five sequential gates, so every gate re-reads the whole conversation (T13 alone reads 64.0M), while the dual-coder branches hand each agent a small scaffold context (~50-150k per agent), keeping transcripts short even with fan-out and rework rounds.
5. Parallelism caveat: parallel branches trade sequential convenience for wall-clock time — third needs block decomposition, a per-file ownership map, an integrator, and parallel-gate tree discipline (its one logged incident was a ktlintFormat XML write collision between two concurrent gates, absorbed inside a gate re-run). third also paid for an initial global-invariant gap: the spec §5 line caps were not encoded in the T11 scaffold, which produced its one documented handback (T11 code-style). The planned final branch is designed to carry global invariants into the scaffolds from the start.

## Recommendations

### Use first when

- The task is small and tightly coupled — first won T11 (33.1 min) and T14 (23.7 min) individually.
- The task is novel or high-uncertainty, so block decomposition cannot be predicted up front.
- Pipeline simplicity matters more than cost — one agent, one transcript, no coordination overhead.
- Cost is not a constraint — first is 63-81x more expensive than the dual-coder branches.

### Use second when

- First-pass quality is the priority — 0 handbacks across all 25 gates.
- The cost floor matters — $0.0812 for the whole T11-T15 backlog.
- The task is medium-sized with a clean structure a sequential architect can scaffold in one pass.
- Wall-clock is not the binding constraint — second is 19.2% slower than third.

### Use third when

- Wall-clock is the binding constraint — 139.0 min, 19.2% faster than second.
- Tasks are larger and multi-component, decomposing into independent blocks.
- Gate throughput matters — five reviewers run in parallel per task.
- A modest premium is acceptable — 1.27x second's cost and 1 handback (vs 0), resolved in-pipeline.

### General recommendation

Default to third (parallel dual-coder) for a multi-component backlog: 139.0 min total wall-clock (19.2% faster than second's 172.1 and 28.3% faster than first's 193.8) at $0.1035 total cost, only 1.27x the second-branch floor of $0.0812. Reserve second for backlogs where first-pass quality or the cost floor dominates, and first for small, tightly-coupled tasks.

## Methodology Notes

- Same T11-T15 backlog from the same main foundation on all three branches.
- Pricing per 1M tokens: deepseek-v4-pro input $0.50, cache_read $0.05, output $2.00; deepseek-v4-flash and deepseek-flash input $0.10, cache_read $0.01, output $0.40; cache_create billed at $0.
- Branch-specific agent configurations in .claude/agents/ (model frontmatter on every agent).
- Tokens and durations read from each branch's metrics/cto-ledger.md.
- Wall-clock for first and second is the sum of per-agent duration_ms (sequential pipelines). Wall-clock for third is the reconciled per-task value from third:metrics/incidents.md (mark-done commit timestamps).
- Handbacks counted from the task files' Handbacks sections and from (fix)/(r2)/rework/amend ledger rows that record a reviewer FAIL; in-phase rework rows without a gate FAIL (third's T14 B1 rework) are excluded.

## Data Quality Notes

- All token counts are exact (per-agent completion notifications).
- Wall-clock for third is exact at one-decimal granularity for T12-T15; T11 is approximate (session start timestamp).
- cache_create is 0 on all branches.
- Sum of duration_ms on the parallel branch (third) deliberately exceeds wall-clock (parallel overlap).
- third's T14 ledger row labeled (B1 rework) is an in-phase block correction, not a reviewer-FAIL handback; the task file carries no Handbacks section for T14.
- Branch final is not yet part of this report.
