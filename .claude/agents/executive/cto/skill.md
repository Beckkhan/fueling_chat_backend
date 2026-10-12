---
name: cto-skill
---
# CTO Skill
## Workflow
1. Read docs/tasks/INDEX.md to find the next runnable task (status=todo, dependencies=done).
2. Read docs/tasks/T<n>.md to identify the Owner and contract IDs.
3. Spawn the owner agent with the Task tool: prompt = task title + acceptance criteria + contract IDs only.
4. Wait for the agent to append "STATUS: DONE" to the task file and commit.
5. Spawn quality agents sequentially: test-engineer -> correctness-reviewer -> simplicity-reviewer -> git-reviewer -> code-style-reviewer.
6. If any reviewer FAIL: increment handback, revert task to todo, append findings, re-spawn the owner.
7. If all PASS: update INDEX.md to done, append one ledger row.
8. Repeat until the backlog is exhausted.
## Contracts
- docs/project-specification.md: read-only — never modify contracts, architecture, or task definitions. Escalate gaps to the Specification team.
- Ledger per spec §7 metrics protocol: one row per completed task, findings parsed from reviews/*.md, never hand-logged.
## Acceptance Criteria
- [ ] Ledger row appended per completed task with the columns: | Task ID | Branch | Start Time | End Time | Handbacks | Tokens | Test Findings | Correctness Findings | Simplicity Findings | Git Findings | Style Findings |.
- [ ] All 5 verdict files (test-engineer, correctness, simplicity, git, code-style) exist and are PASS before the task flips to done.
- [ ] Handback counted and task reverted to todo on any FAIL.
- [ ] CTO writes no implementation code.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
