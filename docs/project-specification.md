# Project Specification — Fueling Chat

Phase 2 of 6: SPECIFICATION. Status: **frozen** (2026-10-12).
Approved design: `docs/fueling_chat-design.md`. Source inputs: `docs/spec-source.md`, `docs/ddl-source.md`.

This document freezes the API/tool contracts, the decision log, and all seven spec gaps recorded in the design (G-1…G-7). Contracts carry unique IDs — implementation tasks reference them by ID only.

---

## 1. Scope, inputs, UI source of truth

Production-grade fullstack chat (React + Kotlin/Ktor) with AI orchestration over fueling data from four Postgres databases. Implemented twice in parallel branches (`first` vs `second`) with time/token/quality metrics compared at the end.

**Inputs:**

| Artifact | Role |
|---|---|
| `docs/fueling_chat-design.md` | Approved design (Phase 1); architecture, flows, decisions |
| `docs/spec-source.md` | Source requirements (Russian); this spec supersedes it where they differ |
| `docs/ddl-source.md` | Informational DDL reference — never executed |
| `docs/schema.sql` | Executable local DDL derived from `ddl-source.md` (this phase) |
| `docs/seed.sql` | Canonical local seed chain `F-10001` (this phase) |
| Figma Make export — `/Users/Slava/Desktop/Scandinavian Style Chat Design/src/App.tsx` + `src/index.css` | **Single source of truth for the UI** (G-1) |

**G-1 resolution (recorded here explicitly):** the Figma Make export code (`src/App.tsx` + `src/index.css` in the folder above) is the single source of truth for the UI. The spec's named screenshot (`Screenshot 2026-10-07 at 17.11.27.png`) is absent from the folder and the remaining image could not be rendered by the design tooling; the export was used as the authoritative reference in the design phase and stays authoritative for implementation. Frontend parity means copying the export's markup, Tailwind utilities, palette tokens (`#17302e`, `#e8e4dc`, `#fbfaf7`, `#d66b50`, `#4e7667`) and fonts (DM Sans body, Manrope display) verbatim, decomposed without touching markup/styles (design §5).

---

## 2. API contracts

### CONTRACT-API-001 — `POST /chat` request

```json
{
  "prompt": "What do we know about fueling F-10001?",
  "fueling_id": "F-10001",
  "provider": "local"
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `prompt` | string | yes | Non-empty after trim. Empty/whitespace → `400`. |
| `fueling_id` | string | no | If present, injected into the system prompt as explicit context — the LLM does **not** extract it from message text (D-9). If absent, the LLM may extract an id from the message or answer without the tool. |
| `provider` | string | no | `"local"` \| `"remote"`. Absent → `local` (D-10). Any other value → `400`. |

Frontend sends `{prompt, provider}` on send, plus `fueling_id` when the user pinned one. Content type: `application/json`.

### CONTRACT-API-002 — `POST /chat` response

`200 OK` — the response contract is frozen as exactly these three fields (G-6):

```json
{
  "summary": "F-10001 completed at Tverskaya 7: 28.5 l of AI-95 for 1495.50 ₽, paid by bank card.",
  "provider": "local",
  "elapsed_ms": 1243
}
```

| Field | Type | Contract |
|---|---|---|
| `summary` | string | LLM summary of the fueling data; written in the language of the user's latest message (D-6). |
| `provider` | string | The provider that served the turn: `"local"` (Ollama) or `"remote"` (DeepSeek). |
| `elapsed_ms` | integer ≥ 0 | Server wall-clock of the whole turn: route entry → response ready. |

**Error responses** (same endpoint, masked bodies — never SQL, credentials or raw provider errors):

| Status | When | Body |
|---|---|---|
| `400` | Empty prompt, invalid `provider`, malformed JSON | `{"error": "<masked message>"}` |
| `500` | Any of the four DBs fails | `{"error": "<masked message>"}` |
| `502` | Provider unreachable, or the tool loop itself breaks | `{"error": "<masked message>"}` |

Tool failure (e.g. fueling not found) is **not** an HTTP error: it is returned to the model as the tool result and the model explains it in `summary`. The frontend renders non-200 bodies in a muted error bubble (design §5).

---

## 3. Tool contract

### CONTRACT-TOOL-001 — `resources/tools/get_fueling_info.json`

Exact file content (path: `resources/tools/get_fueling_info.json` in the backend repo):

```json
{
  "name": "get_fueling_info",
  "description": "Look up one fueling by its id across four internal databases (fueling, gas_station, payment, vendors). Returns the fueling record, its gas station, its payments and the vendor-side order rows as aggregated JSON. Timestamps are ISO-8601 UTC strings; JSON columns are returned as their JSON text.",
  "input_schema": {
    "type": "object",
    "properties": {
      "fueling_id": {
        "type": "string",
        "description": "The fueling identifier to look up. Use exactly the value given by the user."
      }
    },
    "required": ["fueling_id"],
    "additionalProperties": false
  }
}
```

This file is the source both for the Koog tool descriptor and for the startup fail-fast check (tool set must be non-empty, design §3/§6).

### CONTRACT-TOOL-001-OUT — aggregated tool result

The handler returns one JSON object to the model:

```json
{
  "fueling": { "…": "one fueling object or null" },
  "gas_station": { "…": "one gas station object or null" },
  "payments": [ "… 0..N, ordered by created_at DESC" ],
  "fueling_orders": [ "… 0..N, ordered by created_at DESC" ],
  "fueling_events": [ "… 0..N, ordered by event_offset ASC" ],
  "vendor_fueling_orders": [ "… 0..N, ordered by created_at DESC" ]
}
```

Example for the seeded chain `F-10001` (values from `docs/seed.sql`):

```json
{
  "fueling": {
    "fueling_id": "F-10001",
    "vendor_fueling_order_id": "VFO-10001",
    "user_id": "U-90001",
    "status": "completed",
    "amount": 1500.00,
    "fuel_type": "AI-95",
    "gas_station_id": "GS-20001",
    "gas_pump_id": "PUMP-7",
    "refueling_gun_id": "GUN-3",
    "fuel_reservation_key": "RESV-10001",
    "created_at": "2026-10-01T09:30:00Z",
    "updated_at": "2026-10-01T09:31:45Z",
    "actual_amount": 1495.50,
    "vendor_transaction_date": "2026-10-01T09:30:00Z",
    "failed_reason": null,
    "vendor_fuel_price": 52.49,
    "fueled_orders": "[{\"orderId\": \"VFO-10001\"}]",
    "discount_fuel_price": null,
    "fueling_type": "regular",
    "extra": "{}",
    "fueling_payment_type": "prepayment",
    "finished_at": "2026-10-01T09:30:05Z"
  },
  "gas_station": {
    "gas_station_id": "GS-20001",
    "brand": "FuelCorp",
    "address": "Moscow, Tverskaya 7",
    "vendor_gas_station_id": "VS-30001",
    "latitude": 55.7601,
    "longitude": 37.6184,
    "activated": true,
    "deleted": false,
    "fueling_limits": "{\"maxLiters\": 100}",
    "gas_station_name": "Tverskaya 7",
    "manually_disabled": false,
    "updated_at": "2026-09-15T08:00:00Z",
    "gas_station_type": "UNDEFINED",
    "extra": "{}",
    "location_id": "msk_mo",
    "payment_mode": "PREPAID",
    "subnet": null,
    "owner": null,
    "public_brand": null,
    "take_off_mode": "after",
    "available_services": "{}"
  },
  "payments": [
    {
      "payment_id": "P-50001",
      "order_id": "VFO-10001",
      "user_id": "U-90001",
      "external_payment_id": "EXT-777",
      "status": "{\"state\": \"succeeded\"}",
      "created_at": "2026-10-01T09:31:45Z",
      "updated_at": "2026-10-01T09:31:55Z",
      "payment_system": "bank-card",
      "payment_method": "bank-card",
      "payment_type": "fuel-purchase",
      "purpose": "usual",
      "amount": 1500.00,
      "actual_amount": 1495.50,
      "card_binding_id": "CB-123",
      "card_binding_type": "bank-card",
      "sbp_subscription_id": null
    },
    {
      "payment_id": "P-50002",
      "order_id": "VFO-10001",
      "user_id": "U-90001",
      "external_payment_id": "EXT-778",
      "status": "{\"state\": \"failed\"}",
      "created_at": "2026-10-01T09:31:45Z",
      "updated_at": "2026-10-01T09:31:55Z",
      "payment_system": "bank-card",
      "payment_method": "bank-card",
      "payment_type": "fuel-purchase",
      "purpose": "usual",
      "amount": 1500.00,
      "actual_amount": null,
      "card_binding_id": "CB-123",
      "card_binding_type": "bank-card",
      "sbp_subscription_id": null
    }
  ],
  "fueling_orders": [
    {
      "fueling_id": "F-10001",
      "volume": 28.5,
      "price": 52.49,
      "fuel_type": "AI-95",
      "station_id": "VS-30001",
      "pump_id": "PUMP-7",
      "created_at": "2026-10-01T09:30:00Z",
      "data": "{\"checkId\": \"CHK-42\"}",
      "fuel_description": "AI-95 premium",
      "brand": "FuelCorp"
    }
  ],
  "fueling_events": [
    {
      "fueling_id": "F-10001",
      "created_at": "2026-10-01T09:29:58Z",
      "status": "started",
      "data": "{\"pump\": \"PUMP-7\"}",
      "event_offset": 1,
      "brand": "FuelCorp"
    },
    {
      "fueling_id": "F-10001",
      "created_at": "2026-10-01T09:30:05Z",
      "status": "completed",
      "data": "{\"liters\": 28.5}",
      "event_offset": 2,
      "brand": "FuelCorp"
    }
  ],
  "vendor_fueling_orders": [
    {
      "fueling_id": "F-10001",
      "fuel_type": "AI-95",
      "quantity": 28.5,
      "total_sum": 1495.50,
      "status": "PAID",
      "gas_pump_number": 7,
      "vendor_gas_station_id": "VS-30001",
      "vendor_fuel_price": 52.49,
      "email": "driver@example.com",
      "phone": "+7 900 000-00-01",
      "date_create": "2026-10-01T09:31:45Z",
      "created_at": "2026-10-01T09:31:45Z"
    }
  ]
}
```

**Aggregation rules** (normative):

1. **Timestamp normalization (D-15):** every epoch column (numeric/bigint) is epoch **milliseconds** UTC and is emitted as an ISO-8601 UTC string (`Instant.ofEpochMilli`); the single `timestamptz` column (`gas_stations.updated_at`) is emitted as ISO-8601 UTC as well. Text date columns (`vendor_transaction_date`, `date_create`) pass through verbatim.
2. **JSON columns as text:** `jsonb` columns are emitted as their JSON text (string), per source spec §4. `null` stays `null`.
3. **Lists:** every list query carries `LIMIT 100` and an explicit `ORDER BY` (payments/fueling_orders/vendor_fueling_orders by `created_at DESC`, fueling_events by `event_offset ASC`).
4. **Not found:** unknown `fueling_id` → `{"fueling": null, "gas_station": null, "payments": [], "fueling_orders": [], "fueling_events": [], "vendor_fueling_orders": []}` — the model explains the absence in `summary`; the HTTP status stays 200.

### Pinned LLM client surface (G-7, D-8)

`infrastructure.llm` exposes exactly this seam; `service` passes tools in (never empty — spec-critical invariant, enforced by construction):

```kotlin
// infra.llm — bound with named tags `local` (Ollama) and `remote` (DeepSeek)
interface LlmClient {
    suspend fun run(
        systemPrompt: String,
        history: List<ChatMessage>,   // {role, body, timestamp}, last N from DialogHistory (D-1, D-11)
        userMessage: String,
        tools: List<Tool<*, *>>,      // ai.koog.agents.core.tools.Tool — assembled in service (D-8)
    ): String                        // model's final summary text
}
```

`GetFuelingInfoTool` implements `SimpleTool<GetFuelingInfoArgs>` with a `@Serializable` args class (`@property:LLMDescription("The fueling identifier to look up.") val fueling_id: String`); the descriptor in CONTRACT-TOOL-001 above and the Koog-generated schema describe the same input. Koog is pinned in D-16; Koog types never cross the `infra.llm → service` boundary in the other direction (infra references only Koog + its own config).

---

## 4. Data access contracts

**Query chain of `GetFuelingInfoTool`** (Exposed, `readOnlyMode=always` server-side on all four pools, D-2):

| Step | DB | Query |
|---|---|---|
| 1 | `fueling` | `fuelings WHERE fueling_id = ? ORDER BY created_at DESC LIMIT 1` — index lookup on the PK (D-3) |
| 2 | `gas_station` | `gas_stations WHERE gas_station_id = <step-1.gas_station_id> LIMIT 1` |
| 3 | `payment` | `payments WHERE order_id = <step-1.vendor_fueling_order_id> ORDER BY created_at DESC LIMIT 100` (D-14) |
| 4 | `vendors` | `fueling_orders WHERE fueling_id = ? LIMIT 100`, `fueling_events WHERE fueling_id = ? ORDER BY event_offset ASC LIMIT 100`, `vendor_fueling_orders WHERE fueling_id = ? ORDER BY created_at DESC LIMIT 100` |

**Join key (G-2, D-14):** `payments.order_id = fuelings.vendor_fueling_order_id`. Rationale: payments settle vendor-side orders, and `fuelings` stores exactly that vendor reference; `payments.order_id` is indexed (`payments_order_id_idx`). If stage data later shows `payments.order_id` matching `fuelings.fueling_id` instead, only the one join constant in the tool changes — no schema or contract impact.

**Read-only guarantees (normative, source spec §4):** `readOnlyMode=always` server-side; no DDL/DML/migrations from app code; Exposed objects are structure only — `SchemaUtils.create` forbidden in main code; raw column-maps returned, jsonb as text; every list query has `LIMIT` + explicit ordering; the LLM receives only the DDL-derived table structure in the system prompt/tool description — never DB access or credentials.

**Schema/seed files:** `docs/schema.sql` (local Docker only, non-partitioned — G-3/G-4/G-5, deviations in D-17) and `docs/seed.sql` (canonical chain `F-10001`, D-12). Both were applied and validated against `postgres:15-alpine` on 2026-10-12: schema creates all 13 tables/sequences/indexes across the four databases; the seed chain resolves end-to-end through the exact queries above and is idempotent (double-run verified). Applied only to the local container; never migrations.

---

## 5. Decision log

Decisions D-1…D-13 carried from the approved design; D-3 and D-12 updated, D-14…D-17 added in this phase.

| ID | Decision | Status |
|---|---|---|
| D-1 | Dialog history = write-through journal: in-memory cache is the read source; JSONL file appended per turn; both cleared on restart (truncate at startup). | unchanged |
| D-2 | Local DB = Docker `postgres:15-alpine` (port 25432, 4 DBs) + `docs/schema.sql` + `docs/seed.sql`; fail-fast at startup (config completeness, DB reachability, non-empty tool set). | unchanged |
| D-3 | Fueling lookup = **standard index lookup** on `fueling_id` (PK b-tree), `WHERE fueling_id = ? ORDER BY created_at DESC LIMIT 1`; **no table partitioning** in the local schema, no date hint required. *(Updated this phase: was "partition scan" — see G-3.)* | **updated** |
| D-4 | Frontend = mockup-parity profile: Vite 8, React 19, TS 5.7 strict, Tailwind v4, pnpm, Prettier, ESLint flat config, Vitest + RTL. | unchanged |
| D-5 | Metrics = CTO ledger + cost deltas; findings parsed from `reviews/*.md` at measurement phase (not hand-logged). | unchanged |
| D-6 | Summary language = echo the user's latest message language (system-prompt instruction). | unchanged |
| D-7 | Chat model = request/response, one `POST /chat` per message; no streaming, no polling. | unchanged |
| D-8 | Koog agent assembled in service; tool passed to `LlmClient.run` as a tool-set parameter (never empty); infra.llm stays business-free (see pinned surface in §3). | unchanged |
| D-9 | fueling_id branching: explicit field → injected into system prompt as context, no LLM extraction; absent → LLM extracts from message or answers without the tool. | unchanged |
| D-10 | Default provider = `local` (mockup parity). | unchanged |
| D-11 | Dialog-history defaults: `HISTORY_LIMIT` = 20 messages, `DIALOG_HISTORY_FILE` = `data/dialog-history.jsonl`. | unchanged |
| D-12 | Seed = **one canonical chain `F-10001`** in `docs/seed.sql` (happy path with two payments and two events, exercising the full aggregation). The design's extra scenarios F-10002/F-10003 are deferred to implementation-phase test fixtures. *(Updated this phase per owner directive.)* | **updated** |
| D-13 | Frontend env contract: `VITE_API_URL`, default `http://localhost:8080`, documented in the tracked `.env.example`. | unchanged |
| D-14 | Payment join key: `payments.order_id = fuelings.vendor_fueling_order_id` (G-2). Rationale and fallback in §4. | **new** |
| D-15 | Timestamp normalization: all numeric/bigint epoch columns are epoch-ms UTC; the only timestamptz is `gas_stations.updated_at`; the aggregated JSON emits ISO-8601 UTC strings; text date columns pass through verbatim (G-5). | **new** |
| D-16 | Koog pinned to **1.3.0** — `ai.koog:koog-agents-jvm:1.3.0` (latest stable on Maven Central, 2026-09-23). Tool surface: `Tool<TArgs, TResult>(argsType, resultType, name, description, jsonSchemaConfig)` with `suspend fun execute(args): TResult`; text results via `SimpleTool<TArgs>`; registry via `ToolRegistryBuilder().tool(…)/tools(list).build()`. Clients: `DeepSeekLLMClient(apiKey, baseUrl = "https://api.deepseek.com")` and `OllamaClient(baseUrl = "http://localhost:11434")` (both take a `baseUrl` constructor parameter). Requirements: JDK 17+, Kotlin 2.x (G-7). | **new** |
| D-17 | Local schema deviations from production DDL (G-3/G-4/G-5): no partitioning (`fuelings` PK = `fueling_id`), `OWNER TO` statements dropped (roles absent locally), PostGIS `coordinates_idx` dropped (extension absent in `postgres:15-alpine`; tool never queries by geography). `docs/schema.sql` is local-only and is never a migration. | **new** |

---

## 6. Resolved spec gaps

All gaps from design §11, resolved and frozen in this phase:

| Gap | Resolution |
|---|---|
| **G-1** UI source of truth | The Figma Make export (`/Users/Slava/Desktop/Scandinavian Style Chat Design/src/App.tsx` + `src/index.css`) is the single source of truth for the UI. Stated explicitly in §1 of this spec. |
| **G-2** Payment join key | `payments.order_id = fuelings.vendor_fueling_order_id` — pinned as D-14, rationale and fallback in §4, exercised by `docs/seed.sql` (payments reference `VFO-10001`, not `F-10001`, proving the join is not on `fueling_id`). |
| **G-3** Missing partition DDL | **No partitioning in the local schema.** `fuelings` is a standard table with `fueling_id` as the primary key (updated D-3); the lookup is a plain index scan. Production partition definitions were absent from `ddl-source.md` and add nothing to the local read paths. |
| **G-4** Undefined sequences | `payments_payment_id_seq`, `gas_stations_gas_station_id_seq`, `fueling_events_event_offset_seq` are created explicitly in `docs/schema.sql` (per-database), each validated to advance. |
| **G-5** Mixed timestamp types | Normalized by D-15: all epoch columns are epoch-ms UTC (`fuelings.created_at` numeric, vendors/payments bigint), `gas_stations.updated_at` is timestamptz, `date_create` is a verbatim vendor string; the tool emits ISO-8601 UTC. Documented in `docs/schema.sql` header comments and §3/§4. |
| **G-6** Response shape | Frozen as CONTRACT-API-002: `{summary, provider, elapsed_ms}` — exactly three fields on 200. |
| **G-7** Koog API surface | Pinned by D-16: Koog **1.3.0** (`ai.koog:koog-agents-jvm:1.3.0`), exact `Tool`/`SimpleTool`/`ToolRegistryBuilder` signatures and `DeepSeekLLMClient`/`OllamaClient` constructors (verified against the Koog sources at pin time), wrapped by the `LlmClient` seam in §3 that D-8 assumes. |

---

## 7. Metrics protocol

One CTO ledger per repo per branch (D-5): `metrics/first.md` and `metrics/second.md` in **both** repos; branch scoping by git. Each file contains exactly one table:

```markdown
# Metrics — first

| Task ID | Start Time | End Time | Token Delta | Handbacks | Findings Count |
|---|---|---|---|---|---|
| T1 | 2026-10-13T10:00:00Z | 2026-10-13T10:23:45Z | 41,234 | 1 | 3 |
| T2 | 2026-10-13T10:25:00Z |  |  | 0 |  |
```

Column rules:

- **Task ID** — the task's `docs/tasks/T<n>.md` id.
- **Start Time** — CTO dispatch timestamp, ISO-8601 UTC. One row per task; the CTO appends the row at dispatch.
- **End Time** — timestamp of the `T<n> done | verified: ok` verdict, ISO-8601 UTC; empty while the task is in flight.
- **Token Delta** — cost counter read after the agent run minus read before, in the CTO session (the design's token metric; includes handback re-runs).
- **Handbacks** — number of returns to the agent with notes; CTO increments on each handback, `0` at dispatch.
- **Findings Count** — **not hand-logged**: filled during the measurement phase by parsing `reviews/T<n>-<reviewer>.md` (D-5). Left empty while the task runs.

The measurement phase aggregates both repos' ledgers + review files into **`comparison-report.md` in the backend repo root** (the docs hub): wall-clock and token tables for backend/frontend × first/second, handback counts, findings per gate, and the spec's required conclusions (cost/quality verdicts, per-gate comparison, when to use which approach).

---

## 8. README contract

Exact headings and required content. Files are created in Phase 4 (foundation), refreshed at the end of each implementation branch. Any heading marked required must appear with that exact text, in this order.

### Backend `README.md`

```markdown
# Fueling Chat — Backend
## Purpose          — fueling chat + the first/second experiment; what the backend does.
## Stack            — Kotlin, Ktor (netty, content-negotiation, kotlinx-json), Koog 1.3.0 (D-16),
                      Kodein DI, Exposed + HikariCP, kotlinx-serialization, SLF4J + logback, JUnit 5 / MockK.
## Prerequisites    — JDK 17+, Docker (for the local DBs), optionally Ollama for the local provider.
## Setup            — cp .env.example .env → docker compose up -d → apply docs/schema.sql + docs/seed.sql
                      → ./gradlew run.
## API contract     — POST /chat per CONTRACT-API-001 / CONTRACT-API-002 (by reference, no restatement).
## Provider configuration — local (Ollama) / remote (DeepSeek) env keys and defaults.
## Dialog history semantics — journal + cache cleared on restart; HISTORY_LIMIT; DIALOG_HISTORY_FILE.
## Read-only guarantees — readOnlyMode=always, no DDL/DML from code, DDL-first tool description.
## Branch and artifact layout — reviews/, metrics/, comparison-report.md; first/second strategy.
```

### Frontend `README.md`

```markdown
# Fueling Chat — Frontend
## Purpose          — React client for the fueling chat, mockup-parity with the Figma Make export.
## Stack            — Vite 8, React 19, TypeScript 5.7 strict, Tailwind CSS v4, pnpm, Prettier,
                      ESLint flat config, Vitest + @testing-library/react.
## Prerequisites    — pnpm.
## Setup            — pnpm install (then dev/build/test/lint as below).
## Scripts          — dev, build, test, lint, format (prettier --write .).
## Environment      — VITE_API_URL: backend base URL, default http://localhost:8080 (D-13);
                      documented in the tracked .env.example.
```

---

## 9. Artifacts of this phase and exit criteria

Produced here (all committed):

- `docs/schema.sql` — executable non-partitioned local schema, sequences, normalization comments (G-3/G-4/G-5); validated in `postgres:15-alpine`.
- `docs/seed.sql` — canonical idempotent chain `F-10001` across all four DBs; validated and double-run.
- `docs/project-specification.md` — this file: contracts, decision log, resolved gaps, metrics protocol, README contract.
- `.env.example` (backend + frontend repos) — all required keys with placeholders (backend §10 of the design; frontend D-13).

Exit criteria for Phase 2: all seven gaps resolved and frozen; every contract carries an ID; implementation tasks (Phase 3) can reference contracts by ID only. Hard gate respected: no Kotlin/React/test implementation code was written in this phase.
