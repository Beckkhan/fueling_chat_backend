---
description: Execute the task backlog via the CTO in EXECUTION ONLY mode
argument-hint: "[task-id | all]"
---

# /process

You are the **cto** agent of this harness. Before anything else, read `.claude/agents/executive/cto.md` and follow it exactly.

## EXECUTION ONLY mode

You may not change requirements, architecture, agent scopes, or task definitions. Any gap you find is escalated to the Specification team — never fixed inline.

## Steps

1. Read `docs/tasks/INDEX.md` to get the task backlog and dependency graph.
2. Interpret `$ARGUMENTS`:
   - empty or `all` — run every `todo` task whose dependencies are `done`, in dependency order
   - a task id (e.g. `T11`) — run only that task
3. For each runnable task:
   a. Read the task file `docs/tasks/T<n>.md` to identify the `Owner` (e.g., `backend-coder`, `frontend-coder`).
   b. **Spawn the owning agent as a subagent** with the Agent tool:
      - subagent type = the agent name (e.g., `backend-coder` from `.claude/agents/development/backend-coder.md`)
      - prompt = the task title, acceptance criteria, and contract IDs from the task file's `contracts:` field
      - the agent's own definition (`.claude/agents/<team>/<agent>.md`) loads automatically as its instructions
      - spawn template (token-efficient): task title + acceptance criteria + contract IDs only — NO restated definition content, NO verbatim contract text
   c. Wait for the agent to complete: it must append "STATUS: DONE" to `docs/tasks/T<n>.md` and commit its code.
   d. Append the agent's row to `metrics/cto-ledger.md` per the Token Tracking Protocol.
4. Run the task pipeline per the Parallel Spawning Protocol (Phase 0-3) defined below.
5. If ANY reviewer verdict is "FAIL":
   - Increment the task's handback count (reported in the handoff summary; the ledger holds per-agent rows only).
   - Revert task status to "todo" in `docs/tasks/T<n>.md` and append reviewer findings.
   - Re-spawn the owner agent with the findings list (do NOT recreate the agent — resume with context).
   - Loop back to step 3c.
6. If ALL reviewers "PASS":
   - Update task status to "done" in `docs/tasks/INDEX.md`.
   - No extra ledger row at task completion: per-agent rows were already appended per the Token Tracking Protocol. Handback count and findings per gate are derived from the review files at the measurement phase (spec §7, D-5).
7. Repeat until no runnable tasks remain.

## Branch-specific behavior (third)

- Task ownership by task ID: T11, T12, T13, T14 = backend tasks; T15 = frontend task in /Users/Slava/fueling_chat_front
- Backend task pipeline: coder-architect (scaffold with blocks) -> N coder-implementers IN PARALLEL (one per block) -> coder-integrator -> 5 reviewers IN PARALLEL
- Frontend task pipeline: frontend-architect (scaffold with blocks) -> N frontend-implementers IN PARALLEL -> frontend-integrator -> 5 reviewers IN PARALLEL

## Parallel Spawning Protocol

### Phase 0 — Architect scaffold with blocks

- Spawn coder-architect (or frontend-architect). Its scaffold MUST contain:
  - Block boundaries (2 to 4 blocks per task)
  - Cross-block interfaces (signatures each block exposes to others)
  - OWNERSHIP map: exactly which files each implementer owns; shared/wiring files assigned to the integrator
  - TODO markers per block
- Architect appends STATUS: SCAFFOLD_DONE, does NOT commit.

### Phase 1 — Parallel implementers

- Read the OWNERSHIP map from the scaffold.
- Spawn ALL implementers for the blocks in ONE message (multiple Agent tool calls in parallel), each prompt containing: its block TODOs, its owned file list, the cross-block interfaces, and the rule: NEVER edit files outside your ownership list.
- Wait for ALL implementer completion notifications before Phase 2.
- Append one ledger row per implementer.

### Phase 2 — Integrator

- Spawn coder-integrator (or frontend-integrator) after all implementers finish.
- It wires shared files, resolves cross-block errors, runs full verification, commits.
- Append ledger row.

### Phase 3 — Parallel reviewer fan-out

- Spawn ALL 5 quality agents in ONE message (parallel), each reviewing the final integrated diff.
- Collect all 5 verdict files. If ANY verdict is FAIL: append ledger rows, revert task to todo with findings, re-spawn the responsible phase (implementers or integrator) with findings, then re-run the fan-out.
- If ALL PASS: close out task (INDEX flip, mark-done commit).
- Append one ledger row per reviewer.

### Wall-clock accounting note

- Parallel agents overlap in time. Sum of duration_ms in the ledger will EXCEED wall-clock. Record both: ledger keeps per-agent duration_ms; the CTO session notes task wall-clock separately in the task closeout message.

### File ownership rule

- Two agents must never edit the same file. Shared files (DI modules, App.tsx, wiring) belong ONLY to the integrator. Violation = integration conflict; the integrator resolves it and notes it in the commit message.

## Handoff

When the backlog is exhausted or blocked, summarize:
- Tasks done (with timestamps and handback counts)
- Tasks blocked (with reasons)
- Any specification gaps escalated to the Specification team

## Token Tracking Protocol

After each Agent() call completes, extract these fields from the completion notification:
- subagent_tokens.input_tokens
- subagent_tokens.cache_creation_input_tokens
- subagent_tokens.cache_read_input_tokens
- subagent_tokens.output_tokens
- duration_ms
- The model used (visible in agent transcript)

Immediately append a row to metrics/cto-ledger.md with:
- Task ID (e.g., T11)
- Branch (first/second)
- Agent type (coder-architect, coder-implementer, test-engineer, etc.)
- Model (deepseek-v4-pro[1m] or deepseek-flash)
- All 4 token counts
- duration_ms

Do this after EVERY Agent() call, not just at task completion.

## Metrics ledger format

`metrics/cto-ledger.md` contains one row per Agent() call:

| Task ID | Branch | Agent Type | Model | input_tokens | cache_create | cache_read | output_tokens | duration_ms |
|---|---|---|---|---|---|---|---|---|

Rows are appended per the Token Tracking Protocol — after EVERY Agent() call, not just at task completion. costUSD is unreliable (hasUnknownModelCost: true for DeepSeek models) and is not recorded.

## Hard-gate reminders

- Agents communicate ONLY through files (task → artifact → verdict). No dialog between agents.
- Only the CTO updates statuses in `docs/tasks/INDEX.md` and `docs/tasks/T<n>.md`.
- Do NOT change contracts, architecture, or task definitions. Escalate gaps.
- Verdict-only for builds: read `./gradlew build` or `pnpm build` output only by final verdict (`--quiet` or `2>&1 | tail -N`). Never read full output.
