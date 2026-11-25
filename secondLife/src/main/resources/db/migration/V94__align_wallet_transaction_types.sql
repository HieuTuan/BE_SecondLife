-- Older Hibernate-created databases may still have the original enum check.
ALTER TABLE wallet_transactions
    DROP CONSTRAINT IF EXISTS wallet_transactions_type_check,
    ADD CONSTRAINT wallet_transactions_type_check CHECK (
        type IN ('DEPOSIT', 'WITHDRAW', 'PAYMENT', 'EARNING', 'REFUND', 'RETURN_SHIPPING_FEE', 'SHIPPING_REIMBURSE')
    );
