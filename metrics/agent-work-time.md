# Agent Work Time Comparison

Pure agent execution time (sum of all agent durations per task), independent of wall-clock, sleep, stalls, or CTO overhead.

## Usual Branch (Single-Agent Pipeline)

| Task | Owner Time | Reviewers Time | Total Agent Work |
|---|---|---|---|
| T11 | 22m 56s | 13m 01s | 35m 57s |
| T12 | 8m 39s | 9m 31s | 18m 10s |
| T13 | 8m 38s | 11m 22s | 20m 00s |
| T14 | 10m 14s | 16m 26s | 26m 40s |
| T15 | 8m 07s | 9m 04s | 17m 12s |
| **Total** | **58m 34s** | **59m 24s** | **1h 57m 59s** |

T14 reviewers include the handback re-reviews (test-engineer, correctness, simplicity — 3m 55s combined).

## Alternative Branch (Dual-Coder Pipeline)

| Task | Architect Time | Implementer Time | Reviewers Time | Total Agent Work |
|---|---|---|---|---|
| T11 | 5m 58s | 8m 39s | 12m 25s | 27m 02s |
| T12 | 6m 59s | 7m 38s | 12m 45s | 27m 22s |
| T13 | 8m 47s | 18m 37s | 23m 36s | 51m 01s |
| T14 | 5m 53s | 6m 29s | 15m 10s | 27m 32s |
| T15 | 5m 44s | 2m 26s | 8m 10s | 16m 20s |
| **Total** | **33m 21s** | **43m 50s** | **72m 05s** | **2h 29m 17s** |

## Comparison

| Metric | Usual | Alternative | Difference |
|---|---|---|---|
| Total agent work time | 1h 57m 59s | 2h 29m 17s | +31m 18s (+26.5%) |
| Avg per task | 23m 36s | 29m 51s | +6m 15s |

## Interpretation

- This metric excludes: wall-clock gaps, sleep, watchdog stalls, CTO orchestration overhead, operator decision waits.
- This metric includes: only pure agent execution time (coding + reviewing, including test/build runs the agents execute themselves).
- If alternative is slower here, it's due to the dual-coder overhead (architect scaffolding + implementer filling + coordination).
- **Finding: the dual-coder overhead is real in pure execution terms.** Alternative spent 31m 18s more agent work (+26.5%) than usual: coding via architect+implementer cost 77m 11s vs usual's single owner 58m 34s (+31.8%), and alternative's reviewers cost 72m 05s vs usual's 59m 24s (+21.4%) — alternative reviewers read both the scaffold and the filled code and wrote longer reports.
- **The gap concentrates in T13** (51m 01s vs 20m 00s). T13 was the environment-incident task: the implementer's work includes a 10m build that hit the harness's 600s foreground cap and moved to the background, and the test-engineer includes a 6m foreground Gradle re-run. Even after excluding the watchdog-stall idle time (22m 50s implementer + 50m 51s test-engineer, classified as idle), T13 remains the outlier.
- **Coordination cost shows in the wall-clock, not here.** Subtracting agent work from adjusted wall-clock (comparison-report.md): usual leaves ~8m of CTO overhead (2h 06m − 1h 58m), alternative leaves ~78m (3h 48m − 2h 29m). The dual-coder's coordination tax is mostly CTO/queue time between agents, on top of the +31m of extra pure execution.

## Data Sources & Method

- **Sources:** session transcripts under `~/.claude/projects/-Users-Slava-fueling-chat-backend/` — usual run = CTO session `d9b3be39-…` (33 subagent transcripts), alternative run = CTO session `c6b77688-…` (35 subagent transcripts). All 68 agents spawned during T11–T15 are covered; frontend T15 agents ran as subagents of the backend CTO session.
- **Duration rule per agent transcript** (applied uniformly to all 68):
  - `assistant` tool call → its `tool_result`: tool execution = work (capped at the harness's 600s foreground timeout; overflow = background-handoff latency = idle).
  - `assistant` text-only message followed by >60s of silence: the agent finished its turn and waited = idle.
  - `tool_result` → next model message: ≤300s = thinking (work); >300s = idle (watchdog stalls, harness latency).
  - consecutive non-assistant events (interrupts, coordinator messages): idle.
- **Excluded idle totals:** usual ≈ 29m (T11 git-reviewer credential-purge decision wait 15m 19s + T14 owner review/handback wait 11m 10s dominate); alternative ≈ 76m (T13 implementer 22m 50s + T13 test-engineer 50m 51s — the two watchdog-stall incidents).
- The 3h 40m computer-sleep (alternative T13) appears in no agent transcript — no agent was active during it — so it is excluded from this metric automatically.
- Timestamps are UTC (JSONL), second-precision; durations summed and rounded to whole seconds.
