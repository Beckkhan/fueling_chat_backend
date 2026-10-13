# Comparison Report — `usual` vs `alternative`

Measurement-phase deliverable (spec §7). Aggregates `metrics/cto-ledger.md`, `metrics/incidents.md`, and `reviews/*.md` from both repos on branches `usual` and `alternative` (backend T11–T14, frontend T15). Generated 2026-10-13 on `main`.

## Executive Summary

Usual (single-agent pipeline) won on time and tokens: 2h 23m 49s raw wall-clock (~2h 06m after removing the 18m 17s credential-purge block) vs alternative's 7h 47m 08s (~3h 48m after removing ~4h of infrastructure blocks), and 2,449,667 tokens vs 2,671,822 (~8.3% fewer). Alternative (dual-coder: architect on deepseek-v4-pro scaffolding, implementer on deepseek-v4-flash) won on quality: 0 blocking findings and 0 handbacks across all 25 gates, vs usual's 3 blocking simplicity findings on T14 (duplicated test scaffolding) driving exactly 1 handback, which was resolved and re-verified. The key trade-off is speed/cost vs cleanliness: the architect→implementer split adds coordination and scaffolding overhead (concentrated in T13, ~2h adjusted vs usual's 22m) but produced code that passed every gate on first review. Recommendation: choose usual when wall-clock dominates; choose alternative when first-pass quality and structural separation matter — and note that at public DeepSeek pricing (v4-flash ≈ 8–32% of v4-pro per token) alternative is likely cheaper in dollars despite its higher token count, narrowing usual's edge to time.

## Raw Metrics

Per-branch table — backend T11–T14, frontend T15:

| Metric | Usual (backend) | Usual (frontend) | Alternative (backend) | Alternative (frontend) |
|---|---|---|---|---|
| Wall-clock time | 2h 05m 00s | 18m 49s | 7h 28m 39s | 18m 29s |
| Tokens | 2,100,763 | 348,904 | 2,264,614 | 407,208 |
| Handbacks | 1 (T14) | 0 | 0 | 0 |
| Total findings | 3 (T14, simplicity) | 0 | 0 | 0 |

Totals: usual = 2h 23m 49s wall-clock, 2,449,667 tokens, 1 handback, 3 findings. Alternative = 7h 47m 08s, 2,671,822 tokens, 0 handbacks, 0 findings.

"Total findings" counts gate-blocking findings (those driving a FAIL verdict or handback), per the ledger convention. Non-blocking notes are covered in Quality Comparison.

## Adjusted Metrics

Same table, wall-clock minus blocked intervals from each branch's `metrics/incidents.md`.

Blocked-time rows used:

- **usual: 18m 17s** (credential purge) — T11: git gate FAIL on pre-existing credentials in history (real DeepSeek API key + stage Postgres password in an old `docs/spec-source.md` blob). Pipeline halted for operator decision; covers the decision wait plus history-rewrite remediation (git-filter-repo, reflog expire, GC, re-verification, gate re-run). Exact timestamps (verdict timestamps).
- **alternative: ~4h** — T13: 2 × 10m watchdog stalls (implementer agent, then test-engineer agent; each killed at 600s and resumed via SendMessage) + ~3h 40m computer-sleep mid-response. Approximate timestamps (marked "approx" in incidents.md).

Adjusted wall-clock:

| Branch | Raw | Blocked | Adjusted |
|---|---|---|---|
| usual | 2h 23m 49s (≈2h 24m) | 18m 17s | 2h 05m 32s (≈2h 06m) |
| alternative | 7h 47m 08s (≈7h 48m) | ~4h | ~3h 47m 08s (≈3h 48m) |

Both blocked intervals fall on backend tasks (usual T11 → 1h 46m 43s adjusted; alternative T13 → ~3h 28m 39s adjusted). Frontend rows are unchanged.

| Metric | Usual (backend) | Usual (frontend) | Alternative (backend) | Alternative (frontend) |
|---|---|---|---|---|
| Adjusted wall-clock | 1h 46m 43s | 18m 49s | ~3h 28m 39s | 18m 29s |
| Tokens | 2,100,763 | 348,904 | 2,264,614 | 407,208 |
| Handbacks | 1 (T14) | 0 | 0 | 0 |
| Total findings | 3 (T14, simplicity) | 0 | 0 | 0 |

## Quality Comparison

Blocking findings by gate, per task per branch (ledger convention; every verdict re-read from `reviews/*.md` and cross-checked against the ledger).

**usual**

| Task | test | correctness | simplicity | git | code-style | Handbacks |
|---|---|---|---|---|---|---|
| T11 | 0 | 0 | 0 | 0 | 0 | 0 |
| T12 | 0 | 0 | 0 | 0 | 0 | 0 |
| T13 | 0 | 0 | 0 | 0 | 0 | 0 |
| T14 | 0 | 0 | 3 | 0 | 0 | 1 |
| T15 | 0 | 0 | 0 | 0 | 0 | 0 |

The 3 T14 simplicity findings (FAIL at `4bf7371`) were test-side duplication: a byte-identical 39-line JSON accessor block in both LLM-client tests; shared constants duplicated in both companion objects; near-duplicate recorder + `appConfig()` scaffolding. Handback 1 (`355ed78`) rewrote the two test files around one shared fixture exactly as prescribed — no production-code churn — and the simplicity gate plus upstream gates re-verified PASS.

**alternative**

| Task | test | correctness | simplicity | git | code-style | Handbacks |
|---|---|---|---|---|---|---|
| T11 | 0 | 0 | 0 | 0 | 0 | 0 |
| T12 | 0 | 0 | 0 | 0 | 0 | 0 |
| T13 | 0 | 0 | 0 | 0 | 0 | 0 |
| T14 | 0 | 0 | 0 | 0* | 0 | 0 |
| T15 | 0 | 0 | 0 | 0 | 0 | 0 |

\* alternative T14 git gate: PASS with 3 **non-blocking** findings — housekeeping carry-overs (`.claude/settings.local.json` ignored only by machine-global git excludes, 4th report; `.kotlin/` not ignored, 3rd report) and task-file bookkeeping (T14.md/INDEX status flip left to the follow-up "mark done" commit). None blocked the gate or triggered a handback.

Non-blocking notes elsewhere: both branches' T13/T14 correctness reviews carry informational observations (stale local `.env` `/v1` suffix, `ProviderRouter` eager resolution until T14, stale `ServiceModule` comment on alternative T14, test-strength nuances). Frontend T15 passed all five gates on both branches with informational notes only (G-1 pin decision, `provider: string` typing looseness, `.env.local` not gitignored, whole-repo Prettier artifacts, pre-existing `sendMessage` length).

Highlight: **usual: 3 blocking simplicity findings on T14, 1 handback. alternative: 0 blocking findings, 0 handbacks.**

## Cost Analysis

Tokens per task per branch:

| Task | usual | alternative |
|---|---|---|
| T11 | 475,036 | 542,599 |
| T12 | 454,889 | 600,129 |
| T13 | 361,034 | 571,298 |
| T14 | 809,804 | 550,588 |
| T15 | 348,904 | 407,208 |
| **Total** | **2,449,667** | **2,671,822** |

Total: usual 2,449,667 vs alternative 2,671,822 — **usual ~8.3% cheaper in tokens**. Note: alternative used the dual-coder split (architect on deepseek-v4-pro, implementer on deepseek-v4-flash). If v4-flash is significantly cheaper per token, alternative may still be cost-effective despite the higher token count.

Estimated cost using public DeepSeek pricing (retrieved 2026-10-13; official pages + public trackers — cloudzero.com, apidog.com):

| Model | Input (per 1M tokens) | Output (per 1M tokens) |
|---|---|---|
| deepseek-v4-pro | $1.74 (standard) / $0.435 (post-discount, reported permanent) | $3.48 (standard) / $0.87 (post-discount) |
| deepseek-v4-flash | $0.14 | $0.28 |

The flash/pro ratio is identical for input and output: **~8% at standard pro rates, ~32% at post-discount pro rates**. Assuming a 3:1 input:output split (blended: pro ≈ $2.175 / $0.544 per 1M; flash ≈ $0.175) and noting ledger tokens span the whole per-task pipeline (CTO cost-counter delta, reviewers included, so the flash share is a fraction of the total):

| Scenario | Est. cost (standard pro) | Est. cost (discounted pro) |
|---|---|---|
| usual — all tokens at pro | ≈ $5.33 | ≈ $1.33 |
| alternative — worst case, all tokens at pro | ≈ $5.81 (+9.1%) | ≈ $1.45 |
| alternative — 50/50 pro/flash mix | ≈ $3.14 (−41%) | ≈ $0.96 (−28%) |
| alternative — all tokens at flash (lower bound) | ≈ $0.47 | ≈ $0.47 |

Break-even: alternative matches usual's dollar cost once ≥ ~9% (standard pro) or ≥ ~12% (discounted pro) of its tokens bill at flash rates — a low bar given the implementer does the bulk of code writing, though the actual pro/flash mix is unmeasured. These conclusions are robust to the 3:1 assumption, because the flash/pro ratio is split-independent. At equal per-token pricing (both all-pro), usual wins by its 8.3% token delta.

## Time Efficiency

Per-task wall-clock (raw → adjusted where a block falls in the task):

| Task | usual (raw → adjusted) | alternative (raw → adjusted) |
|---|---|---|
| T11 | 53m 32s → 35m 15s | 27m 57s |
| T12 | 19m 25s | 29m 16s |
| T13 | 21m 55s | 6h 01m 52s → ~2h 01m 52s |
| T14 | 30m 08s | 29m 34s |
| T15 | 18m 49s | 18m 29s |
| **Total** | 2h 23m 49s → **2h 05m 32s** | 7h 47m 08s → **~3h 47m 08s** |

Speedup: on adjusted wall-clock, **alternative is ~1.8× slower than usual** (≈2h 06m vs ≈3h 48m); equivalently usual is ~45% faster. The gap is concentrated in T13 (~5.6× even after removing the blocked time) — the dual-coder chain (architect scaffold → implementer fill → five gates) and its two watchdog stalls all landed there. It is not uniform: alternative's T11 actually beat usual's adjusted T11 (27m 57s vs 35m 15s), and T14/T15 were near-parity (within 1.5 min). The dual-coder overhead shows on the highest-uncertainty backend task and is negligible on the frontend task.

## Process Notes

- **Gate-chain and ledger convention.** The runs used a 5-gate chain per task — test → correctness → simplicity → git → code-style — recorded as finding columns in one per-branch ledger, `metrics/cto-ledger.md`. Design §8 (and `docs/tasks/INDEX.md`) describes 4 sequential gates (correctness → simplicity → git → code-style), and spec §7's metrics protocol names per-repo, per-branch ledger files (`metrics/usual.md`, `metrics/alternative.md`). This report follows the ledger convention (5 gates, `cto-ledger.md`, blocking-finding counts).
- **D-16 wording escalations.** Koog 1.3.0 surface deviations were documented in the T13/T14 correctness reviews on both branches: the Ollama client speaks Ollama's native `/api/chat` (not the OpenAI-compatible `/v1` surface the task text names), and the 1.3.0 pin ships no stable DeepSeek client (the remote client is implemented via the pinned OpenAI client relabelled to `LLMProvider.DeepSeek`). Both were corrected in the implementation and recorded as documentation-only amendments for the spec owner — **no code changes made** beyond matching the pinned jar surface.

## Data Quality Notes

- All token counts are exact (CTO-session cost-counter deltas per spec §7; the usual-branch values were backfilled from session usage records, not estimates — commit `0cf7ec4`).
- Blocked times: usual's 18m 17s is exact (bracketed by git-reviewer FAIL/PASS verdict timestamps); alternative's ~4h is approximate (incidents.md timestamps marked "approx").
- The computer-sleep incident (alternative T13, ~3h 40m) was an environment failure, not operator delay or pipeline misbehavior.
- Wall-clock includes review-gate time by construction (ledger rows span dispatch → `verified: ok`); only incident-blocked intervals are subtracted.
- All 25 gate verdicts per branch (backend T11–T14 × 5 gates; frontend T15 × 5 gates) were re-read for this report and match the ledger counts.

## Recommendations

**When to choose `usual` (single-agent pipeline):**

- Time-critical projects — ~45% faster on adjusted wall-clock.
- Budget-constrained projects at equal per-token pricing — ~8.3% fewer tokens.
- When some rework is acceptable — 1 handback occurred (test-only dedupe, resolved and re-verified with no production churn).

**When to choose `alternative` (dual-coder pipeline):**

- Quality-critical projects — 0 handbacks, 0 blocking findings across all 25 gates.
- When architect + implementer separation improves code structure (signatures/contracts fixed by the architect before implementation).
- When v4-flash is significantly cheaper per token — public pricing puts it at ~8–32% of v4-pro, making alternative cost-competitive or cheaper in dollars despite its higher token count.

**Concrete threshold:** if v4-flash is <50% the cost of v4-pro per token, alternative becomes cost-competitive despite the higher token count. Public DeepSeek pricing meets this comfortably (flash ≈ 8% of standard pro, ≈ 32% of discounted pro); the break-even flash share of alternative's tokens is ~9–12%. With that priced in, the remaining usual advantage is wall-clock time alone.
