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
   b. **Spawn the owning agent as a subagent** with the Task tool:
      - subagent type = the agent name (e.g., `backend-coder` from `.claude/agents/development/backend-coder.md`)
      - prompt = the task title, acceptance criteria, and contract IDs from the task file's `contracts:` field
      - the agent's own definition (`.claude/agents/<team>/<agent>.md`) loads automatically as its instructions
      - spawn template (token-efficient): task title + acceptance criteria + contract IDs only — NO restated definition content, NO verbatim contract text
   c. Wait for the agent to complete: it must append "STATUS: DONE" to `docs/tasks/T<n>.md` and commit its code.
   d. Record start timestamp in `metrics/cto-ledger.md`.
4. After the owner agent finishes, spawn the quality agents sequentially on the task's diff:
   Spawn quality agents sequentially: test-engineer -> correctness-reviewer -> simplicity-reviewer -> git-reviewer -> code-style-reviewer.
   - `test-engineer` → writes `reviews/T<n>-test-engineer.md`
   - on PASS: `correctness-reviewer` → writes `reviews/T<n>-correctness-reviewer.md`
   - on PASS: `simplicity-reviewer` → writes `reviews/T<n>-simplicity-reviewer.md`
   - on PASS: `git-reviewer` → writes `reviews/T<n>-git-reviewer.md`
   - on PASS: `code-style-reviewer` → writes `reviews/T<n>-code-style-reviewer.md`
   Each reviewer reads the code and task file, then writes its verdict file.
5. If ANY reviewer verdict is "FAIL":
   - Increment handback count in `metrics/cto-ledger.md`.
   - Revert task status to "todo" in `docs/tasks/T<n>.md` and append reviewer findings.
   - Re-spawn the owner agent with the findings list (do NOT recreate the agent — resume with context).
   - Loop back to step 3c.
6. If ALL reviewers "PASS":
   - Update task status to "done" in `docs/tasks/INDEX.md`.
   - Record end timestamp, handback count (0), and findings count per gate in `metrics/cto-ledger.md`.
   - Format: `| T<n> | <branch> | <start> | <end> | <handbacks> | <tokens> | <test findings> | <correctness findings> | <simplicity findings> | <git findings> | <style findings> |`
7. Repeat until no runnable tasks remain.

## Branch-specific behavior

- On `usual` branch: spawn one `backend-coder` or `frontend-coder` per task (single-agent approach).
- On `alternative` branch: spawn two agents per task:
  1. `coder-architect` (model: deepseek-v4-pro) — writes method signatures and TODO comments only, no implementation.
  2. `coder-implementer` (model: deepseek-v4-flash) — reads the scaffold and fills in the implementation per TODO blocks.
  Then run the 5 quality agents (test-engineer -> correctness-reviewer -> simplicity-reviewer -> git-reviewer -> code-style-reviewer) on the final result.

## Handoff

When the backlog is exhausted or blocked, summarize:
- Tasks done (with timestamps and handback counts)
- Tasks blocked (with reasons)
- Any specification gaps escalated to the Specification team

## Metrics ledger format

`metrics/cto-ledger.md` must contain:
| Task ID | Branch | Start Time | End Time | Handbacks | Tokens | Test Findings | Correctness Findings | Simplicity Findings | Git Findings | Style Findings |

Append one row per completed task.

## Hard-gate reminders

- Agents communicate ONLY through files (task → artifact → verdict). No dialog between agents.
- Only the CTO updates statuses in `docs/tasks/INDEX.md` and `docs/tasks/T<n>.md`.
- Do NOT change contracts, architecture, or task definitions. Escalate gaps.
- Verdict-only for builds: read `./gradlew build` or `pnpm build` output only by final verdict (`--quiet` or `2>&1 | tail -N`). Never read full output.
