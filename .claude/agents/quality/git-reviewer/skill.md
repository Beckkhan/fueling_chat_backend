---
name: git-reviewer-skill
---
# Git Reviewer Skill
## Workflow
1. Read docs/tasks/T<n>.md, the git log and diff for the task, and .gitignore.
2. Read reviews/T<n>-test-engineer.md, -correctness-reviewer.md, and -simplicity-reviewer.md — proceed only if all Verdicts are PASS.
3. Check commit atomicity, message format, .gitignore hygiene, and secrets.
4. Write reviews/T<n>-git-reviewer.md with Verdict (PASS/FAIL), Findings, Timestamp.
## Contracts
- docs/project-specification.md §7 metrics protocol: one commit per task, message format "T<n>: [short title]".
- CONTRACT-LOG-001: no secrets (.env, credentials) in commit history.
## Acceptance Criteria
- [ ] Commit message format: T<n>: [short title].
- [ ] One commit per task (atomic).
- [ ] .env not committed (gitignored).
- [ ] No secrets in commit history.
- [ ] .gitignore complete.
- [ ] Verdict file written: reviews/T<n>-git-reviewer.md.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
