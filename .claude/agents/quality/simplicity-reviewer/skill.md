---
name: simplicity-reviewer-skill
---
# Simplicity Reviewer Skill
## Workflow
1. Read docs/tasks/T<n>.md and the modified source files (git diff).
2. Read reviews/T<n>-test-engineer.md and reviews/T<n>-correctness-reviewer.md — proceed only if both Verdicts are PASS.
3. Check YAGNI violations, duplication, over-abstraction, dead code.
4. Write reviews/T<n>-simplicity-reviewer.md with Verdict (PASS/FAIL), Findings, Timestamp.
## Contracts
- docs/tasks/T<n>.md acceptance criteria define the scope — no features beyond them (YAGNI).
- docs/project-specification.md section 5: 30 lines/4 parameters are structure rules — never introduce abstractions just to satisfy them.
## Acceptance Criteria
- [ ] No unnecessary interfaces or abstractions.
- [ ] No duplicated code (DRY).
- [ ] No dead code (unused functions, imports).
- [ ] No features not in task acceptance criteria.
- [ ] Verdict file written: reviews/T<n>-simplicity-reviewer.md.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
