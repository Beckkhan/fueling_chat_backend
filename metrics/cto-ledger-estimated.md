# CTO Ledger (Estimated)

Token breakdown ESTIMATED from totals: the DeepSeek API returned only `subagent_tokens` totals,
so the 4-column breakdown is estimated from typical ratios observed on the third branch
(±30% accuracy). Durations are real (from completion notifications).

| Task ID | Branch | Agent Type | Model | input_tokens | cache_create | cache_read | output_tokens | duration_ms |
|---|---|---|---|---|---|---|---|---|
| T11-T15 | final | planner | deepseek-v4-pro | 0 | 0 | 0 | 0 | 0 |
| T11 | final | implementer (T11-block1) | deepseek-v4-flash | 8065 | 2688 | 37636 | 5377 | 126646 |
| T11 | final | implementer (T11-block2) | deepseek-v4-flash | 14094 | 4698 | 65769 | 9396 | 487990 |
| T11 | final | implementer (T11-block3) | deepseek-v4-flash | 16379 | 5460 | 76433 | 10919 | 524275 |
| T11 | final | integrator | deepseek-v4-flash | 9197 | 3066 | 42918 | 6131 | 189494 |
| T15 | final | implementer (T15-block1) | deepseek-v4-flash | 5953 | 1984 | 27780 | 3969 | 160859 |
| T15 | final | implementer (T15-block2) | deepseek-v4-flash | 7975 | 2658 | 37216 | 5317 | 184015 |
| T15 | final | integrator | deepseek-v4-flash | 5474 | 1825 | 25545 | 3649 | 89581 |
| T15 | final | meta-reviewer | deepseek-v4-pro | 8592 | 4296 | 25774 | 4296 | 136471 |
| T11 | final | meta-reviewer | deepseek-v4-pro | 14420 | 7210 | 43258 | 7210 | 481937 |
| T12 | final | implementer (T12-block2) | deepseek-v4-flash | 14655 | 4885 | 68391 | 9770 | 593828 |
| T12 | final | implementer (T12-block1) | deepseek-v4-flash | 12692 | 4231 | 59230 | 8462 | 621654 |
| T12 | final | implementer (T12-block3) | deepseek-v4-flash | 20632 | 6877 | 96283 | 13755 | 694511 |
| T12 | final | integrator | deepseek-v4-flash | 12315 | 4105 | 57469 | 8210 | 295769 |
| T12 | final | meta-reviewer | deepseek-v4-pro | 16195 | 8098 | 48586 | 8098 | 306420 |
| T14 | final | implementer (T14-block2) | deepseek-v4-flash | 20910 | 6970 | 97578 | 13940 | 628543 |
| T13 | final | implementer (T13-block1) | deepseek-v4-flash | 24544 | 8181 | 114540 | 16363 | 646492 |
| T14 | final | implementer (T14-block1) | deepseek-v4-flash | 19195 | 6398 | 89575 | 12796 | 707139 |
| T13 | final | implementer (T13-block2) | deepseek-v4-flash | 21884 | 7295 | 102127 | 14590 | 889154 |
| T13 | final | integrator | deepseek-v4-flash | 10966 | 3655 | 51174 | 7310 | 330282 |
| T14 | final | integrator | deepseek-v4-flash | 5932 | 1977 | 27681 | 3954 | 113280 |
| T13 | final | meta-reviewer | deepseek-v4-pro | 16632 | 8316 | 49895 | 8316 | 370956 |
| T14 | final | meta-reviewer | deepseek-v4-pro | 15067 | 7533 | 45201 | 7533 | 356693 |

## Totals by agent type

| Tier | Agents | Total tokens | input_tokens | cache_create | cache_read | output_tokens |
|---|---|---|---|---|---|---|---|
| flash (implementers + integrators) | 18 | 1539068 | 230862 | 76953 | 1077345 | 153908 |
| pro (planner + meta-reviewers) | 6 | 354526 | 70906 | 35453 | 212714 | 35453 |

## Estimated cost

- DeepSeek pricing per 1M tokens — v4-flash: input $0.10, cache_read $0.01, output $0.40;
  v4-pro: input $0.50, cache_read $0.05, output $2.00.
- `cache_create` is billed at the input rate (standard cache-write billing; the API price
  sheet used here lists input/cache_read/output only).
- Model assignment follows agent type (implementer/integrator → flash, planner/meta-reviewer → pro).

| Tier | Estimated cost (USD) |
|---|---|
| flash (implementers + integrators) | $0.1031 |
| pro (planner + meta-reviewers) | $0.1347 |
| **Total (all agents)** | **$0.2378** |

Total recorded tokens across all agents: 1,893,594

Notes:
- Rows sum exactly to each agent's recorded `subagent_tokens` total; `cache_read` absorbs rounding.
- Accuracy disclaimer: breakdown columns are ESTIMATES (±30%), not API-reported values.
- The planner row (Phase 0) has no usage data — it ran in a previous session; all zeros.
