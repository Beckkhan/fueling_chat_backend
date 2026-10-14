# CTO Ledger

Detailed per-agent token tracking for cost analysis.

| Task ID | Branch | Agent Type | Model | input_tokens | cache_create | cache_read | output_tokens | duration_ms |
|---|---|---|---|---|---|---|---|---|
| T11-T15 | final | planner | n/a | 0 | 0 | 0 | 0 | 0 |
| T11 | final | implementer (T11-block1) | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: planner ran in a previous session; completion notification not available to this session -->
<!-- no-token-data: T11-block1 completion notification did not include a token breakdown -->
<!-- T11-block1 partial usage from completion notification: subagent_tokens total = 53766, duration_ms = 126646 (breakdown columns unavailable) -->
| T11 | final | implementer (T11-block2) | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T11-block2 completion notification did not include a token breakdown -->
<!-- T11-block2 partial usage from completion notification: subagent_tokens total = 93957, duration_ms = 487990 (breakdown columns unavailable) -->
| T11 | final | implementer (T11-block3) | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T11-block3 completion notification did not include a token breakdown -->
<!-- T11-block3 partial usage from completion notification: subagent_tokens total = 109191, duration_ms = 524275 (breakdown columns unavailable) -->
| T11 | final | integrator | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T11 integrator completion notification did not include a token breakdown -->
<!-- T11 integrator partial usage from completion notification: subagent_tokens total = 61312, duration_ms = 189494 (breakdown columns unavailable) -->
| T15 | final | implementer (T15-block1) | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T15-block1 completion notification did not include a token breakdown -->
<!-- T15-block1 partial usage from completion notification: subagent_tokens total = 39686, duration_ms = 160859 (breakdown columns unavailable) -->
| T15 | final | implementer (T15-block2) | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T15-block2 completion notification did not include a token breakdown -->
<!-- T15-block2 partial usage from completion notification: subagent_tokens total = 53166, duration_ms = 184015 (breakdown columns unavailable) -->
| T15 | final | integrator | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T15 integrator completion notification did not include a token breakdown -->
<!-- T15 integrator partial usage from completion notification: subagent_tokens total = 36493, duration_ms = 89581 (breakdown columns unavailable) -->
| T15 | final | meta-reviewer | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T15 meta-reviewer completion notification did not include a token breakdown -->
<!-- T15 meta-reviewer partial usage from completion notification: subagent_tokens total = 42958, duration_ms = 136471 (breakdown columns unavailable) -->
| T11 | final | meta-reviewer | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T11 meta-reviewer completion notification did not include a token breakdown -->
<!-- T11 meta-reviewer partial usage from completion notification: subagent_tokens total = 72098, duration_ms = 481937 (breakdown columns unavailable) -->
| T12 | final | implementer (T12-block2) | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T12-block2 completion notification did not include a token breakdown -->
<!-- T12-block2 partial usage from completion notification: subagent_tokens total = 97701, duration_ms = 593828 (breakdown columns unavailable) -->
| T12 | final | implementer (T12-block1) | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T12-block1 completion notification did not include a token breakdown -->
<!-- T12-block1 partial usage from completion notification: subagent_tokens total = 84615, duration_ms = 621654 (breakdown columns unavailable) -->
| T12 | final | implementer (T12-block3) | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T12-block3 completion notification did not include a token breakdown -->
<!-- T12-block3 partial usage from completion notification: subagent_tokens total = 137547, duration_ms = 694511 (breakdown columns unavailable) -->
| T12 | final | integrator | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T12 integrator completion notification did not include a token breakdown -->
<!-- T12 integrator partial usage from completion notification: subagent_tokens total = 82099, duration_ms = 295769 (breakdown columns unavailable) -->
| T12 | final | meta-reviewer | n/a | 0 | 0 | 0 | 0 | 0 |
<!-- no-token-data: T12 meta-reviewer completion notification did not include a token breakdown -->
<!-- T12 meta-reviewer partial usage from completion notification: subagent_tokens total = 80977, duration_ms = 306420 (breakdown columns unavailable) -->

Notes:
- Tokens extracted from Agent() completion notifications (subagent_tokens breakdown)
- costUSD unreliable (hasUnknownModelCost: true for DeepSeek models)
- duration_ms is wall-clock time of the sub-agent only
- Branch is always "final" on this branch
- Agent Type includes block id where applicable, e.g. "implementer (T12-block2)"
- Per-task wall-clock and wave timing are recorded in metrics/incidents.md and metrics/waves.md, NOT in this ledger
