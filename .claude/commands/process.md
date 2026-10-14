# Process: final branch (radical optimization)

## Phases

### Phase 0: Pre-compute scaffolds
1. Spawn planner agent
2. Planner reads all tasks T11-T15, analyzes complexity, decomposes into blocks
3. Planner writes scaffold per block in scaffolds/T<n>-block<id>.md
4. Each scaffold contains literal invariants block
5. Planner commits: "scaffolds: pre-computed for T11-T15"
6. Append planner row to metrics/cto-ledger.md

### Phase 1: Cross-task parallel implement (waves)
Dependency graph:
- Wave 1: T11 (independent)
- Wave 2: T12 + T15 (both depend on T11)
- Wave 3: T13 + T14 (both depend on T12)

For each wave:
1. Spawn implementers for all blocks in all tasks of this wave (parallel)
2. Each implementer reads its scaffold, implements, runs local validation
3. Append implementer rows to metrics/cto-ledger.md
4. For each task in the wave, spawn integrator (can be parallel across tasks)
5. Integrator wires blocks, runs full verification, commits
6. Append integrator row to metrics/cto-ledger.md

### Phase 2: Meta-review (per task, can overlap across tasks)
1. Spawn meta-reviewer for each task in the wave
2. Meta-reviewer performs single-pass review (invariants + logic + API + tests)
3. Append meta-reviewer row to metrics/cto-ledger.md
4. If PASS: mark task done, move to Phase 4
5. If FAIL: move to Phase 3

### Phase 3: Fixer loop (if FAIL)
1. Spawn fixer with review findings
2. Fixer fixes directly (max 2 cycles)
3. Append fixer row to metrics/cto-ledger.md
4. Re-run meta-reviewer (Phase 2)
5. If still FAIL after 2 cycles: escalate to incidents.md, mark task blocked

### Phase 4: Measure & close
1. Record task wall-clock in metrics/incidents.md
2. Update docs/tasks/INDEX.md: task status → done
3. Commit: "T<n>: mark done"
- Record the task wall-clock (minutes, from wave start to task close) in the per-task wall-clock table of metrics/incidents.md, and update the wave row in metrics/waves.md when the wave completes.

## Token Tracking Protocol
After EVERY Agent() completion notification, extract the subagent_tokens breakdown (input_tokens, cache_creation_input_tokens, cache_read_input_tokens, output_tokens) and the agent duration. Append exactly one row to metrics/cto-ledger.md in the 9-column format: Task ID, Branch (final), Agent Type (with block id for implementers, e.g. "implementer (T12-block2)"), Model, input_tokens, cache_create, cache_read, output_tokens, duration_ms. Rows are append-only — never edit a previous row. If a completion notification lacks token data, append the row with zeros and add "no-token-data" in a trailing comment line below the table.

## Concurrency limits
- Max 6 concurrent implementers (to avoid resource exhaustion)
- Max 3 concurrent meta-reviewers
- If more needed, queue them

## Handback protocol
- Reviewer never returns to implementer; only to fixer
- Handback count per task ≤ 2
- Each handback logged in metrics/incidents.md
