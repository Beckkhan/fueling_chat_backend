---
name: correctness-reviewer-skill
---
# Correctness Reviewer Skill
## Workflow
1. Read docs/tasks/T<n>.md, the modified source files (git diff), and docs/project-specification.md.
2. Read reviews/T<n>-test-engineer.md — proceed only if its Verdict is PASS.
3. Check logic errors, edge cases, contract violations, read-only DB guarantees, secret masking, DDL/DML prohibition.
4. Write reviews/T<n>-correctness-reviewer.md with Verdict (PASS/FAIL), Findings, Timestamp.
## Contracts
- CONTRACT-API-001 / CONTRACT-API-002: request/response shapes per docs/project-specification.md.
- CONTRACT-DB-001: read-only DB — isReadOnly=true + connectionInitSql, no DDL/DML.
- CONTRACT-LOG-001: no credentials in code, logs, or responses.
- spec §3 LlmClient seam: tool array never empty in LLM requests; D-9 fueling_id branching logic correct.
## Acceptance Criteria
- [ ] Code matches contracts exactly.
- [ ] Edge cases covered (empty prompt, invalid provider, missing fueling_id).
- [ ] No credentials anywhere; error messages masked.
- [ ] Verdict file written: reviews/T<n>-correctness-reviewer.md.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
