# Comparison Report: first vs second

Generated 2026-10-13. Aggregates metrics/cto-ledger.md from both branches (backend T11-T14, frontend T15). Both runs had TOTAL BLOCKED TIME = 0.

## Executive Summary

- Winner by time: second (172.13 min vs 193.85 min, 21.7 min faster, ~11 percent)
- Winner by cost: second ($0.08 vs $6.58, 81.1x cheaper)
- Winner by quality: second (0 handbacks vs 3)
- Overall recommendation: dual-coder (second) as the default for executing a decomposed task backlog. Reserve single-agent (first) for small, tightly-coupled tasks.

## Time Comparison

| Task | first (min) | second (min) | Difference | Winner |
|---|---|---|---|---|
| T11 | 33.13 | 43.29 | +10.16 | first |
| T12 | 43.36 | 28.02 | -15.34 | second |
| T13 | 69.16 | 35.69 | -33.47 | second |
| T14 | 23.75 | 34.46 | +10.71 | first |
| T15 | 24.45 | 30.67 | +6.22 | first |
| Total | 193.85 | 172.13 | -21.72 | second |

## Cost Comparison

| Task | first ($) | second ($) | Ratio | Winner |
|---|---|---|---|---|
| T11 | 1.0992 | 0.0159 | 69.1x | second |
| T12 | 1.4269 | 0.0149 | 95.6x | second |
| T13 | 2.4970 | 0.0183 | 136.7x | second |
| T14 | 0.9964 | 0.0184 | 54.2x | second |
| T15 | 0.5581 | 0.0136 | 40.9x | second |
| Total | 6.5776 | 0.0811 | 81.1x | second |

## Quality Comparison

| Task | first handbacks | second handbacks | Winner |
|---|---|---|---|
| T11 | 1 | 0 | second |
| T12 | 0 | 0 | tie |
| T13 | 1 | 0 | second |
| T14 | 0 | 0 | tie |
| T15 | 1 | 0 | second |
| Total | 3 | 0 | second |

First-branch handbacks: T11 (simplicity-reviewer FAIL on dead import), T13 (simplicity-reviewer FAIL on dead import), T15 (code-style-reviewer FAIL on Prettier formatting) — each resolved and re-verified. Second branch: all 25 gates passed on first review.

## Token Usage Comparison

| Metric | first | second | Ratio |
|---|---|---|---|
| Total input tokens | 5,364,293 | 6,715 | 798.9x |
| Total cache_read tokens | 177,104,512 | 2,978,176 | 59.5x |
| Total output tokens | 828,784 | 36,283 | 22.8x |

Total tokens: first 183,297,589 vs second 3,021,174 (~60.7x). cache_create is 0 on both branches.

## Key Findings

1. Cost efficiency: second is ~81x cheaper overall ($6.58 to $0.08). The dual-coder split moves the bulk of token consumption to deepseek-v4-flash (10x cheaper input/cache, 5x cheaper output than v4-pro) and keeps each agent's context small. The gap is largest on T13 (136.7x).
2. Speed: second is ~11 percent faster overall (172.1 vs 193.9 min), but the win is not uniform — first won T11, T14, and T15 individually; second won T12 and T13 by large margins (15.3 and 33.5 min).
3. Quality: second had 0 handbacks across all 25 gates; first had 3 handbacks (T11, T13, T15), each requiring owner rework plus gate re-runs.
4. Cache efficiency: second consumed 59.5x less cache_read (2.98M vs 177.1M tokens). Architect scaffolds keep per-agent transcripts short.

## Recommendations

Use first (single-agent) when:

- Small, tightly-coupled tasks where one context is faster than a scaffold handoff
- The task is novel/high-uncertainty and the architect phase cannot predict structure up front
- Simplicity of pipeline operation matters more than cost

Use second (dual-coder) when:

- Executing a decomposed backlog of many tasks
- First-pass quality matters
- Large tasks where the split pays off in time as well as cost

General recommendation: dual-coder by default for backlog execution; fall back to single-agent for small, tightly-coupled tasks or novel domains.

## Methodology Notes

- Both runs had TOTAL BLOCKED TIME = 0
- Pricing per 1M tokens: deepseek-v4-pro input $0.50, cache_read $0.05, output $2.00; deepseek-v4-flash input $0.10, cache_read $0.01, output $0.40. Reviewer model deepseek-flash priced at v4-flash rates.
- All 5 quality gates identical between branches
- Branch-specific agent configurations in .claude/agents/
- Tokens and durations from metrics/cto-ledger.md
- Handbacks counted from (fix)/(r2) ledger rows on first; second has none
