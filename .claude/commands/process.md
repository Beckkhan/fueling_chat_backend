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
4. After the owner agent finishes, spawn the quality agents sequentially on the task's diff:
   Spawn quality agents sequentially: test-engineer -> correctness-reviewer -> simplicity-reviewer -> git-reviewer -> code-style-reviewer.
   - `test-engineer` → writes `reviews/T<n>-test-engineer.md`
   - on PASS: `correctness-reviewer` → writes `reviews/T<n>-correctness-reviewer.md`
   - on PASS: `simplicity-reviewer` → writes `reviews/T<n>-simplicity-reviewer.md`
   - on PASS: `git-reviewer` → writes `reviews/T<n>-git-reviewer.md`
   - on PASS: `code-style-reviewer` → writes `reviews/T<n>-code-style-reviewer.md`
   Each reviewer reads the code and task file, then writes its verdict file.
5. If ANY reviewer verdict is "FAIL":
   - Increment the task's handback count (reported in the handoff summary; the ledger holds per-agent rows only).
   - Revert task status to "todo" in `docs/tasks/T<n>.md` and append reviewer findings.
   - Re-spawn the owner agent with the findings list (do NOT recreate the agent — resume with context).
   - Loop back to step 3c.
6. If ALL reviewers "PASS":
   - Update task status to "done" in `docs/tasks/INDEX.md`.
   - No extra ledger row at task completion: per-agent rows were already appended per the Token Tracking Protocol. Handback count and findings per gate are derived from the review files at the measurement phase (spec §7, D-5).
7. Repeat until no runnable tasks remain.

## Branch-specific behavior

Task ownership by task ID:
- T11, T12, T13, T14 = backend tasks (work in the backend repo)
- T15 = frontend task (work in /Users/Slava/fueling_chat_front)

On `first` branch (single-agent):
- Backend task: spawn `backend-coder`
- Frontend task: spawn `frontend-coder`

On `second` branch (dual-coder):
- Backend task:
  1. `coder-architect` (model: deepseek-v4-pro) — writes method signatures and TODO comments only, no implementation.
  2. `coder-implementer` (model: deepseek-v4-flash) — reads the scaffold and fills in the implementation per TODO blocks.
- Frontend task:
  1. `frontend-architect` (model: deepseek-v4-pro) — creates the React/TypeScript scaffold with TODO markers only, no implementation.
  2. `frontend-implementer` (model: deepseek-v4-flash) — fills the TODO markers with real components, hooks, API calls, and tests.
Then run the 5 quality agents (test-engineer -> correctness-reviewer -> simplicity-reviewer -> git-reviewer -> code-style-reviewer) on the final result — same reviewers for both branches; they auto-detect the repo and tech stack from the task context.

## Agent Spawning Protocol

### Correct Agent() Syntax

Use the Agent tool with explicit parameters. The `model:` in agent frontmatter sets the default model.

Example for second branch (dual-coder):

```
Agent(
  description="Scaffold T11 chat route",
  prompt="[paste T11.md content + acceptance criteria + contract IDs]",
  subagent_type="coder-architect"
)
```

→ Runs on deepseek-v4-pro[1m] (from frontmatter)

Example for first branch (single-agent):

```
Agent(
  description="Implement T11 chat route",
  prompt="[paste T11.md content + acceptance criteria + contract IDs]",
  subagent_type="backend-coder"
)
```

→ Runs on deepseek-v4-pro[1m] (from frontmatter)

DO NOT simulate agents by changing roles within this session. Each Agent() call must spawn a real sub-agent with its own token budget.

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
