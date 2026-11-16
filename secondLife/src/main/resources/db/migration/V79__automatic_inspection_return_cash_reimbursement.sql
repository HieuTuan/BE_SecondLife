ALTER TABLE shipments
    DROP CONSTRAINT ck_shipment_cash_confirmation,
    ADD CONSTRAINT ck_shipment_cash_confirmation CHECK (
        (cash_confirmed_at IS NULL AND cash_confirmed_by IS NULL AND cash_receipt_reference IS NULL
            AND cash_paid_amount IS NULL AND cash_reimbursed_amount IS NULL)
        OR
        (cash_confirmed_at IS NOT NULL AND cash_payer_id IS NOT NULL
            AND cash_receipt_reference IS NOT NULL AND cash_paid_amount IS NOT NULL
            AND cash_reimbursed_amount IS NOT NULL AND cash_paid_amount >= 0
            AND cash_reimbursed_amount BETWEEN 0 AND cash_paid_amount
            AND (cash_confirmed_by IS NOT NULL OR (
                leg = 'CENTER_TO_SELLER' AND provider = 'LALAMOVE' AND order_code IS NOT NULL
                AND cash_receipt_reference = 'LALAMOVE_COMPLETED:' || order_code
            )))
    );
