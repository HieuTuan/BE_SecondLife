ALTER TABLE inspection_orders
    ADD COLUMN buyer_fee_percent INTEGER,
    ADD COLUMN previous_buyer_fee_percent INTEGER,
    ADD CONSTRAINT inspection_buyer_fee_percent_step
        CHECK (buyer_fee_percent BETWEEN 0 AND 100 AND MOD(buyer_fee_percent, 5) = 0),
    ADD CONSTRAINT inspection_previous_buyer_fee_percent_step
        CHECK (previous_buyer_fee_percent BETWEEN 0 AND 100 AND MOD(previous_buyer_fee_percent, 5) = 0),
    ADD CONSTRAINT inspection_custom_fee_percent_required
        CHECK (fee_payer <> 'CUSTOM' OR buyer_fee_percent IS NOT NULL);
