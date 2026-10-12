# T02 — Correctness Review

**Verdict:** pass
**Timestamp:** 2026-10-12T20:42:10Z

## Acceptance criteria check

- [x] `AppConfig` exposes one typed accessor per key of the tracked `.env.example` (15 keys): `dbHost`, `dbPort` (Int), `dbUser`, `dbPassword`, the four `*DbUrl`s, `deepseekApiKey`, `deepseekModel`, `ollamaBaseUrl`, `ollamaModel`, `koogVersion`, `dialogHistoryFile` (Path), `historyLimit` (Int). The default constructor reads `.env` from the working directory.
- [x] Required keys (`DB_PASSWORD`, `DEEPSEEK_API_KEY`, `DEEPSEEK_MODEL`, `OLLAMA_MODEL` — the `<…>`-placeholder keys) throw at construction with a message naming exactly the key; verified per key by two parameterized tests (missing and empty). `ConfigException("missing required config key: <KEY>")`.
- [x] Concrete-value keys fall back to their `.env.example` values when absent; verified by `minimalEnvFileFallsBackToExampleDefaults` against a 4-key `.env` (defaults asserted one per key).
- [x] Fail-fast at construction: `ConfigException` propagates out of the constructor; a missing `.env` file also throws naming the file. Entry-point abort (non-zero exit) is wired in T04 per the AC.
- [x] `./gradlew build ktlintCheck detekt` — 0 errors; 13 tests, 0 failures.

## Findings

1. **Extra strictness beyond the AC** (informational): a missing `.env` file throws `ConfigException(".env file not found: …")` instead of degrading into "missing required config key: DB_PASSWORD". This reports the actual cause; the fail-fast guarantee is unaffected.
2. **Malformed lines are skipped** (informational): lines without `=` are ignored (standard dotenv behavior), inline comments are not stripped. The tracked `.env.example` uses neither, so no contract impact; documented here for the reviewer trail.

## Verdict

`T02 done | verified: ok`
