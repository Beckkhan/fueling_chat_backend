# CTO Ledger

Detailed per-agent token tracking for cost analysis.

| Task ID | Branch | Agent Type | Model | input_tokens | cache_create | cache_read | output_tokens | duration_ms |
|---|---|---|---|---|---|---|---|---|
| T11 | first | backend-coder | deepseek-v4-pro[1m] | 192430 | 0 | 9838592 | 45799 | 694483 |
| T11 | first | test-engineer | deepseek-flash | 167771 | 0 | 3696512 | 24592 | 172710 |
| T11 | first | correctness-reviewer | deepseek-flash | 191996 | 0 | 2016000 | 23112 | 194653 |
| T11 | first | simplicity-reviewer | deepseek-flash | 159261 | 0 | 2139008 | 17687 | 153883 |
| T11 | first | backend-coder (fix) | deepseek-v4-pro[1m] | 2930 | 0 | 1449344 | 1544 | 62441 |
| T11 | first | test-engineer (r2) | deepseek-flash | 92347 | 0 | 2916736 | 15066 | 117002 |
| T11 | first | correctness-reviewer (r2) | deepseek-flash | 88492 | 0 | 1350528 | 10783 | 92324 |
| T11 | first | simplicity-reviewer (r2) | deepseek-flash | 57142 | 0 | 1297408 | 6992 | 282016 |
| T11 | first | git-reviewer (r2) | deepseek-flash | 209976 | 0 | 2031616 | 21871 | 134048 |
| T11 | first | code-style-reviewer (r2) | deepseek-flash | 182614 | 0 | 1994112 | 12532 | 84408 |

Notes:
- Tokens extracted from Agent() completion notifications (subagent_tokens breakdown)
- costUSD unreliable (hasUnknownModelCost: true for DeepSeek models)
- duration_ms is wall-clock time of the sub-agent only
