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
| T12 | second | coder-architect | deepseek-v4-pro[1m] | 283 | 0 | 98432 | 1652 | 485196 |
| T12 | second | coder-implementer | deepseek-v4-flash | 184 | 0 | 86784 | 1193 | 290980 |
| T12 | second | test-engineer | deepseek-flash | 202 | 0 | 70272 | 790 | 173524 |
| T12 | second | correctness-reviewer | deepseek-flash | 166 | 0 | 95360 | 752 | 290865 |
| T12 | second | simplicity-reviewer | deepseek-flash | 168 | 0 | 76672 | 652 | 160988 |
| T12 | second | git-reviewer | deepseek-flash | 177 | 0 | 50304 | 737 | 166328 |
| T12 | second | code-style-reviewer | deepseek-flash | 191 | 0 | 61952 | 961 | 113219 |
| T13 | second | coder-architect | deepseek-v4-pro[1m] | 156 | 0 | 122624 | 1816 | 443497 |
| T13 | second | coder-implementer | deepseek-v4-flash | 213 | 0 | 142592 | 1462 | 612707 |
| T13 | second | test-engineer | deepseek-flash | 191 | 0 | 72704 | 1107 | 190682 |
| T13 | second | correctness-reviewer | deepseek-flash | 141 | 0 | 149632 | 1144 | 356495 |
| T13 | second | simplicity-reviewer | deepseek-flash | 146 | 0 | 70016 | 698 | 155715 |
| T13 | second | git-reviewer | deepseek-flash | 260 | 0 | 59520 | 728 | 107115 |
| T13 | second | code-style-reviewer | deepseek-flash | 162 | 0 | 92928 | 965 | 275240 |
| T14 | second | coder-architect | deepseek-v4-pro[1m] | 180 | 0 | 130560 | 2121 | 530673 |
| T14 | second | coder-implementer | deepseek-v4-flash | 174 | 0 | 107648 | 1923 | 302686 |
| T14 | second | test-engineer | deepseek-flash | 190 | 0 | 70784 | 665 | 177319 |
| T14 | second | correctness-reviewer | deepseek-flash | 239 | 0 | 111488 | 995 | 324192 |
| T14 | second | simplicity-reviewer | deepseek-flash | 231 | 0 | 55424 | 642 | 74814 |
| T14 | second | git-reviewer | deepseek-flash | 157 | 0 | 47104 | 779 | 104670 |
| T14 | second | code-style-reviewer | deepseek-flash | 237 | 0 | 110592 | 917 | 553185 |
| T15 | second | frontend-architect | deepseek-v4-pro[1m] | 192 | 0 | 89728 | 1521 | 460338 |
| T15 | second | frontend-implementer | deepseek-v4-flash | 256 | 0 | 56832 | 1097 | 181183 |
| T15 | second | test-engineer | deepseek-flash | 254 | 0 | 67328 | 672 | 428063 |
| T15 | second | correctness-reviewer | deepseek-flash | 242 | 0 | 65792 | 848 | 158831 |
| T15 | second | simplicity-reviewer | deepseek-flash | 152 | 0 | 43648 | 541 | 68046 |
| T15 | second | git-reviewer | deepseek-flash | 136 | 0 | 71040 | 886 | 209295 |
| T15 | second | code-style-reviewer | deepseek-flash | 173 | 0 | 86016 | 944 | 334232 |

Notes:
- Tokens extracted from Agent() completion notifications (subagent_tokens breakdown)
- costUSD unreliable (hasUnknownModelCost: true for DeepSeek models)
- duration_ms is wall-clock time of the sub-agent only
