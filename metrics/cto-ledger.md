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
| T12 | first | backend-coder | deepseek-v4-pro[1m] | 214024 | 0 | 18025984 | 63287 | 1392629 |
| T12 | first | test-engineer | deepseek-flash | 135837 | 0 | 2668800 | 16781 | 289138 |
| T12 | first | correctness-reviewer | deepseek-flash | 256159 | 0 | 5812224 | 34199 | 427409 |
| T12 | first | simplicity-reviewer | deepseek-flash | 194879 | 0 | 1386112 | 14029 | 135450 |
| T12 | first | git-reviewer | deepseek-flash | 215196 | 0 | 1519232 | 12459 | 143336 |
| T12 | first | code-style-reviewer | deepseek-flash | 303948 | 0 | 2910592 | 18528 | 213623 |
| T13 | first | backend-coder | deepseek-v4-pro[1m] | 410296 | 0 | 31977728 | 64427 | 2636000 |
| T13 | first | test-engineer | deepseek-flash | 83326 | 0 | 2329216 | 15827 | 181394 |
| T13 | first | correctness-reviewer | deepseek-flash | 243811 | 0 | 16466944 | 57713 | 503350 |
| T13 | first | simplicity-reviewer | deepseek-flash | 92089 | 0 | 4033792 | 27899 | 281292 |
| T13 | first | backend-coder (fix) | deepseek-v4-pro[1m] | 2570 | 0 | 2525824 | 2477 | 67406 |
| T13 | first | test-engineer (r2) | deepseek-flash | 32941 | 0 | 847488 | 6103 | 118452 |
| T13 | first | correctness-reviewer (r2) | deepseek-flash | 56308 | 0 | 925312 | 8268 | 73033 |
| T13 | first | simplicity-reviewer (r2) | deepseek-flash | 64113 | 0 | 689152 | 4524 | 36437 |
| T13 | first | git-reviewer | deepseek-flash | 57221 | 0 | 888704 | 10091 | 64424 |
| T13 | first | code-style-reviewer | deepseek-flash | 113434 | 0 | 3281536 | 26260 | 187521 |
| T14 | first | backend-coder | deepseek-v4-pro[1m] | 149735 | 0 | 10926976 | 33181 | 566543 |
| T14 | first | test-engineer | deepseek-flash | 78971 | 0 | 2246656 | 14823 | 170015 |
| T14 | first | correctness-reviewer | deepseek-flash | 230526 | 0 | 11606400 | 36439 | 380522 |
| T14 | first | simplicity-reviewer | deepseek-flash | 114709 | 0 | 2092416 | 15288 | 107307 |
| T14 | first | git-reviewer | deepseek-flash | 94886 | 0 | 1044352 | 9286 | 58486 |
| T14 | first | code-style-reviewer | deepseek-flash | 174313 | 0 | 2994560 | 23220 | 141907 |
| T15 | first | frontend-coder | deepseek-v4-pro[1m] | 131028 | 0 | 2711168 | 16400 | 238347 |
| T15 | first | test-engineer | deepseek-flash | 81653 | 0 | 4758784 | 24133 | 255556 |
| T15 | first | correctness-reviewer | deepseek-flash | 116130 | 0 | 3473024 | 23663 | 195195 |
| T15 | first | simplicity-reviewer | deepseek-flash | 71641 | 0 | 1848064 | 14489 | 116523 |
| T15 | first | git-reviewer | deepseek-flash | 36072 | 0 | 658432 | 7410 | 157497 |
| T15 | first | code-style-reviewer | deepseek-flash | 62373 | 0 | 2532608 | 11772 | 117190 |
| T15 | first | frontend-coder (fix) | deepseek-v4-pro[1m] | 3191 | 0 | 794240 | 1728 | 47046 |
| T15 | first | test-engineer (r2) | deepseek-flash | 34014 | 0 | 1001216 | 5588 | 68104 |
| T15 | first | correctness-reviewer (r2) | deepseek-flash | 51803 | 0 | 859904 | 8168 | 67498 |
| T15 | first | simplicity-reviewer (r2) | deepseek-flash | 54792 | 0 | 1026304 | 7376 | 67787 |
| T15 | first | git-reviewer (r2) | deepseek-flash | 26837 | 0 | 932992 | 8849 | 88578 |
| T15 | first | code-style-reviewer (r2) | deepseek-flash | 30508 | 0 | 577920 | 4121 | 47749 |

Notes:
- Tokens extracted from Agent() completion notifications (subagent_tokens breakdown)
- costUSD unreliable (hasUnknownModelCost: true for DeepSeek models)
- duration_ms is wall-clock time of the sub-agent only
