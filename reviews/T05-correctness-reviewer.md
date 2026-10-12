# T05 — Correctness Review

**Verdict:** pass
**Timestamp:** 2026-10-12T21:40:00Z

## Acceptance criteria check

- [x] `docker compose up -d` starts exactly one `postgres:15-alpine` container exposing port 25432 and creating `fueling`, `gas_station`, `payment`, `vendors` via the mounted init script (`docker/init/01-create-databases.sql`).
- [x] `docs/schema.sql` and `docs/seed.sql` apply cleanly via per-DB psql (`ON_ERROR_STOP=1`); the seed is idempotent — applied twice, row counts stable (1 fueling / 1 gas station / 2 payments / 1 fueling order / 2 events / 1 vendor fueling order, no new rows on the second run).
- [x] Four Exposed `Database` objects (`DbPools.fueling/gasStation/payment/vendors`), each on its own HikariCP pool built from the `AppConfig` URLs/credentials; `allFourDatabasesExposeExpectedTables` connects all four and asserts each database's table set. **Finding 1** covers the AC's "13 tables" wording.
- [x] Read-only enforcement, server-side: `insertIsRejectedByReadOnlyServer` proves an INSERT through a pool is rejected by the server with "cannot execute INSERT in a read-only transaction". **Finding 2** covers the mechanism change from the spec's literal `readOnlyMode=always`.
- [x] Structure-only table objects: `grep -r "SchemaUtils.create" src/main` returns nothing.
- [x] Reachability probe: `probeFailsNamingFirstUnreachableDatabase` (all four URLs at a closed port → "database unreachable: fueling"), `probeFailsNamingUnreachableDatabaseAmongReachableOnes` (three live + one closed → names that one), `probePassesWhenAllDatabasesReachable`. Boot wiring lands in T04 per the AC.
- [x] `./gradlew build ktlintCheck detekt` — 0 errors; 26 tests, 0 failures.

## Findings

1. **"13 tables total" does not match the frozen DDL** (spec discrepancy, not a code bug): `docs/schema.sql` defines **6 tables** (fuelings; gas_stations; payments; fueling_events, fueling_orders, vendor_fueling_orders) + 3 sequences + 20 indexes. Spec §4's "13" appears to be a miscount carried from the specification phase. The test asserts the actual 6-table layout per database (verified against information_schema); the spec's table/sequence/index content is what `docs/schema.sql` declares, and that file is frozen.
2. **`readOnlyMode=always` replaced by an equivalent, working server-side mechanism** (spec mechanism broken in the pinned driver): pgjdbc 42.7.14's `setAutoCommit` — verified by test and by decompiling `PgConnection.setAutoCommit` — executes `SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE` whenever auto-commit is disabled under `readOnlyMode=always`, silently undoing the guarantee exactly when Exposed starts its transactions. Pools therefore use `isReadOnly = true` (JDBC read-only flag) + `connectionInitSql = "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY"` (session default, survives autocommit toggling) + pgjdbc's default per-transaction `SET TRANSACTION READ ONLY`. Empirically: session `default_transaction_read_only = on` before and after `setAutoCommit(false)`, and INSERT is rejected by the server. The spec's *intent* — the app can never write, enforced server-side, on all four pools — holds strictly; the *mechanism name* it pinned is the one piece that had to change, and it is documented here.
3. **Exposed version pin changed** (T01 had 1.5.1): the 1.x line (`org.jetbrains.exposed.v1.*`) is a 2026 API reorganization that removed `SchemaUtils` and the classic `org.jetbrains.exposed.sql.*` surface the frozen design/spec and T05's own acceptance grep (`SchemaUtils.create`) target. Pinned to **0.61.0**, the last 0.x release, matching the specification era. Updated in the T05 commit.

## Verdict

`T05 done | verified: ok`
