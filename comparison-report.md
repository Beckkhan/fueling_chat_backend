# Comparison Report: first vs second vs third vs final

Generated 2026-10-14 on main. Aggregates metrics from four experiment branches (backend T11–T14, frontend T15). All four branches executed the same T11–T15 backlog from the same foundation.

## Executive Summary

- Winner by time: **final** (61 min run wall-clock vs next best third 139 min, 2.3× faster)
- Winner by cost: **second** ($0.081 vs next best third $0.104, 1.28× cheaper)
- Winner by quality: **second and final** (0 handbacks each)
- Overall recommendation: **final** for time-critical backlogs, **second** for cost-critical backlogs

Key findings:

1. Radical optimization (pre-computed scaffolds + cross-task parallelism + single-pass meta-reviewer) achieved **3.18× speedup** vs the single-agent baseline (61 min vs 194 min).
2. Parallel dual-coder (third/final) consistently outperforms sequential (second) in wall-clock: 139 min and 61 min vs 172 min.
3. Single-agent (first) is the most expensive by far: $6.58 vs $0.08–$0.24 — 183M tokens vs 1.9M–4.2M, driven by one agent re-reading the full context through 5 sequential gates.
4. The meta-reviewer (single-pass) replaces 5 sequential reviewers, cutting review time to ~6 min per task.
5. Pre-computed scaffolds save planning time but introduce gaps (T13 base-URL premise, T14 dependency omitted) that integrators fixed in-run (not counted as handbacks).

## Wall-clock Time Comparison

| Task | first | second | third | final | Fastest |
|---|---|---|---|---|---|
| T11 | 33 min | 43 min | 36 min | 22 min | final |
| T12 | 43 min | 28 min | 24 min | 23 min | final |
| T13 | 69 min | 36 min | 36 min | 28 min | final |
| T14 | 24 min | 34 min | 25 min | 30 min | first |
| T15 | 25 min | 31 min | 18 min | 8 min | final |
| **Total** | **194 min** | **172 min** | **139 min** | **61 min** | **final** |

Speedup vs first:

- second: 1.13×
- third: 1.40×
- final: 3.18×

Note: first and second ran sequentially; their per-task values are the sum of per-agent `duration_ms` from their ledgers. third's per-task values are git-anchored task wall-clocks from its incidents.md. final ran tasks in parallel ACROSS waves — per-task wall-clocks sum to 111 min, but the run wall-clock (Wave 1 start → T14 close) is 61 min, which is the comparable figure.

## Cost Comparison

| Task | first ($) | second ($) | third ($) | final ($) | Cheapest |
|---|---|---|---|---|---|
| T11 | 1.10 | 0.016 | 0.029 | 0.049 | second |
| T12 | 1.43 | 0.015 | 0.020 | 0.058 | second |
| T13 | 2.50 | 0.018 | 0.021 | 0.057 | second |
| T14 | 1.00 | 0.018 | 0.020 | 0.049 | second |
| T15 | 0.56 | 0.014 | 0.015 | 0.025 | second |
| **Total** | **6.58** | **0.081** | **0.104** | **0.238** | **second** |

Cost ratio vs first:

- second: 81× cheaper
- third: 63× cheaper
- final: 28× cheaper

Note: final branch cost is estimated from total tokens using typical ratios from the third branch (±30% accuracy). cache_create is billed at the input rate (0 on first/second/third; estimated 5–10% on final).

## Quality Comparison

| Task | first handbacks | second handbacks | third handbacks | final handbacks | Cleanest |
|---|---|---|---|---|---|
| T11 | 1 (dead import) | 0 | 1 (function length) | 0 | second, final |
| T12 | 0 | 0 | 0 | 0 | all |
| T13 | 1 (dead import) | 0 | 0 | 0 | second, third, final |
| T14 | 0 | 0 | 0 | 0 | all |
| T15 | 1 (Prettier) | 0 | 0 | 0 | second, third, final |
| **Total** | **3** | **0** | **1** | **0** | **second, final** |

Handback reasons:

- first: simplicity-reviewer caught dead imports twice (T11, T13); code-style-reviewer caught a Prettier violation (T15, printWidth 100) — the only guards, since ktlint/detekt/lint were not wired to catch them.
- third: code-style-reviewer caught a function-length violation in T11 (`chatRoutes` 47 lines vs the 30-line cap).
- final: zero handbacks; the meta-reviewer accepted documented D-16 deviations and in-run scaffold-gap fixes as non-blocking.

## Pipeline Comparison

| Branch | Coding model(s) | Coordination | Review phase | Global invariants | Typical use |
|---|---|---|---|---|---|
| first | v4-pro (single) | Sequential | 5 reviewers sequential | Implicit | Small tightly-coupled tasks |
| second | v4-pro architect + v4-flash implementer | Sequential | 5 reviewers sequential | Implicit | Cost-critical backlogs |
| third | v4-pro architect + v4-flash implementer | Parallel blocks | 5 reviewers parallel | Implicit | Time-critical backlogs |
| final | v4-pro planner/meta-reviewer + v4-flash implementer/integrator | Cross-task parallel | Meta-reviewer (single) | Explicit in scaffolds | Time-critical, high-quality |

## Key Findings

1. **Parallelism compounds.** third's within-task parallelism (parallel blocks + parallel reviewers) beat sequential second 139 vs 172 min; final's cross-task parallelism (overlapped waves + meta-review overlapped with next-wave implementers) beat third 61 vs 139 min (2.3×).

2. **Pre-computed scaffolds save planning time but introduce gaps.** The planner ran once for all five tasks (saving repeated per-task planning), but two scaffold gaps surfaced: the T13 scaffold's `/v1` base-URL premise was wrong (verified against pinned Koog sources and corrected), and the T14 scaffold omitted the `prompt-executor-deepseek-client-jvm:1.3.0-beta` dependency. Both were caught by implementers and fixed by integrators in-run — net cost a few minutes, not a handback.

3. **The meta-reviewer replaces 5 sequential reviewers.** Single-pass review (invariants + logic + API + tests) took ~6 min per task on final vs the 5-gate sequence on second. Review findings were Info-only; zero blocking defects.

4. **Single-agent context bloat is expensive.** first used 183.3M tokens vs 3.0M (second), 4.2M (third), 1.9M (final): one agent re-reading the entire conversation through 5 sequential gates. That makes first 81× more expensive than second and 28× more expensive than final despite using the cheapest cache-read rate.

5. **Executable invariants prevent style handbacks.** final's ktlint/detekt/eslint gates (run by every implementer and integrator) eliminated the class of handback that hit first (dead imports, Prettier) and third (30-line function cap). final's zero-handback record is partly earned by the pipeline, not just the reviewers.

6. **Cost is driven by model tier, not just agent count.** final (24 agent rows incl. planner) costs $0.238 — 2.3× third's $0.104 (63 rows): final ran its five meta-reviewers on v4-pro (≈57% of final's total cost), while third's reviewers were flash. Trading $0.13 for a 2.3× wall-clock cut is the final-branch deal.

## Recommendations

### Use first when:

- Task is small and tightly-coupled (single file or module)
- Context is simple enough that re-reading cost is negligible
- Quality gates are not critical

### Use second when:

- Cost is the primary constraint ($0.081 vs $0.238 for final)
- Zero handbacks are required (first-pass quality)
- Wall-clock time is not critical (172 min acceptable)

### Use third when:

- Time matters but budget is tight (139 min, $0.104)
- Parallel infrastructure is available (max 6 concurrent agents)
- Some handbacks are acceptable (1 on average)

### Use final when:

- Time is the primary constraint (61 min, 3.18× faster than first, 2.3× faster than third)
- Zero handbacks are required
- Budget allows $0.24 (still 28× cheaper than first)
- Pre-computed scaffolds are feasible (all tasks known upfront)

### General recommendation:

Use **final** as the default pipeline for multi-component backlogs where time matters: 3.18× faster than single-agent, 2.3× faster than the best prior parallel branch, zero handbacks, at $0.24 (28× cheaper than the single-agent baseline). Reserve **second** for cost-sensitive scenarios where wall-clock is not the limiting factor and $0.08 per run matters.

## Methodology Notes

- All four branches executed the same T11–T15 backlog from the same foundation (main).
- Pricing per 1M tokens: v4-pro input $0.50 / cache_read $0.05 / output $2.00; v4-flash input $0.10 / cache_read $0.01 / output $0.40; cache_create billed at input rate.
- Branch-specific agent configurations in `.claude/agents/`.
- Tokens and durations read from each branch's `metrics/cto-ledger.md`; final branch cost read from `metrics/cto-ledger-estimated.md` (breakdown estimated from totals using third-branch ratios).
- Wall-clock for first/second is sum(`duration_ms`) from ledgers (sequential execution); for third it is git-anchored per-task wall-clock from `metrics/incidents.md`; for final it is per-task wall-clock from `metrics/incidents.md` plus the 61-min run wall-clock from `metrics/waves.md`.
- Handbacks counted from `## Handbacks` sections in `docs/tasks/T<n>.md` and fixer/rework rows in the ledgers.

## Data Quality Notes

- All token counts for first/second/third are exact (per-agent completion notifications with full breakdown).
- final branch token TOTALS are exact, but the 4-column breakdown is estimated (±30% accuracy) due to a DeepSeek API limitation (`subagent_tokens` total only, no breakdown).
- cache_create is 0 on first/second/third (not tracked separately); on final it is estimated at 5% (flash) / 10% (pro) of totals.
- Sum of `duration_ms` on parallel branches (third/final) deliberately exceeds wall-clock due to agent overlap.
- final branch scaffold gaps (T13 base-URL, T14 dependency) were detected and fixed during the run, not counted as handbacks.
- first's T13 wall-clock (69 min) includes a 44-minute backend-coder implementation run and two review rounds; it dominates first's total.
- third's T13 includes an architect "dep pre-stage" row (dependency verification before blocks) — an extra pipeline step not present on other branches.
