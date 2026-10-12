# T06 — Correctness Review

**Verdict:** pass
**Timestamp:** 2026-10-12T20:42:00Z

## Acceptance criteria check

- [x] `src/main/resources/tools/get_fueling_info.json` is byte-identical to the JSON body of CONTRACT-TOOL-001; `bundledDescriptorIsByteIdenticalToContractTool001` asserts equality against the embedded contract text (and `diff` against the spec block is empty).
- [x] `GetFuelingInfoTool` implements `SimpleTool<GetFuelingInfoArgs>` with the `@Serializable` args class and `@property:LLMDescription("The fueling identifier to look up.") val fueling_id: String` per the pinned surface (spec §3); the tool is constructed from the descriptor file (`name`, `description`) and the Koog `TypeToken` of the args class.
- [x] The handler executes the spec §4 chain in order, exactly: `fuelings WHERE fueling_id = ? ORDER BY created_at DESC LIMIT 1` → `gas_stations WHERE gas_station_id = ? LIMIT 1` → `payments WHERE order_id = vendor_fueling_order_id ORDER BY created_at DESC LIMIT 100` → vendors tables (`fueling_orders` by `created_at DESC`, `fueling_events` by `event_offset ASC`, `vendor_fueling_orders` by `created_at DESC`, all `LIMIT 100`).
- [x] Against the seeded container, `F-10001` returns the six CONTRACT-TOOL-001-OUT keys with values matching the spec example — asserted section by section (8 integration tests), including both payments (`P-50001` succeeded / `P-50002` failed) and both events (offset 1 then 2).
- [x] Timestamp normalization (D-15): epoch-ms columns (`fuelings.*`, `payments.*`, vendors bigint/numeric) emit ISO-8601 UTC (`2026-10-01T09:30:00Z` etc.); `gas_stations.updated_at` (timestamptz) emits ISO-8601 UTC; text date columns (`vendor_transaction_date`, `date_create`) pass through verbatim; jsonb columns emit their JSON text; `null` stays `null` — all asserted on seeded rows.
- [x] Unknown `fueling_id` returns `{"fueling": null, "gas_station": null, "payments": [], "fueling_orders": [], "fueling_events": [], "vendor_fueling_orders": []}` (exact-equality test).
- [x] Tool-set fail-fast: `ToolDescriptorLoader` fails with a message naming the missing file (`missingDescriptorFailsNamingTheFile`); boot wiring lands in T04.
- [x] `./gradlew build ktlintCheck detekt` — 0 errors; 42 tests total, 0 failures.

## Findings

1. **Numeric scale rendering** (informational, contract nuance): PG `numeric(10,2)` returns scale-2 decimals, so `volume`/`quantity` emit `28.50` while the spec's illustrative example writes `28.5` — numerically identical values; all other decimals (`1500.00`, `52.49`, `1495.50`, `7`) match the example exactly. The tests assert the database-faithful representation; the spec's normative rules (D-15 timestamps, jsonb-as-text, null, six keys) are exact.
2. **Payments ordering tie** (informational): the two seeded payments share the same `created_at` (1790847105000), so their relative order under `ORDER BY created_at DESC` is tie-undefined; the aggregation returns both rows and the test asserts them as a set keyed by `payment_id` rather than assuming an order.
3. **detekt config addition** (transparent): `ConstructorParameterNaming` is excluded for `GetFuelingInfoArgs.kt` — the `fueling_id` parameter name is mandated by the pinned Koog/descriptor surface and intentionally breaks camelCase; the exclusion is scoped to exactly that file.

## Verdict

`T06 done | verified: ok`
