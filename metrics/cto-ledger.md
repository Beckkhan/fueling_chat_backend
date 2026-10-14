# CTO Ledger

Detailed per-agent token tracking for cost analysis.

| Task ID | Branch | Agent Type | Model | input_tokens | cache_create | cache_read | output_tokens | duration_ms |
|---|---|---|---|---|---|---|---|---|

Notes:
- Tokens extracted from Agent() completion notifications (subagent_tokens breakdown)
- costUSD unreliable (hasUnknownModelCost: true for DeepSeek models)
- duration_ms is wall-clock time of the sub-agent only
- Branch is always "final" on this branch
- Agent Type includes block id where applicable, e.g. "implementer (T12-block2)"
- Per-task wall-clock and wave timing are recorded in metrics/incidents.md and metrics/waves.md, NOT in this ledger
