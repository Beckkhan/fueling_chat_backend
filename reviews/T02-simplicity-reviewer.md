# T02 — Simplicity Review

**Verdict:** pass
**Timestamp:** 2026-10-12T20:42:10Z

## Findings

None blocking.

## Notes

- No dotenv library dependency: a 15-line private parser covers the documented `.env` shape (comments, blank lines, `KEY=VALUE`); adding a third-party parser for one file would be heavier than the code it replaces.
- Two tiny helpers (`requiredValue` / `valueOrDefault` / `intValueOrDefault`) centralize the fail-fast rule instead of 15 inline ternaries; accessors stay one line each.
- One exception type (`ConfigException`) covers missing keys, missing file, and invalid integers — one catch site at boot (T04) handles all config failure modes.
- Defaults are string literals mirroring `.env.example`; no indirection via a second defaults object.
