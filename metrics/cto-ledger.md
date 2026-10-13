# CTO Ledger

Detailed per-agent token tracking for cost analysis.

| Task ID | Branch | Agent Type | Model | input_tokens | cache_create | cache_read | output_tokens | duration_ms |
|---|---|---|---|---|---|---|---|---|
| T11 | third | coder-architect | deepseek-v4-pro[1m] | 294 | 0 | 93056 | 1622 | 548276 |
| T11 | third | coder-implementer | deepseek-v4-flash | 157 | 0 | 39936 | 1305 | 47260 |
| T11 | third | coder-implementer | deepseek-v4-flash | 185 | 0 | 40192 | 1064 | 69494 |
| T11 | third | coder-implementer | deepseek-v4-flash | 4244 | 0 | 62080 | 3065 | 214757 |
| T11 | third | coder-implementer | deepseek-v4-flash | 1012 | 0 | 100992 | 2647 | 735850 |
| T11 | third | coder-integrator | deepseek-v4-flash | 382 | 0 | 49920 | 941 | 129618 |
| T11 | third | test-engineer | deepseek-v4-flash | 212 | 0 | 60288 | 687 | 185884 |
| T11 | third | git-reviewer | deepseek-v4-flash | 225 | 0 | 45696 | 504 | 199410 |
| T11 | third | correctness-reviewer | deepseek-v4-flash | 251 | 0 | 69376 | 685 | 224785 |
| T11 | third | simplicity-reviewer | deepseek-v4-flash | 1047 | 0 | 78208 | 790 | 267195 |
| T11 | third | code-style-reviewer | deepseek-v4-flash | 163 | 0 | 86912 | 894 | 361908 |
| T11 | third | coder-implementer (B3 rework) | deepseek-v4-flash | 370 | 0 | 138240 | 1286 | 210018 |
| T11 | third | coder-implementer (B4 rework) | deepseek-v4-flash | 319 | 0 | 91392 | 1414 | 121477 |
| T11 | third | coder-integrator (amend) | deepseek-v4-flash | 480 | 0 | 61568 | 607 | 87142 |
| T11 | third | test-engineer (r2) | deepseek-v4-flash | 262 | 0 | 60928 | 651 | 168564 |
| T11 | third | git-reviewer (r2) | deepseek-v4-flash | 211 | 0 | 38784 | 434 | 59579 |
| T11 | third | code-style-reviewer (r2) | deepseek-v4-flash | 189 | 0 | 65408 | 595 | 176587 |
| T11 | third | simplicity-reviewer (r2) | deepseek-v4-flash | 148 | 0 | 71680 | 569 | 246271 |
| T11 | third | correctness-reviewer (r2) | deepseek-v4-flash | 175 | 0 | 75008 | 786 | 315723 |

Notes:
- Tokens extracted from Agent() completion notifications (subagent_tokens breakdown)
- costUSD unreliable (hasUnknownModelCost: true for DeepSeek models)
- duration_ms is wall-clock time of the sub-agent only
