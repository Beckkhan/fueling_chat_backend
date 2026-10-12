---
name: test-engineer-skill
---
# Test Engineer Skill
## Workflow
1. Read docs/tasks/T<n>.md, the modified source files (git diff), and the test files.
2. Verify tests are present, isolated, and properly mock external dependencies (DB, LLM).
3. Run: ./gradlew test (backend) or pnpm test (frontend).
4. Write reviews/T<n>-test-engineer.md with Verdict (PASS/FAIL), Findings, Timestamp.
## Contracts
- CONTRACT-DB-001: DB tests must use the local Docker container or Exposed test utilities — never a production DB.
- spec §3 LlmClient seam: LLM calls must be mocked (MockK on the LlmClient seam) — no real network calls.
- CONTRACT-API-001 / CONTRACT-API-002: frontend API-client tests must mock fetch against these shapes.
## Acceptance Criteria
- [ ] Every new or changed function has unit tests (JUnit 5 + MockK backend; Vitest + RTL frontend).
- [ ] Tests are isolated: no shared mutable state, no order dependence, deterministic assertions.
- [ ] Tests are not flaky: no sleeps, no time-dependent expectations.
- [ ] DB tests use local Docker or Exposed test utilities; LLM calls mocked.
- [ ] Verdict file written: reviews/T<n>-test-engineer.md.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
