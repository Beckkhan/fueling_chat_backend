# CTO Ledger

Detailed per-agent token tracking for cost analysis.

| Task ID | Branch | Agent Type | Model | input_tokens | cache_create | cache_read | output_tokens | duration_ms |
|---|---|---|---|---|---|---|---|---|
| T11 | second | coder-architect | deepseek-v4-pro[1m] | 270 | 0 | 95104 | 1803 | 619876 |
| T11 | second | coder-implementer | deepseek-v4-flash | 135 | 0 | 148480 | 1181 | 711539 |
| T11 | second | test-engineer | deepseek-flash | 161 | 0 | 55424 | 656 | 168118 |
| T11 | second | correctness-reviewer | deepseek-flash | 154 | 0 | 87424 | 980 | 245164 |
| T11 | second | simplicity-reviewer | deepseek-flash | 228 | 0 | 63104 | 779 | 140950 |
| T11 | second | git-reviewer | deepseek-flash | 149 | 0 | 70656 | 849 | 438675 |
| T11 | second | code-style-reviewer | deepseek-flash | 165 | 0 | 94208 | 827 | 273207 |

Notes:
- Tokens extracted from Agent() completion notifications (subagent_tokens breakdown)
- costUSD unreliable (hasUnknownModelCost: true for DeepSeek models)
- duration_ms is wall-clock time of the sub-agent only
