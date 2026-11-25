-- Replace the legacy Hibernate check that predates percentage-based fee splits.
-- V84 continues to require a valid buyer percentage for CUSTOM.
ALTER TABLE inspection_orders
    DROP CONSTRAINT IF EXISTS inspection_orders_fee_payer_check,
    ADD CONSTRAINT inspection_orders_fee_payer_check
        CHECK (fee_payer IN ('BUYER_PAYS', 'SELLER_PAYS', 'SPLIT_50_50', 'CUSTOM'));
