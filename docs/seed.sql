-- =====================================================================
-- fueling_chat — local seed (D-12, updated)
--
-- ONE canonical fueling chain `F-10001` exercising GetFuelingInfoTool
-- end-to-end across all four databases. Deterministic (fixed ids and
-- timestamps), idempotent (DELETE + INSERT per table inside a
-- transaction, safe to re-run), and read-only-safe for the app.
--
-- Invocation (single psql run, `\c` switches databases in place):
--   psql -h localhost -p 25432 -U postgres -v ON_ERROR_STOP=1 -f docs/seed.sql
--
-- Chain shape (ids are stable spec constants, see CONTRACT-TOOL-001):
--   fueling.fuelings                        F-10001
--     └─ vendor_fueling_order_id            VFO-10001   (payment join key, D-14)
--   gas_station.gas_stations                GS-20001
--   payment.payments (2 rows)               P-50001, P-50002 → order_id = VFO-10001
--   vendors.fueling_orders                  PK fueling_id = F-10001
--   vendors.fueling_events (2 rows)         event_offset 1, 2
--   vendors.vendor_fueling_orders           PK fueling_id = F-10001
--
-- Timestamps follow G-5/D-15: all numeric/bigint epoch columns are
-- EPOCH MILLISECONDS UTC; the only timestamptz is gas_stations.updated_at.
--   F-10001 created at 2026-10-01T09:30:00Z → 1790847000000
--   F-10001 finished at 2026-10-01T09:30:05Z → 1790847005000
--   F-10001 updated at 2026-10-01T09:31:45Z → 1790847105000
-- =====================================================================

-- ── fueling ──────────────────────────────────────────────────────────

\connect fueling

BEGIN;

DELETE FROM public.fuelings WHERE fueling_id = 'F-10001';

INSERT INTO public.fuelings (
    fueling_id, vendor_fueling_order_id, user_id, status, amount, fuel_type,
    gas_station_id, gas_pump_id, refueling_gun_id, fuel_reservation_key,
    created_at, updated_at, actual_amount, vendor_transaction_date,
    failed_reason, vendor_fuel_price, fueled_orders, discount_fuel_price,
    fueling_type, extra, fueling_payment_type, finished_at
) VALUES (
    'F-10001', 'VFO-10001', 'U-90001', 'completed', 1500.00, 'AI-95',
    'GS-20001', 'PUMP-7', 'GUN-3', 'RESV-10001',
    1790847000000, 1790847105000, 1495.50, '2026-10-01T09:30:00Z',
    NULL, 52.49, '[{"orderId": "VFO-10001"}]'::jsonb, NULL,
    'regular', '{}'::jsonb, 'prepayment', 1790847005000
);

COMMIT;

-- ── gas_station ──────────────────────────────────────────────────────

\connect gas_station

BEGIN;

DELETE FROM public.gas_stations WHERE gas_station_id = 'GS-20001';

INSERT INTO public.gas_stations (
    gas_station_id, brand, address, vendor_gas_station_id, latitude, longitude,
    activated, deleted, fueling_limits, gas_station_name, manually_disabled,
    updated_at, gas_station_type, extra, location_id, payment_mode,
    subnet, owner, public_brand, take_off_mode, available_services
) VALUES (
    'GS-20001', 'FuelCorp', 'Moscow, Tverskaya 7', 'VS-30001', 55.7601, 37.6184,
    true, false, '{"maxLiters": 100}'::jsonb, 'Tverskaya 7', false,
    '2026-09-15 08:00:00+00'::timestamptz, 'UNDEFINED', '{}'::jsonb, 'msk_mo', 'PREPAID',
    NULL, NULL, NULL, 'after', '{}'::jsonb
);

SELECT setval('gas_stations_gas_station_id_seq', 1000, true);

COMMIT;

-- ── payment ──────────────────────────────────────────────────────────

\connect payment

BEGIN;

-- all seed payments hang off the canonical order id (join key, D-14)
DELETE FROM public.payments WHERE order_id = 'VFO-10001';

INSERT INTO public.payments (
    payment_id, order_id, user_id, external_payment_id, status,
    created_at, updated_at, payment_system, payment_method, payment_type,
    purpose, amount, actual_amount, card_binding_id, card_binding_type,
    sbp_subscription_id
) VALUES
(
    'P-50001', 'VFO-10001', 'U-90001', 'EXT-777', '{"state": "succeeded"}'::jsonb,
    1790847105000, 1790847115000, 'bank-card', 'bank-card', 'fuel-purchase',
    'usual', 1500.00, 1495.50, 'CB-123', 'bank-card', NULL
),
(
    -- second row exercises the list path of the payments aggregation
    'P-50002', 'VFO-10001', 'U-90001', 'EXT-778', '{"state": "failed"}'::jsonb,
    1790847105000, 1790847115000, 'bank-card', 'bank-card', 'fuel-purchase',
    'usual', 1500.00, NULL, 'CB-123', 'bank-card', NULL
);

SELECT setval('payments_payment_id_seq', 1000, true);

COMMIT;

-- ── vendors ──────────────────────────────────────────────────────────

\connect vendors

BEGIN;

DELETE FROM public.fueling_orders WHERE fueling_id = 'F-10001';
DELETE FROM public.fueling_events WHERE fueling_id = 'F-10001';
DELETE FROM public.vendor_fueling_orders WHERE fueling_id = 'F-10001';

INSERT INTO public.fueling_orders (
    fueling_id, volume, price, fuel_type, station_id, pump_id,
    created_at, data, fuel_description, brand
) VALUES (
    'F-10001', 28.5, 52.49, 'AI-95', 'VS-30001', 'PUMP-7',
    1790847000000, '{"checkId": "CHK-42"}'::jsonb, 'AI-95 premium', 'FuelCorp'
);

INSERT INTO public.fueling_events (
    fueling_id, created_at, status, data, event_offset, brand
) VALUES
(
    'F-10001', 1790846998000, 'started', '{"pump": "PUMP-7"}'::jsonb, 1, 'FuelCorp'
),
(
    'F-10001', 1790847005000, 'completed', '{"liters": 28.5}'::jsonb, 2, 'FuelCorp'
);

INSERT INTO public.vendor_fueling_orders (
    fueling_id, fuel_type, quantity, total_sum, status, gas_pump_number,
    vendor_gas_station_id, vendor_fuel_price, email, phone,
    date_create, created_at
) VALUES (
    'F-10001', 'AI-95', 28.5, 1495.50, 'PAID', 7,
    'VS-30001', 52.49, 'driver@example.com', '+7 900 000-00-01',
    '2026-10-01T09:31:45Z', 1790847105000
);

SELECT setval('fueling_events_event_offset_seq', 100, true);

COMMIT;
