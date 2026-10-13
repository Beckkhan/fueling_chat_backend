# T12 — Git Review

**Verdict:** PASS
**Timestamp:** 2026-10-13T17:17:07Z

## Scope

Commit `f032e39` ("T12: ChatService & DialogHistory") on branch `second` — commit atomicity, message format, `.gitignore` hygiene, and secrets in history — plus the surrounding branch state (untracked review files, modified `metrics/cto-ledger.md`). Gates 1–3 are PASS (`reviews/T12-test-engineer.md`, `reviews/T12-correctness-reviewer.md`, `reviews/T12-simplicity-reviewer.md`).

## Checks performed

**Commit message format.** Subject `T12: ChatService & DialogHistory` matches the required `T<n>: <title>` exactly and matches the INDEX.md title for T12 verbatim. Attribution trailer present; author/committer identical to the repo's prior commits; single parent (`c92e9d3`), non-merge.

**One commit per task (atomicity).** `git log second` shows `f032e39` as the only T12 commit; no scaffold commit, no stray fix-up. All 9 files are T12-scoped and are exactly the artifact list declared in `docs/tasks/T12.md`: `DialogHistory.kt`, `ProviderRouter.kt`, `ChatService.kt`, `ServiceModule.kt`, `ChatMessage.kt` (`@Serializable`), the three service test files, plus the `T12.md` status lines. The architect's scaffold (uncommitted per the dual-coder pipeline design) landed in this same commit, consistent with the one-commit-per-task convention; the commit removes the T11 scaffold TODO comments from `ChatService.kt`/`ServiceModule.kt`, and no TODO markers remain in any T12 file at HEAD. No build outputs, no IDE files, no `.env`, no unrelated refactors. No machine-specific paths (`/Users/…`) or leftover markers in the diff; no binary files.

**`.env` not committed / secrets.** `.env` and `.env.stage-backup` are untracked and ignored; a filename scan across all refs finds only `.env.example` (angle-bracket placeholders, no real values). Value-precise pickaxe `-S` over all refs (counts only, no values printed): real and stage `DEEPSEEK_API_KEY` — 0 commits; stage `DB_PASSWORD` — 0 commits; `OLLAMA_MODEL` — 0 commits. The local `.env` `DB_PASSWORD` is the literal string `postgres`, the public docker-compose dev default visible in tracked `docker-compose.yml` (`POSTGRES_PASSWORD: postgres`) and used in test env fixtures — not a leak. High-entropy sweep of `f032e39` and history: no `sk-…`, no AWS `AKIA…`, no private-key material, no Bearer tokens. Diff-specific scan of `f032e39`: no secret-like strings, no credentials, no provider text.

**`.gitignore`.** Covers `.env`, `.env.stage-backup`, `build/`, `data/` (the D-11 journal path `data/dialog-history.jsonl`), `.gradle/`, `.DS_Store`, `.idea/`. `git status --porcelain --ignored` shows all worktree noise ignored; the only untracked non-ignored files are the three intentional T12 review files (see observation 1). Nothing `f032e39` adds requires a new rule.

## Observations (non-blocking)

1. `metrics/cto-ledger.md` is modified in the worktree (5 T12 token rows: coder-architect, coder-implementer, test-engineer, correctness-reviewer, simplicity-reviewer) and the three gate review files are untracked. Judged **acceptable out-of-commit scope**: this is CTO bookkeeping, not T12 code, and it correctly does not appear in `f032e39`. Precedent `c92e9d3` ("T11: mark Ktor Chat Route done") committed reviews + ledger + INDEX.md + task header together; the T12 mark-done commit must likewise land the ledger, all review files and the INDEX.md header flip. Leaving them uncommitted indefinitely would be the hygiene issue.
2. `docs/tasks/T12.md` carries both appended status lines (`STATUS: SCAFFOLD_DONE`, `STATUS: DONE`) while the header `**Status:**` still reads `todo`, and the INDEX.md T12 row still reads `todo`. This matches the T11 precedent exactly (`ac759f7` carried both status lines; `c92e9d3` flipped the header and INDEX). INDEX.md is CTO-owned and is correctly untouched by `f032e39`. The CTO reconciles this in the mark-done step.
3. `.claude/settings.local.json` is ignored only by the machine-global excludes file (`~/.config/git/ignore`), not by the repo's `.gitignore`; it has never been committed from this machine. Same minor portability gap noted in the T11 review; not caused by T12.
4. `f032e39` does not touch `detekt.yml`, leaving the `TODO:` allowance in place. Correct for now: a scaffold TODO remains in `AppModule.kt` (attributed to T11), so narrowing the allowance would break the detekt gate; the T11 review's suggestion to narrow it is still future cleanup.

## Verdict

`T12-git-reviewer: PASS | verified: ok` — message format, atomicity, `.env`/secret hygiene and `.gitignore` all clean; four non-blocking observations recorded.
