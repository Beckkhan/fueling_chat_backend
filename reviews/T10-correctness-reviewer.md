# T10 — Correctness Review

**Verdict:** pass
**Timestamp:** 2026-10-12T22:20:00Z

## Acceptance criteria check

- [x] Backend `.github/workflows/ci.yml` triggers on push and PR to `main`, runs `./gradlew build detekt ktlintCheck` on a JDK 17 runner (temurin 17 — the declared toolchain, so no extra JDK download), and provides a `postgres:15-alpine` service container on 25432. Because GH Actions service containers start before checkout and cannot mount the repo's `docker/init/`, the workflow creates the four databases with explicit `CREATE DATABASE` steps, then applies `docs/schema.sql` and `docs/seed.sql` (seed twice — idempotency, D-12) with `ON_ERROR_STOP=1`.
- [x] CI flow validated locally end-to-end: a fresh `postgres:15-alpine` container with the workflow's exact settings (trust auth, port 25432) plus the workflow's exact psql steps, followed by `./gradlew build detekt ktlintCheck` — BUILD SUCCESSFUL, 42 tests / 0 failures (including the DB-backed T05/T06 suites and the boot e2e). Note: the T10 dependency gate (T01 + T07) is the AC's precondition; running the backend workflow against the finished foundation code also covers T02–T06.
- [x] Frontend `.github/workflows/ci.yml` triggers on push and PR to `main` and runs `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm build` (pnpm 12.11.2 via `pnpm/action-setup`, Node 22 via `setup-node` with pnpm caching); the same commands exit 0 locally against the T07–T09 code.
- [x] Both workflows gate only `main` (branch filters on push and pull_request) and contain no secrets — no API keys or credentials anywhere (the DB service uses `POSTGRES_HOST_AUTH_METHOD: trust`, so not even a local password appears). Verified by parsing both files.
- [x] CI configuration is committed on `main` before the `usual`/`alternative` fork (this commit precedes Step 2), so both branches inherit identical gates (design §7).

## Findings

1. **Explicit `CREATE DATABASE` steps added** (necessary deviation from "service container + schema/seed step"): GitHub Actions creates service containers before repository checkout, so the repo's `docker/init/` mount (used by `docker compose`) cannot be used there. The workflow reproduces the init script as four `psql -c "CREATE DATABASE ..."` steps — same effect, no behavior difference, disclosed here for the reviewer trail.
2. **`trust` auth in CI, password in local compose** (informational): the pinned tests connect with `postgres/postgres`; under `trust` the password is ignored, so CI stays credential-free while local dev keeps the documented `postgres/postgres` login.
3. **Frontend CI runs lint+build, not tests** (per AC): the T10 AC lists exactly `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm build` (design §7: "build + lint"); the T08/T09 test suite exists and can be added to the gate when the branch tasks extend it.

## Verdict

`T10 done | verified: ok`
