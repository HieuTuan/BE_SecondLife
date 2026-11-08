SET LOCAL lock_timeout = '5s';

-- NULL preserves the historical settlement policy for existing orders.
ALTER TABLE inspection_orders
    ADD COLUMN platform_fee_rate_snapshot NUMERIC(9,6),
    ADD CONSTRAINT ck_inspection_platform_rate_snapshot
        CHECK (platform_fee_rate_snapshot IS NULL OR platform_fee_rate_snapshot BETWEEN 0 AND 1);
