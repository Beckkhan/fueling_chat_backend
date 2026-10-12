-- =====================================================================
-- fueling_chat — local schema (D-2, D-17)
--
-- Executable DDL for the LOCAL Docker Postgres only (postgres:15-alpine,
-- one instance, four databases: fueling, gas_station, payment, vendors).
-- Derived from docs/ddl-source.md (informational source, never executed).
-- Never treated as a migration; applied only to the local container.
--
-- Invocation (single psql run, `\c` switches databases in place):
--   psql -h localhost -p 25432 -U postgres -v ON_ERROR_STOP=1 -f docs/schema.sql
--
-- DELIBERATE DEVIATIONS from production DDL (see docs/project-specification.md,
-- resolved gaps G-3/G-4/G-5 and decision log D-3/D-17):
--   1. NO TABLE PARTITIONING. `fuelings` is a standard table with
--      `fueling_id` as the primary key; the fueling lookup is a plain
--      index lookup (updated D-3). Production partition definitions were
--      absent from ddl-source.md (G-3) and add nothing to the local
--      read paths the tool exercises.
--   2. The three referenced sequences are created explicitly (G-4).
--   3. `ALTER TABLE ... OWNER TO` statements are dropped: the roles
--      (fueling, gas_station, payment, vendors) do not exist in the local
--      container and the local superuser owns everything.
--   4. The PostGIS index (`coordinates_idx`) is dropped: the postgis
--      extension is not present in postgres:15-alpine and the tool never
--      queries by geography.
--
-- TIMESTAMP / EPOCH NORMALIZATION (G-5, D-15) — the contract for the whole
-- project; every epoch column below is EPOCH MILLISECONDS UTC:
--   fuelings.created_at / updated_at / finished_at   NUMERIC  epoch-ms UTC
--   payments.created_at / updated_at                 BIGINT   epoch-ms UTC
--   fueling_orders.created_at                        BIGINT   epoch-ms UTC
--   fueling_events.created_at                        BIGINT   epoch-ms UTC
--   vendor_fueling_orders.created_at                 NUMERIC  epoch-ms UTC
--   vendor_fueling_orders.date_create                TEXT     vendor string, passed through verbatim
--   gas_stations.updated_at                          TIMESTAMPTZ
-- GetFuelingInfoTool aggregates all of them into ISO-8601 UTC strings
-- (Instant.ofEpochMilli / OffsetDateTime.toInstant respectively).
-- =====================================================================

-- =====================================================================
-- Database `fueling`
-- =====================================================================

\connect fueling

create table public.fuelings
(
    -- numeric epoch-ms UTC (G-5); primary key on fueling_id alone —
    -- local schema is NOT partitioned (G-3, updated D-3)
    fueling_id              text                         not null
        constraint fuelings_pkey primary key,
    vendor_fueling_order_id text,
    user_id                 text                         not null,
    status                  text                         not null,
    amount                  numeric                      not null,
    fuel_type               text                         not null,
    gas_station_id          text                         not null,
    gas_pump_id             text                         not null,
    refueling_gun_id        text                         not null,
    fuel_reservation_key    text                         not null,
    created_at              numeric                      not null, -- epoch-ms UTC
    updated_at              numeric                      not null, -- epoch-ms UTC
    actual_amount           numeric,
    vendor_transaction_date text,
    failed_reason           text,
    vendor_fuel_price       numeric,
    fueled_orders           jsonb,
    discount_fuel_price     numeric,
    fueling_type            text default 'regular'::text not null,
    extra                   jsonb,
    fueling_payment_type    text default 'prepayment'::text not null,
    finished_at             numeric -- epoch-ms UTC
);

create index fuelings_new_created_at_idx
    on public.fuelings (created_at);

create index fuelings_new_user_id_idx
    on public.fuelings (user_id);

create index fuelings_new_partner_fueling_id_idx
    on public.fuelings ((extra ->> 'partnerFuelingId'::text))
    where (fueling_type = 'outer-partner'::text);

create index fuelings_new_non_final_statuses_idx
    on public.fuelings (status)
    where (status <> ALL (ARRAY ['completed'::text, 'failed'::text, 'cancelled'::text]));

create index fuelings_new_contract_id_idx
    on public.fuelings ((extra ->> 'contractId'::text))
    where (fueling_type = ANY (ARRAY ['outer-partner'::text, 'mirror-api-partner'::text, 'webview'::text]));

create index fuelings_new_company_idx
    on public.fuelings ((extra ->> 'companyId'::text))
    where ((extra ->> 'companyId'::text) IS NOT NULL);

create index fuelings_new_agent_id_idx
    on public.fuelings ((extra ->> 'agentId'::text))
    where ((extra ->> 'agentId'::text) IS NOT NULL);

create index fuelings_new_updated_at_idx
    on public.fuelings (updated_at);

create index fuelings_new_finished_at_idx
    on public.fuelings (finished_at);

-- (fuelings_new_id_key_idx dropped: the PK above already provides the
--  b-tree index on fueling_id used by the lookup, updated D-3)

-- =====================================================================
-- Database `gas_station`
-- =====================================================================

\connect gas_station

-- G-4: sequence referenced by gas_stations.gas_station_id default
create sequence public.gas_stations_gas_station_id_seq;

create table public.gas_stations
(
    gas_station_id        text                     default (nextval('gas_stations_gas_station_id_seq'::regclass))::text not null
        primary key,
    brand                 text                                                                                          not null,
    address               text                                                                                          not null,
    vendor_gas_station_id text                                                                                          not null,
    latitude              double precision                                                                              not null,
    longitude             double precision                                                                              not null,
    activated             boolean                                                                                       not null,
    deleted               boolean                                                                                       not null,
    fueling_limits        jsonb,
    gas_station_name      text                                                                                          not null,
    manually_disabled     boolean                                                                                       not null,
    updated_at            timestamp with time zone default now()                                                        not null, -- timestamptz (G-5)
    gas_station_type      text                     default 'UNDEFINED'::text                                            not null,
    extra                 jsonb                    default '{}'::jsonb                                                  not null,
    location_id           text                     default 'msk_mo'::text                                               not null,
    payment_mode          text                     default 'PREPAID'::text                                              not null,
    subnet                text,
    owner                 text,
    public_brand          text,
    take_off_mode         text                     default 'after'::text                                                not null,
    available_services    jsonb                    default '{}'::jsonb                                                  not null
);

-- coordinates_idx (gist on geography) dropped: postgis extension is not
-- available in the local container (D-17); the tool never queries by
-- geography.

create index tracks_locations_gin_idx
    on public.gas_stations using gin (extra);

create index updated_at_idx
    on public.gas_stations (updated_at);

create index vendor_gas_station_id_idx
    on public.gas_stations (vendor_gas_station_id);

-- =====================================================================
-- Database `payment`
-- =====================================================================

\connect payment

-- G-4: sequence referenced by payments.payment_id default
create sequence public.payments_payment_id_seq;

create table public.payments
(
    payment_id          text        default (nextval('payments_payment_id_seq'::regclass))::text not null
        primary key,
    -- join key: payments.order_id = fuelings.vendor_fueling_order_id (G-2, D-14)
    order_id            text                                                                     not null,
    user_id             text                                                                     not null,
    external_payment_id text,
    status              jsonb,
    created_at          bigint, -- epoch-ms UTC
    updated_at          bigint, -- epoch-ms UTC
    payment_system      varchar(64) default 'bank-card'::character varying                       not null,
    payment_method      varchar(64) default 'bank-card'::character varying                       not null,
    payment_type        text        default 'fuel-purchase'::text                                not null,
    purpose             text        default 'usual'::text                                        not null,
    amount              numeric,
    actual_amount       numeric,
    card_binding_id     text,
    card_binding_type   text,
    sbp_subscription_id text
);

create index payments_order_id_idx
    on public.payments (order_id);

create index payments_user_id_idx
    on public.payments (user_id);

create index payments_updated_at_idx
    on public.payments (updated_at);

create index payment_external_payment_id_idx
    on public.payments (external_payment_id);

-- =====================================================================
-- Database `vendors`
-- =====================================================================

\connect vendors

-- G-4: sequence referenced by fueling_events.event_offset default
create sequence public.fueling_events_event_offset_seq;

create table public.fueling_events
(
    fueling_id   text                                                                not null,
    created_at   bigint                                                              not null, -- epoch-ms UTC
    status       text                                                                not null,
    data         jsonb                                                               not null,
    event_offset bigint default nextval('fueling_events_event_offset_seq'::regclass) not null
        primary key,
    brand        text                                                                not null
);

create index fueling_events_fueling_id_hash
    on public.fueling_events using hash (fueling_id);

create index fueling_events_created_at_idx
    on public.fueling_events (created_at desc);

-------------------------------------------------------------------------

create table public.fueling_orders
(
    fueling_id       text    not null
        primary key,
    volume           numeric not null,
    price            numeric not null,
    fuel_type        text    not null,
    station_id       text    not null,
    pump_id          text    not null,
    created_at       bigint  not null, -- epoch-ms UTC
    data             jsonb   not null,
    fuel_description text,
    brand            text    not null
);

create index fueling_orders_created_at_idx
    on public.fueling_orders (created_at desc);

-------------------------------------------------------------------------

create table public.vendor_fueling_orders
(
    fueling_id            text    not null
        primary key,
    fuel_type             text    not null,
    quantity              numeric not null,
    total_sum             numeric not null,
    status                text    not null,
    gas_pump_number       numeric not null,
    vendor_gas_station_id text    not null,
    vendor_fuel_price     numeric not null,
    email                 text,
    phone                 text,
    date_create           text    not null, -- vendor date string, passed through verbatim (G-5)
    created_at            numeric not null -- epoch-ms UTC
);

create index vendor_fueling_orders_created_at_idx
    on public.vendor_fueling_orders (created_at desc);
