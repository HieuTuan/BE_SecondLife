-- Preserve historical rows: their original balances and reference category were not recorded.
ALTER TABLE wallet_transactions
    ADD COLUMN balance_before NUMERIC(18,2),
    ADD COLUMN balance_after NUMERIC(18,2),
    ADD COLUMN reference_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    ADD CONSTRAINT ck_wallet_transaction_snapshot CHECK (
        (balance_before IS NULL AND balance_after IS NULL)
        OR (balance_before IS NOT NULL AND balance_after IS NOT NULL
            AND balance_before >= 0 AND balance_after >= 0
            AND balance_after = balance_before + amount)
    ),
    ADD CONSTRAINT ck_wallet_transaction_reference_type CHECK (
        reference_type IN ('ORDER', 'INSPECTION_ORDER', 'DEPOSIT_REQUEST', 'WITHDRAWAL',
                           'SHIPMENT', 'CREDIT_PURCHASE', 'TOPUP_PACKAGE', 'UNKNOWN')
    );
