ALTER TABLE inspection_orders
    ADD COLUMN cash_shipping_simulation BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE shipments
    ADD COLUMN cash_payer_id UUID REFERENCES users(id),
    ADD COLUMN cash_confirmed_by UUID REFERENCES users(id),
    ADD COLUMN cash_receipt_reference VARCHAR(255),
    ADD COLUMN cash_confirmed_at TIMESTAMPTZ,
    ADD COLUMN cash_paid_amount NUMERIC(18,2),
    ADD COLUMN cash_reimbursed_amount NUMERIC(18,2),
    ADD CONSTRAINT ck_shipment_cash_payer CHECK (
        cash_payer_id IS NULL OR (
            inspection_order_id IS NOT NULL AND order_id IS NULL
            AND leg IN ('SELLER_TO_CENTER', 'CENTER_TO_SELLER') AND provider = 'LALAMOVE'
        )
    ),
    ADD CONSTRAINT ck_shipment_cash_confirmation CHECK (
        (cash_confirmed_at IS NULL AND cash_confirmed_by IS NULL AND cash_receipt_reference IS NULL
            AND cash_paid_amount IS NULL AND cash_reimbursed_amount IS NULL)
        OR
        (cash_confirmed_at IS NOT NULL AND cash_payer_id IS NOT NULL AND cash_confirmed_by IS NOT NULL
            AND cash_receipt_reference IS NOT NULL AND cash_paid_amount IS NOT NULL
            AND cash_reimbursed_amount IS NOT NULL AND cash_paid_amount >= 0
            AND cash_reimbursed_amount BETWEEN 0 AND cash_paid_amount)
    );

CREATE UNIQUE INDEX uq_inspection_cash_shipping_confirmation
    ON shipments(inspection_order_id, leg) WHERE cash_confirmed_at IS NOT NULL;

CREATE UNIQUE INDEX uq_wallet_shipping_reimburse_reference
    ON wallet_transactions(reference_id) WHERE type = 'SHIPPING_REIMBURSE';
