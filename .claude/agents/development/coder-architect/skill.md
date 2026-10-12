---
name: coder-architect-skill
---
# Coder Architect Skill (Alternative Branch Only)
## Workflow
1. Read docs/tasks/T<n>.md and docs/project-specification.md.
2. Write scaffold in src/main/kotlin/: method signatures, class structures, interfaces, data classes, and TODO comments. No implementation logic.
3. Run: ./gradlew compileKotlin (TODOs are valid Kotlin comments; the scaffold must compile).
4. If fails, fix and retry (max 3 times).
5. Append "STATUS: SCAFFOLD_DONE" to docs/tasks/T<n>.md and commit.
## Contracts
- spec §3 LlmClient seam: any LlmClient-facing signatures must match the pinned surface — never change it.
- CONTRACT-DB-001: read-only DB rules shape any DB-facing signatures; no DDL/DML.
- CONTRACT-API-001 / CONTRACT-API-002: endpoint signatures must match the request/response shapes.
## Acceptance Criteria
- [ ] ./gradlew compileKotlin passes.
- [ ] Only signatures and TODO comments — no method bodies, no business logic.
- [ ] TODO comments are specific (what each method must do).
- [ ] Functions <= 30 lines, <= 4 parameters.
## Communication
Read input files, write output files, exit. Do not dialog with other agents.
