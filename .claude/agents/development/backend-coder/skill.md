---
name: backend-coder-skill
---
# Backend Coder Skill
## Workflow
1. Read docs/tasks/T<n>.md and docs/project-specification.md.
2. Implement code in src/main/kotlin/ and tests in src/test/kotlin/.
3. Run: ./gradlew build ktlintCheck detekt.
4. If fails, fix and retry (max 3 times).
5. Append "STATUS: DONE" to docs/tasks/T<n>.md and commit.
## Contracts
- CONTRACT-DB-001: All DB connections must use isReadOnly=true and connectionInitSql="SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY".
- CONTRACT-LOG-001: No credentials in logs; use MaskingTurboFilter.
- spec §3 LlmClient seam: pinned LlmClient surface — tools passed in from the service, never empty.
## Acceptance Criteria
- [ ] ./gradlew build passes.
- [ ] ktlintCheck and detekt report 0 errors.
- [ ] Functions <= 30 lines, <= 4 parameters.
- [ ] LLM calls in tests are mocked; DB tests use the local Docker container or Exposed test utilities.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
