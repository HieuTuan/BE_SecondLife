ALTER TABLE shipments
    ADD COLUMN sender_name TEXT,
    ADD COLUMN sender_phone TEXT,
    ADD COLUMN sender_address TEXT,
    ADD COLUMN receiver_name TEXT,
    ADD COLUMN receiver_phone TEXT,
    ADD COLUMN receiver_address TEXT;

WITH snapshots AS MATERIALIZED (
    SELECT id, CASE WHEN pg_input_is_valid(payload, 'jsonb') THEN payload::jsonb END AS route
    FROM shipments
)
UPDATE shipments s
SET sender_name = NULLIF(snapshots.route ->> 'from_name', ''),
    sender_phone = NULLIF(snapshots.route ->> 'from_phone', ''),
    sender_address = NULLIF(snapshots.route ->> 'from_address', ''),
    receiver_name = NULLIF(snapshots.route ->> 'to_name', ''),
    receiver_phone = NULLIF(snapshots.route ->> 'to_phone', ''),
    receiver_address = NULLIF(snapshots.route ->> 'to_address', '')
FROM snapshots
WHERE s.id = snapshots.id
  AND (snapshots.route ? 'from_address' OR snapshots.route ? 'to_address');

-- A delivery to the buyer can belong to both the purchase and its linked inspection.
ALTER TABLE shipments
    DROP CONSTRAINT IF EXISTS ck_shipment_parent,
    DROP CONSTRAINT IF EXISTS ck_shipment_leg,
    ADD CONSTRAINT ck_shipment_parent CHECK (
        (order_id IS NOT NULL OR inspection_order_id IS NOT NULL)
        AND (order_id IS NULL OR inspection_order_id IS NULL OR leg IN ('SELLER_TO_BUYER', 'CENTER_TO_BUYER'))
    ),
    ADD CONSTRAINT ck_shipment_leg CHECK (
        leg IN ('SELLER_TO_BUYER', 'SELLER_TO_CENTER', 'CENTER_TO_BUYER', 'CENTER_TO_SELLER', 'BUYER_TO_SELLER')
    );
