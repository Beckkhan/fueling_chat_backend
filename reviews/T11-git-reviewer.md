# T11 — Git Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T16:44:12Z

## Scope

Commit `ac759f7` ("T11: Ktor Chat Route") on branch `second` — commit atomicity, message format, `.gitignore` hygiene, and secrets in history — plus the surrounding branch state (untracked review files, modified `metrics/cto-ledger.md`). Gates 1–3 are PASS (`reviews/T11-test-engineer.md`, `reviews/T11-correctness-reviewer.md`, `reviews/T11-simplicity-reviewer.md`).

## Checks performed

**Commit message format.** Subject `T11: Ktor Chat Route` matches the required `T<n>: <title>` exactly and matches the INDEX.md title for T11 verbatim. Informative body, attribution trailer present, author/committer identical to the repo's prior commits. Single parent (`d9fcd82`), non-merge.

**One commit per task (atomicity).** `git log second` for every T11 path (`routes/`, `ChatService.kt`, `ChatTurn.kt`, `ChatFailure.kt`, test file, `docs/tasks/T11.md`, `detekt.yml`) shows `ac759f7` as the only T11 content commit; no scaffold commit, no stray fix-up commit. All 12 files are T11-scoped: the artifacts listed in `docs/tasks/T11.md` (sources, tests, `build.gradle.kts` with `ktor-server-status-pages` + `ktor-server-test-host`), the task-file status line, and `detekt.yml` (the TODO allowance required for this commit's own scaffold TODOs to clear the AC's detekt gate — see observation 1). No build outputs, no IDE files, no `.env`, no unrelated refactors. `git diff HEAD -- src build.gradle.kts detekt.yml docs/tasks/T11.md .gitignore` is empty — the reviewed tree is the commit.

**`.env` not committed / secrets.** `.env` and `.env.stage-backup` are untracked and ignored; a filename scan across all refs finds only `.env.example` (placeholders, no real values) and `SecretMasker.kt`. Value-precise checks (pickaxe `-S` on the exact `KEY=value` assignments, counts only) over all refs: real `DEEPSEEK_API_KEY` — 0 commits (both files); stage `DB_PASSWORD` — 0 commits; stage `DB_URL` contains no userinfo and no password text. The only `DB_PASSWORD=` fixture equal to the local `.env` value is the public docker-compose dev default (`POSTGRES_PASSWORD: postgres`, visible in tracked `docker-compose.yml`), used in test env fixtures — not a leak. High-entropy sweep (AWS AKIA, Slack `xox*`, `-----BEGIN`, long Bearer tokens, `secret_key=` assignments) — 0 hits. The `BEGIN…PRIVATE KEY` grep hits are prose inside first-branch review files describing their own scan, not key material, and not on `second`. `ChatRoutesTest`'s `supersecret` string is a deliberate fake fixture proving non-leakage.

**`.gitignore`.** Covers `.env`, `.env.stage-backup`, `build/`, `data/`, `.gradle/`, `.DS_Store`, `.idea/`. `git status --porcelain --ignored` shows all worktree noise ignored; the only untracked non-ignored files are the three intentional T11 review files (see observation 3). Nothing the T11 commit adds needs a new rule. One minor gap outside T11 scope: `.claude/settings.local.json` is ignored only by the machine-global excludes file (`~/.config/git/ignore`), not by the repo's `.gitignore` (observation 4).

## Observations (non-blocking)

1. `detekt.yml` (`ForbiddenComment.allowedPatterns: 'TODO:'`) is bundled into the T11 commit. Judged acceptable: the scaffold TODOs committed here (`AppModule.kt`, `ServiceModule.kt`, `ChatService.kt`) would otherwise fail detekt, so the config change is causally part of landing T11's AC. Harness-only changes elsewhere use the `harness:` prefix; this one is not separable without breaking the commit's own gate. The simplicity gate already recommends narrowing or reverting the allowance once the dual-coder pipeline no longer needs it — endorse folding that into T12.
2. `docs/tasks/T11.md` carries two appended status lines (`STATUS: SCAFFOLD_DONE`, `STATUS: DONE`) while the header `**Status:**` still reads `todo` and INDEX.md reads `todo`. The appended lines are the dual-coder pipeline's handoff convention and the task prompt states the status line is intentionally in this commit; INDEX.md is untouched (CTO-owned), which is correct. The stale header field should be reconciled by the CTO's mark-done step.
3. Branch state: `metrics/cto-ledger.md` is modified (5 T11 token rows) and the three gate review files are untracked. Judged acceptable out-of-commit scope — this is CTO bookkeeping, not a T11 atomicity defect, and matches the first-branch precedent where `1506332 T11: mark Ktor Chat Route done` committed reviews + ledger + INDEX together. Requirement: the CTO must land these in the bookkeeping/mark-done commit; leaving them uncommitted indefinitely would be the hygiene issue.
4. `.claude/settings.local.json` relies on a global gitignore. It is not committed from this machine, but a repo-level targeted entry would make the protection portable (a broad `.claude/` ignore would be wrong — `.claude/agents/` and `.claude/commands/` are tracked deliberately). Not part of T11.

## Verdict

`T11-git-reviewer: PASS | verified: ok` — message format, atomicity, `.env`/secret hygiene and `.gitignore` all clean; four non-blocking observations recorded.
