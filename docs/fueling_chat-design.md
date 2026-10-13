# Fueling Chat — Design

Phase 1 of 6: DESIGN. Status: **approved** (2026-10-12).
Inputs: `docs/spec-source.md`, `docs/ddl-source.md` (informational, never executed), UI mockup folder `/Users/Slava/Desktop/Scandinavian Style Chat Design` (Figma Make export — see G-1).

---

## 1. Purpose and scope

Production-grade fullstack chat (React + Kotlin/Ktor) with AI orchestration over fueling data from four Postgres databases. The user asks about a fueling by ID; the backend collects data from all four DBs through a tool and returns an LLM summary. The project is implemented **twice, in parallel branches** (`first` vs `second`), with time/token/quality metrics compared at the end.

This design covers: layer architecture, Kodein modules, the Koog tool chain, provider routing, dialog history, frontend structure, local dev environment, branch strategy, review/metrics artifacts, and README plans. Hard gate: no implementation, scaffolding or specification writing until this design is approved (done) — the specification phase follows.

---

## 2. Architecture: layers and Kodein modules

**Layer diagram** (dependency direction; spec notation `config ← infrastructure ← service ← routes`):

```
routes ──────────────► service ──────────────► infrastructure ──────────────► config
POST /chat            ChatService              db (Exposed+Hikari ×4)         AppConfig
Ktor routing          ProviderRouter           llm (Koog clients ×2)         (.env, typed)
                      DialogHistory            logging (logback+masking)
                      GetFuelingInfoTool
```

- **config** — typed `AppConfig` read from `.env` (DB URLs, credentials, provider endpoints/models, journal path, `HISTORY_LIMIT`, timeouts). Missing/empty key → fail-fast crash at startup.
- **infrastructure** — `db`: one Exposed `Database` per production DB, HikariCP pools, `readOnlyMode=always` enforced **server-side**; `llm`: two Koog-backed clients (named bindings `local`/`remote`); `logging`: logback with mask-before-write (`DEEPSEEK_API_KEY`, `DB_PASSWORD`, `Authorization` incl. Bearer, JSON fields `api_key/password/secret/token`).
- **service** — `ChatService` (orchestrates one turn), `ProviderRouter`, `DialogHistory` (write-through journal, §4), `GetFuelingInfoTool` (tool handler; SELECTs across the 4 DBs, returns aggregated JSON).
- **routes** — single Ktor route group: `POST /chat`. Parse → delegate → respond. No logic here.
- **Application** — builds `AppModule`, runs fail-fast checks, starts Netty. No business logic, SQL, or LLM calls.

**Koog agent assembly (D-8):** the agent/run is assembled in the **service** layer. `ChatService` constructs the tool set from `GetFuelingInfoTool` and passes it into `LlmClient.run(systemPrompt, history, toolSet, …)` as a parameter. `infrastructure.llm` (module `client`) is a thin Koog-backed client referencing only Koog types and its own config — it never references service classes, so no infrastructure → service dependency exists. The alternative (tool handler in infrastructure) was rejected: the handler embeds business aggregation logic (which DBs, what JSON shape), which belongs to service. Exact Koog run/tool API surface pinned in the specification phase (G-7); the assembly point and parameter passing stay as stated either way.

**Kodein module list** (all `bind<T>() with singleton { }`; multiple implementations via named tags):

| Module | Bindings |
|---|---|
| `config` | `AppConfig` (singleton) |
| `db` | 4 × `Database` named `fueling`, `gas_station`, `payment`, `vendors` (one Hikari pool each) |
| `client` | `LlmClient` named `local` (Ollama, OpenAI-compatible endpoint) and `remote` (DeepSeek) |
| `logging` | masked logger factory / logback configuration |
| `service` | `ChatService`, `ProviderRouter`, `DialogHistory`, `GetFuelingInfoTool` |
| `AppModule` | composition class referenced from `Application` — DI graph lives outside the entry point |

Packages follow layers (`config / infra.db / infra.llm / infra.logging / service / routes`); each module owns non-overlapping files.

---

## 3. Request flow: Koog tool chain

One turn through the spec's R9 chain (`client → backend → LLM → tool → backend → DB → aggregated JSON → summary`):

1. Frontend sends `POST /chat {prompt, fueling_id?, provider?}`.
2. Route parses → `ChatService`. Empty prompt → 400; invalid `provider` → 400.
3. `ProviderRouter` selects the Koog client by the `provider` field: `local` → Ollama (OpenAI-compatible endpoint, base URL from `.env`), `remote` → DeepSeek. **Default when absent: `local`** (D-10, mockup pre-selects "Local model").
4. `DialogHistory` supplies recent context (last N messages, §4).
5. `ChatService` assembles the Koog run: system prompt (reply in the language of the user's latest message — D-6; answer fueling questions only via `get_fueling_info`; table structure per the DDL) + context + new prompt + **tool set `[get_fueling_info]` — never empty** (spec-critical invariant, enforced by construction).
6. **fueling_id branching (D-9):** if `fueling_id` is present in the POST body, `ChatService` injects it into the system prompt as explicit context ("attached fueling_id: X — use exactly this value for `get_fueling_info`"); the LLM does **not** extract it from the message text. If absent, the LLM may extract it from the user's message, or answer directly without invoking the tool when the question needs no lookup. The tool set stays non-empty either way — the invariant is about the request, not about forcing a call.
7. `GetFuelingInfoTool` executes on the backend through Exposed, in order:
   - `fueling.fuelings`: `WHERE fueling_id = ? ORDER BY created_at DESC LIMIT 1` — **partition scan with LIMIT 1** (D-3); the row's `gas_station_id` and `vendor_fueling_order_id` drive the next queries;
   - `vendors.fueling_orders` / `fueling_events` / `vendor_fueling_orders` by `fueling_id`, `LIMIT` + explicit `ORDER BY`;
   - `gas_station.gas_stations` by `gas_station_id`;
   - `payment.payments` by the fueling's order id — **exact join key pinned in the specification phase** (G-2).
8. The handler aggregates raw column-maps into one JSON (fueling + station + payments + vendor rows; JSON columns as text) and returns it to the model as the tool result.
9. The model writes the summary in the user's language (D-6). Backend appends both messages to the journal (§4) and responds **`{summary, provider, elapsed_ms}`** (G-6) — provider and response time per spec §2.
10. **Errors:** provider unreachable → 502 (masked); DB failure → 500 (masked); tool failure is returned to the model as the tool result so it can explain the failure in the summary — if the loop itself breaks, 502. Credentials, SQL and secrets never appear in responses or logs.

**Read-only guarantees:** `readOnlyMode=always` set server-side on all four pools; Exposed objects define structure only — no `SchemaUtils.create` in main code; every list query carries `LIMIT` and explicit ordering; the LLM receives only the DDL-derived table structure in the system prompt/tool description, never DB access or credentials.

---

## 4. Dialog history storage (write-through journal, D-1)

Two components, both cleared on restart:

**In-memory cache — the read source.** A message list (`{role, body, timestamp}`) owned by `DialogHistory`. It is the only source used to build LLM context: `ChatService` sends the last N messages (`HISTORY_LIMIT`, default 20, D-11) plus the new prompt. The file is never read on the hot path.

**File journal — the write-through copy.** JSON Lines (`.jsonl`, one message per line, kotlinx-serialization), path from `.env` (`DIALOG_HISTORY_FILE`, default `data/dialog-history.jsonl`, D-11).

Lifecycle:

1. **At startup:** the file is truncated and the cache starts empty — "cleared on restart" is observable and testable. If the previous run crashed, its transcript is still on disk until this truncation — the crash-debuggability window.
2. **Per turn:** after the summary is produced, the user message and assistant message are appended as two lines (append-only; writes serialized via a `Mutex` — Ktor serves concurrent requests).
3. **Mid-run recovery:** if the cache were lost while the process is alive, it can be rebuilt from the journal — the only recovery path, deliberately not used in normal operation.
4. **Restart:** cache gone, journal truncated → LLM context is empty, so follow-up questions cannot reference pre-restart context (spec acceptance criterion). The frontend's on-screen history is client-side state and may outlive a backend restart until page reload — expected behavior, not a bug: the backend journal is the context of record.

---

## 5. Frontend (mockup-parity, D-4 + D-7)

**Stack (pinned):** Vite 8 · React 19 · TypeScript 5.7 `strict: true` · Tailwind CSS v4 via `@tailwindcss/vite` (no config file) · **pnpm** (package manager) · **Prettier** (formatting, `"format": "prettier --write ."`, with `eslint-config-prettier`) · ESLint flat config with typescript-eslint + react-hooks (linting) · Vitest + @testing-library/react (tests). The export's `oxfmt` script is replaced by Prettier.

**Parity strategy:** the Figma Make export (`App.tsx` + `index.css`) is the working reference — its Tailwind utilities, palette tokens (`#17302e`, `#e8e4dc`, `#fbfaf7`, `#d66b50`, `#4e7667`) and font theme (DM Sans body, Manrope display) are copied verbatim, guaranteeing pixel parity. The single `App.tsx` is decomposed without touching markup/styles:

```
src/
  main.tsx           entry, mounts App
  index.css          Tailwind v4 import + @theme tokens + base styles (verbatim from export)
  App.tsx            layout shell: page background, centered card (header + list + composer)
  components/
    Header.tsx        logo block ("Fueling Chat" / "Private workspace") + ModelSwitcher
    ModelSwitcher.tsx dropdown button + menu: Local model "Private" / Remote model "Cloud"
    MessageList.tsx   scrollable area, auto-scrolls to bottom on new message
    Message.tsx       bubble: avatar, body, time; user = right-aligned dark-green, assistant = left white
    Composer.tsx      textarea (Enter sends, Shift+Enter newline), attach button (visual-only, as in the
                      mockup), coral send button disabled when empty, "Enter to send" hint, disclaimer
    Icon.tsx          shared inline SVG set (attach, check, chevron, send, spark, stop — stop stays unused)
  api/chat.ts        typed POST /chat client (fetch)
  types.ts           Message, ModelId, ChatRequest/ChatResponse
```

**State:** `App` owns `messages`, `activeModel` (default `local`, as pre-selected in the mockup) and composer text. On send: optimistic user bubble → `POST /chat {prompt, provider}` → assistant bubble from `summary`; on error, a muted error bubble. No global state library, no streaming, no job polling (D-7). The mockup's demo messages are not used — the conversation starts empty, mirroring the backend journal. Model ids map 1:1 to the `provider` field (`local`/`remote`).

**API contract (frontend view):** `POST /chat` `{prompt, fueling_id?, provider?}` → `200 {summary, provider, elapsed_ms}`; `400`/`500`/`502` with a masked message shown as the error bubble. Backend base URL from `VITE_API_URL` (default `http://localhost:8080`, D-13; documented in the tracked `.env.example`).

**Tests (Vitest + RTL):** renders header/switcher/composer per mockup; send posts correct payload and appends the summary; provider switch changes the `provider` field; failure renders the error bubble. `fetch` mocked.

---

## 6. Local dev environment (Docker + schema + seeds, D-2)

Stage Postgres (`postgres.stage.turboapp.ru:25432`) is unreachable today; local Docker is the dev database. When stage returns, only `.env` URL values change — no code changes.

**Composition:** `docker-compose.yml` in the backend repo — one `postgres:15-alpine` container exposing port **25432** (same as stage, so `.env` edits stay minimal), with four databases created inside the instance (`fueling`, `gas_station`, `payment`, `vendors`) via a mounted init script.

**Schema:** `docs/schema.sql` — executable local DDL derived from `docs/ddl-source.md`, validated against the Docker container **in the specification phase, not now** (hard gate respected). Never treated as a migration; applied only to the local container. It reproduces the `fuelings` monthly partitioning (2025-01 … 2027-03) so the LIMIT-1 partition scan (D-3) behaves like production (see G-3, G-4).

**Seeds:** committed `docs/seed.sql` (created in the foundation phase, shape defined here) — **three scenarios** (D-12), small, deterministic, idempotent:
- `F-10001` happy path: one fueling (known id, `created_at` in a known partition month), its gas station, one payment, one row in each vendors table;
- `F-10002` multiple payments + multiple `fueling_events`;
- `F-10003` a fueling with no payment rows — exercises null/empty-list handling in the aggregation.

**Fail-fast at startup:** `AppConfig` completeness (every required `.env` key present and non-empty); reachability of all four local DBs; presence of the tool set (`get_fueling_info.json`). Any failure crashes at boot with a clear message — never at first request. `.env.example` documents every required key with placeholders (the only committed env file; `.env` git-ignored in both repos).

**Dev workflow:** `docker compose up -d` → apply `docs/schema.sql` + `docs/seed.sql` → `./gradlew run` → frontend `pnpm dev`.

---

## 7. Branch strategy

```
main ── common foundation (scaffold, DI, schema, base contracts)
    ├── first        (backend + frontend)
    └── second       (backend + frontend)
```

Both repos follow the same shape: `main` accumulates the design doc and the common foundation (committed in the foundation phase); `first` and `second` fork from the foundation commit — never from each other — and develop backend + frontend in parallel. Foundation work lands on `main` first, so both branches share an identical starting point.

---

## 8. Review and metrics artifacts

**Review gates** (4 sequential per task: correctness → simplicity → git → code-style). Each reviewer writes `reviews/T<n>-<reviewer>.md` (e.g. `T1-correctness-reviewer.md`). The `reviews/` directory exists in **both repos**; branch scoping is provided by git — each branch's review files are committed on that branch, so `first`'s and `second`'s verdicts never mix.

**Metrics protocol (D-5):** per repo, per branch, one CTO ledger — `metrics/first.md`, `metrics/second.md`. Each task row: start timestamp, token delta (cost counter read before/after the agent run in the CTO session), handback count (incremented by the CTO on each return-to-agent), `verified: ok` timestamp. Findings are **not** hand-logged: the measurement phase counts them by parsing `reviews/*.md`. After both branches finish, the measurement phase aggregates ledgers + review files into **`comparison-report.md` in the backend repo root** (the docs hub): wall-clock and token tables for backend/frontend × first/second, handback counts, findings per gate, and the spec's required conclusions (cost/quality verdicts, per-gate comparison, when to use which approach).

---

## 9. README plan

Content contract fixed in the **specification phase**, files created in the **foundation phase**, refreshed at the **end of each implementation branch**.

- **Backend `README.md`:** purpose (fueling chat + the first/second experiment), stack, prerequisites (JDK, Docker, optionally Ollama for the local provider), setup flow (`.env` from `.env.example`, `docker compose up`, schema + seeds, `./gradlew run`), `POST /chat` contract, provider configuration, dialog-history semantics (journal cleared on restart), read-only guarantees, branch/artifact layout (reviews, metrics, comparison-report).
- **Frontend `README.md`:** purpose, stack (Vite/React 19/TS strict/Tailwind v4/Prettier/ESLint/Vitest, pnpm), `pnpm install` / `dev` / `build` / `test` / `lint`, env contract — `VITE_API_URL` (backend base URL, default `http://localhost:8080`), added to the tracked `.env.example`.

---

## 10. Decision log

| ID | Decision |
|---|---|
| D-1 | Dialog history = write-through journal: cache is the read source; JSONL file appended per turn; both cleared on restart (truncate at startup). |
| D-2 | Local DB = Docker `postgres:15-alpine` (port 25432, 4 DBs) + `docs/schema.sql` + seeds; fail-fast at startup. |
| D-3 | Fueling lookup = partition scan, `WHERE fueling_id = ? ORDER BY created_at DESC LIMIT 1`; no date hint required. |
| D-4 | Frontend = mockup-parity profile: Vite 8, React 19, TS 5.7 strict, Tailwind v4, pnpm, Prettier, ESLint flat config, Vitest + RTL. |
| D-5 | Metrics = CTO ledger + cost deltas; findings parsed from `reviews/*.md` at measurement phase. |
| D-6 | Summary language = echo the user's latest message language (system-prompt instruction). |
| D-7 | Chat model = request/response, one `POST /chat` per message; no streaming, no polling. |
| D-8 | Koog agent assembled in service; tool passed to `LlmClient.run` as a tool-set parameter; infra.llm stays business-free. |
| D-9 | fueling_id branching: explicit field → injected into system prompt as context, no LLM extraction; absent → LLM extracts from message or answers without the tool. |
| D-10 | Default provider = `local` (mockup parity). |
| D-11 | Dialog-history defaults: `HISTORY_LIMIT` = 20 messages, `DIALOG_HISTORY_FILE` = `data/dialog-history.jsonl`. |
| D-12 | Seeds = three scenarios (`F-10001` happy path, `F-10002` multiple payments/events, `F-10003` no payments). |
| D-13 | Frontend env contract: `VITE_API_URL`, default `http://localhost:8080`. |

---

## 11. Spec gaps (recorded, not fixed inline — escalated to the owner)

- **G-1 UI source of truth:** the spec's named screenshot (`...at 17.11.27.png`) is absent from the folder; the only image (`...at 17.08.21.png`) could not be rendered by the design tooling. The Figma Make export code (`src/App.tsx` + `src/index.css`) was used as the authoritative UI source. Please confirm the export is acceptable as the single source of truth.
- **G-2 Payment join key:** `ddl-source.md` doesn't define how `payments` links to a fueling (`payments.order_id` vs `fuelings.vendor_fueling_order_id` vs `fuelings.fueling_id` vs `payments.user_id`). To be pinned during `docs/schema.sql` derivation in the specification phase.
- **G-3 `PARTITION BY` DDL missing:** `fuelings` is described as monthly-partitioned (2025-01…2027-03, PK `(fueling_id, created_at)`), but the informational DDL contains no partition definitions. `schema.sql` derivation must synthesize and locally validate them.
- **G-4 Undefined sequences:** `payments_payment_id_seq`, `gas_stations_gas_station_id_seq`, `fueling_events_event_offset_seq` are referenced as defaults but never defined; `schema.sql` must define them.
- **G-5 Mixed timestamp types:** `fuelings.created_at` numeric vs `gas_stations.updated_at` timestamptz vs vendors tables bigint — epoch unit (s vs ms) is unconfirmed from the DDL; aggregation must normalize consistently.
- **G-6 Response shape addition:** the spec gives the request contract only; this design fixes the response as `{summary, provider, elapsed_ms}` — to be frozen as a spec contract in the specification phase.
- **G-7 Koog API surface:** the spec names Koog without version or run/tool API. The specification phase must pin the framework version and the exact run-with-tools surface that D-8 assumes.

---

## 12. Out of scope (this phase)

Streaming responses, file uploads (the mockup's attach button stays visual-only), persistence across restarts, real stage-DB integration — all deferred; stage access will be restored later per the spec's note.
