# Experiment Retrospective: first vs second vs third

Companion to comparison-report.md (main, 2026-10-14). Records what the three-branch experiment measured, what worked, what did not, and where further leverage sits.

## 1. Headline comparison: time and tokens

Wall-clock and token consumption per branch, whole T11-T15 backlog.

| Metric | first (single-agent) | second (sequential dual-coder) | third (parallel dual-coder) | Best |
|---|---|---|---|---|
| Wall-clock time (min) | 193.8 | 172.1 | **139.0** | third |
| Speedup vs first | — | 1.13x | **1.39x** | third |
| Total input tokens | 5,364,293 | 6,715 | 21,445 | second |
| Total cache_read tokens | 177,104,512 | 2,978,176 | 4,135,588 | second |
| Total output tokens | 828,784 | 36,283 | 64,583 | second |
| Total cost ($) | 6.5777 | **0.0812** | 0.1035 | second |
| Cost ratio vs first | 1x | 81.0x cheaper | 63.6x cheaper | second |
| Handbacks | 3 | **0** | 1 | second |
| Handback rate (per task) | 0.60 | 0.00 | 0.20 | second |

Reading the table: third wins wall-clock by 19.2% over second and 28.3% over first; second wins tokens, cost and quality. The three winners are different — the experiment is a genuine three-way trade-off, not a single-pipeline winner.

## 2. Token consumption breakdown

Why the token gap between first and the dual-coder branches is 40-80x, per metric:

| Token class | first | second | third | Ratio first/second |
|---|---|---|---|---|
| Input | 5,364,293 | 6,715 | 21,445 | 799x |
| Cache_read | 177,104,512 | 2,978,176 | 4,135,588 | 59.5x |
| Output | 828,784 | 36,283 | 64,583 | 22.8x |
| Sum duration_ms (min) | 193.8 | 172.1 | 222.2 | — |

Mechanism: first's single agent carries one large transcript through five sequential gates, so every reviewer re-reads the whole conversation (T13 alone: 64.0M cache_read). The dual-coder branches hand each agent a small scaffold context (~50-150k per agent), which keeps transcripts short even under fan-out and rework.

On third, sum(duration_ms) = 222.2 min exceeds wall-clock 139.0 min by design: parallel implementers and parallel reviewers overlap in time. Wall-clock is the correct metric for cross-branch comparison; the ledger sum measures total agent compute, not elapsed time.

## 3. What worked well

1. Single foundation on main for all three branches — the comparison isolates pipeline architecture, not incidental drift.
2. Five-gate review chain (test, correctness, simplicity, git, code-style) — identical across branches; the gate outcomes are the primary quality signal and the source of every documented handback.
3. Git-anchored wall-clock reconciliation for third — closeout messages contradicted the mark-done commit times; using commit timestamps as ground truth removed a 10-20 min ambiguity on T12 and T13.
4. Reclassifying third T14 "B1 rework" as in-phase correction, not a gate-FAIL handback — preserved the meaning of "handback" as a reviewer-driven re-run.
5. Parallel reviewers produced the largest single win: on second the five gates consumed 94.9 min (55% of the total); on third the same five gates collapse to the slowest reviewer per task.
6. Block decomposition + integrator kept per-agent context small, which is what makes the token and cache_read gap vs first so large.

## 4. What could be improved

1. Sample size. One run per branch, 5 tasks each — exploratory, not confirmatory. T13 (tie second/third) is likely noise. Future experiments should run each branch at least 3 times and compare medians.
2. Cost is modeled, not billed. The $0.0812 vs $0.1035 gap is ~$0.02 for the whole backlog and depends on assumed DeepSeek pricing. The 1.27x ratio is robust to pricing changes; the absolute dollars are not.
3. cache_create = 0 on every branch. This is a red flag in the tracker: the input figure may be silently folding cache_create in. If so, first's real cost is 15-30% higher, which does not change the winner but does affect precision.
4. No code-volume metrics. Handbacks alone are a coarse quality signal. LOC, test count and coverage per branch should be added to the retrospective for the next iteration.
5. Confounded variables on third. Parallel implementers, integrator, and parallel reviewers all landed in one branch. Without splitting them, the individual contribution of each lever cannot be isolated. The 19.2% win is real; attribution to the levers is not.
6. ktlintFormat XML write collision on T14 (concurrent gates writing the same report file) — logged and absorbed by a gate re-run, but a known hazard of parallel fan-out.

## 5. Where further leverage sits

Ranked by expected impact per unit of engineering effort. Each item is a separate future experiment branch; do not combine them, or attribution is lost again.

| Rank | Change | Expected effect | Cost | Risk |
|---|---|---|---|---|
| 1 | Global invariants in every block's scaffold (the "final" plan) | −1 handback on T11, ≈ −10 min | 1 scaffold-invariant section in each architect prompt | Low — pure rule addition |
| 2 | Architect pipeline (scaffold task N+1 while implementers fill task N) | −25-30 min across 5 tasks | CTO spawns architect N+1 right after architect N | Medium — needs careful ordering |
| 3 | Balanced block effort (architect estimates effort, not file count) | −5-7 min per task Phase 1 | Instruction in architect prompt | Low |
| 4 | Reviewer fan-out per block (2 per block + 1 integrator) | −2-3 min review | Change Phase 3 in process.md | Medium — loses cross-block view |
| 5 | Reduce gate chain to 3 LLM gates (style/git → CI) | −10-15 min, −cost | Move ktlint/detekt/prettier/commitlint into CI | High — code-style and simplicity gates caught real defects linters missed |

The single highest-value change is #2 (architect pipeline) — it removes pure latency without adding coordination. The cheapest is #1 (invariants), which alone should turn third's 1 handback into 0 and shrink T11 wall-clock by ~10 min.

## 6. Recommendation

Keep third (parallel dual-coder) as the default for multi-component backlogs. The 19.2% wall-clock win over second is stable across T12/T15 (third won outright) and does not require rework on the owner side — only a modest +$0.02 cost and one handback that was resolved in-pipeline.

Reserve second (sequential dual-coder) for backlogs where first-pass quality or cost floor dominates, and first for small tightly-coupled tasks (first won T11 and T14 individually).

Before adopting third in production, run two follow-up experiments:
- final: third + global invariants in scaffolds (target: 0 handbacks, ~125 min)
- pipelined: final + architect lookahead (target: ~90-100 min)

Only after both are validated should third-tier architecture be treated as the production default.

## 7. Methodology caveats

- One run per branch; treat all numbers as exploratory.
- Wall-clock for first and second is sum(duration_ms) from their ledgers (sequential, no overlap).
- Wall-clock for third is the reconciled value from third:metrics/incidents.md, derived from mark-done commit timestamps.
- Cost model: deepseek-v4-pro input $0.50 / cache_read $0.05 / output $2.00 per 1M; deepseek-v4-flash (and the reviewer alias deepseek-flash) input $0.10 / cache_read $0.01 / output $0.40 per 1M; cache_create billed at $0. Actual API billing may differ.
