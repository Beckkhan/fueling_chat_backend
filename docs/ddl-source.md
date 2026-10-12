-- =====================================================================
-- Database `payment`
-- =====================================================================

create table public.payments
(
    payment_id          text        default (nextval('payments_payment_id_seq'::regclass))::text not null
        primary key,
    order_id            text                                                                     not null,
    user_id             text                                                                     not null,
    external_payment_id text,
    status              jsonb,
    created_at          bigint,
    updated_at          bigint,
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

alter table public.payments
    owner to payment;

create index payments_order_id_idx
    on public.payments (order_id);

create index payments_user_id_idx
    on public.payments (user_id);

create index payments_updated_at_idx
    on public.payments (updated_at);

create index payment_external_payment_id_idx
    on public.payments (external_payment_id);

-- =====================================================================
-- Database `gas_station`
-- =====================================================================

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
        updated_at            timestamp with time zone default now()                                                        not null,
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

    alter table public.gas_stations
        owner to gas_station;

    create index coordinates_idx
        on public.gas_stations using gist (geography(st_makepoint(longitude, latitude)));

    create index tracks_locations_gin_idx
        on public.gas_stations using gin (extra);

    create index updated_at_idx
        on public.gas_stations (updated_at);

    create index vendor_gas_station_id_idx
        on public.gas_stations (vendor_gas_station_id);

-- =====================================================================
-- Database `fueling`
-- =====================================================================

create table public.fuelings
(
    fueling_id              text                            not null,
    vendor_fueling_order_id text,
    user_id                 text                            not null,
    status                  text                            not null,
    amount                  numeric                         not null,
    fuel_type               text                            not null,
    gas_station_id          text                            not null,
    gas_pump_id             text                            not null,
    refueling_gun_id        text                            not null,
    fuel_reservation_key    text                            not null,
    created_at              numeric                         not null,
    updated_at              numeric                         not null,
    actual_amount           numeric,
    vendor_transaction_date text,
    failed_reason           text,
    vendor_fuel_price       numeric,
    fueled_orders           jsonb,
    discount_fuel_price     numeric,
    fueling_type            text default 'regular'::text    not null,
    extra                   jsonb,
    fueling_payment_type    text default 'prepayment'::text not null,
    finished_at             numeric,
    constraint fuelings_new_pkey
        primary key (fueling_id, created_at)
);

alter table public.fuelings
    owner to fueling;

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

create index fuelings_new_id_key_idx
    on public.fuelings (fueling_id);

-- =====================================================================
-- Database `vendors`
-- =====================================================================

create table public.fueling_events
(
    fueling_id   text                                                                not null,
    created_at   bigint                                                              not null,
    status       text                                                                not null,
    data         jsonb                                                               not null,
    event_offset bigint default nextval('fueling_events_event_offset_seq'::regclass) not null
        primary key,
    brand        text                                                                not null
);

alter table public.fueling_events
    owner to vendors;

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
    created_at       bigint  not null,
    data             jsonb   not null,
    fuel_description text,
    brand            text    not null
);

alter table public.fueling_orders
    owner to vendors;

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
    date_create           text    not null,
    created_at            numeric not null
);

alter table public.vendor_fueling_orders
    owner to vendors;

create index vendor_fueling_orders_created_at_idx
    on public.vendor_fueling_orders (created_at desc);

