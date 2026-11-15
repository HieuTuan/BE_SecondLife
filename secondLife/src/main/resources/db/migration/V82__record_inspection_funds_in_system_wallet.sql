ALTER TABLE system_wallet_transactions
    ALTER COLUMN order_id DROP NOT NULL,
    ADD COLUMN inspection_order_id UUID REFERENCES inspection_orders(id),
    ADD COLUMN inspection_entry VARCHAR(32),
    ADD COLUMN carrier_fee NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (carrier_fee >= 0),
    ADD COLUMN unfunded_expense NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (unfunded_expense >= 0),
    DROP CONSTRAINT system_wallet_transactions_type_check,
    ADD CONSTRAINT ck_system_wallet_transaction_reference CHECK (
        (order_id IS NOT NULL AND inspection_order_id IS NULL AND inspection_entry IS NULL
            AND type IN ('OPENING_BALANCE', 'PAYMENT_HOLD', 'SETTLEMENT', 'REFUND', 'RETURN_SHIPPING_FEE'))
        OR
        (order_id IS NULL AND inspection_order_id IS NOT NULL AND inspection_entry IS NOT NULL
            AND type IN ('INSPECTION_HOLD', 'INSPECTION_SETTLEMENT', 'INSPECTION_REFUND', 'INSPECTION_RETURN_SHIPPING'))
    ),
    ADD CONSTRAINT uq_system_wallet_inspection_entry UNIQUE (inspection_order_id, inspection_entry);
