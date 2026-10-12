---
name: code-style-reviewer-skill
---
# Code Style Reviewer Skill
## Workflow
1. Read docs/tasks/T<n>.md and the modified source files (git diff).
2. Read reviews/T<n>-test-engineer.md, -correctness-reviewer.md, -simplicity-reviewer.md, and -git-reviewer.md — proceed only if all Verdicts are PASS.
3. Run: ./gradlew ktlintCheck detekt (backend) or pnpm lint (frontend).
4. Check docs/project-specification.md section 5: 30 lines or fewer per function, 4 parameters or fewer, no trivial KDoc.
5. Write reviews/T<n>-code-style-reviewer.md with Verdict (PASS/FAIL), Findings, Timestamp.
## Contracts
- docs/project-specification.md section 5: style rules (line counts, parameters, KDoc).
- CONTRACT-UI-001 (frontend): Tailwind utilities and palette tokens copied verbatim from the Figma export.
## Acceptance Criteria
- [ ] Backend: ./gradlew ktlintCheck detekt — 0 errors. Frontend: pnpm lint — 0 errors.
- [ ] Auto-fix applied if possible.
- [ ] Functions <= 30 lines, <= 4 parameters, no trivial KDoc.
- [ ] Verdict file written: reviews/T<n>-code-style-reviewer.md.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
