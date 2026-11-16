ALTER TABLE inspection_orders
    ADD COLUMN corporate_shipping BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_inspection_shipping_payment_mode CHECK (NOT (corporate_shipping AND cash_shipping_simulation));

ALTER TABLE inspection_fee_settlements
    ADD COLUMN seller_additional_collected NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (seller_additional_collected >= 0),
    ADD COLUMN seller_outstanding_amount NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (seller_outstanding_amount >= 0),
    DROP CONSTRAINT ck_inspection_fee_conservation,
    ADD CONSTRAINT ck_inspection_fee_conservation CHECK (
        total_collected + seller_additional_collected + seller_outstanding_amount =
            center_payout_amount + outbound_shipping_allocated + return_reserve_allocated
            + buyer_refund_amount + seller_refund_amount + seller_compensation_amount + platform_retained_amount
    );
