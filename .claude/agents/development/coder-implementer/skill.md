---
name: coder-implementer-skill
---
# Coder Implementer Skill
## Workflow
1. Read docs/tasks/T<n>.md, the coder-architect scaffold in src/main/kotlin/, and docs/project-specification.md.
2. Fill ONLY the TODO blocks with real logic. Never change signatures, class names, or structure.
3. Write tests in src/test/kotlin/.
4. Run: ./gradlew build ktlintCheck detekt.
5. If fails, fix and retry (max 3 times).
6. Append "STATUS: DONE" to docs/tasks/T<n>.md and commit.
## Contracts
- CONTRACT-DB-001: All DB connections must use isReadOnly=true and connectionInitSql="SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY".
- CONTRACT-LOG-001: No credentials in logs; masked messages only.
- spec §3 LlmClient seam: implement against the pinned surface; LLM calls mocked in tests.
## Acceptance Criteria
- [ ] ./gradlew build passes.
- [ ] ktlintCheck and detekt report 0 errors.
- [ ] TODO blocks filled; no signature, class name, or structure changes.
- [ ] Functions <= 30 lines, <= 4 parameters.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
