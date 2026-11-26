ALTER TABLE inspection_fee_settlements
    ADD COLUMN return_original_reserve NUMERIC(18,2),
    ADD COLUMN return_quote_fee NUMERIC(18,2),
    ADD COLUMN return_charge_shipment_id UUID REFERENCES shipments(id),
    ADD COLUMN return_buyer_collected NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (return_buyer_collected >= 0),
    ADD COLUMN return_seller_collected NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (return_seller_collected >= 0);

ALTER TABLE system_wallet_transactions DROP CONSTRAINT uq_system_wallet_inspection_entry;
CREATE UNIQUE INDEX uq_system_wallet_inspection_entry ON system_wallet_transactions (inspection_order_id, inspection_entry)
    WHERE inspection_entry NOT IN ('RETURN_TOP_UP', 'RETURN_DEBT_PAYMENT', 'RETURN_DEBT_RECOVERY');