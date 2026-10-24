ALTER TABLE system_wallet_transactions
    DROP CONSTRAINT system_wallet_transactions_type_check,
    ADD CONSTRAINT system_wallet_transactions_type_check
        CHECK (type IN ('OPENING_BALANCE', 'PAYMENT_HOLD', 'SETTLEMENT', 'REFUND', 'RETURN_SHIPPING_FEE'));

CREATE UNIQUE INDEX uq_wallet_return_shipping_fee_reference
    ON wallet_transactions (reference_id) WHERE type = 'RETURN_SHIPPING_FEE';

CREATE TABLE return_shipping_fees (
    id UUID PRIMARY KEY REFERENCES shipments(id),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    report_id UUID NOT NULL UNIQUE REFERENCES user_reports(id),
    seller_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    fee_amount NUMERIC(18,2),
    collected_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    debt_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assessed_at TIMESTAMPTZ,
    assessed_by UUID REFERENCES users(id),
    CONSTRAINT ck_return_shipping_fee_amount
        CHECK (fee_amount IS NULL OR (fee_amount >= 0 AND fee_amount <= 1000000000)),
    CONSTRAINT ck_return_shipping_fee_balances
        CHECK (collected_amount >= 0 AND collected_amount <= 1000000000
            AND debt_amount >= 0 AND debt_amount <= 1000000000),
    CONSTRAINT ck_return_shipping_fee_state
        CHECK ((status = 'PENDING_FEE' AND fee_amount IS NULL AND collected_amount = 0 AND debt_amount = 0
                    AND assessed_at IS NULL AND assessed_by IS NULL)
            OR (status IN ('PAID', 'OUTSTANDING') AND fee_amount IS NOT NULL
                    AND fee_amount = collected_amount + debt_amount
                    AND assessed_at IS NOT NULL AND assessed_by IS NOT NULL
                    AND ((status = 'PAID' AND debt_amount = 0)
                        OR (status = 'OUTSTANDING' AND debt_amount > 0))))
);

CREATE INDEX idx_return_shipping_fees_seller_created
    ON return_shipping_fees (seller_id, created_at DESC, id DESC);

CREATE FUNCTION prevent_return_shipping_fee_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Return shipping fee assessments cannot be deleted';
    END IF;
    IF OLD.status <> 'PENDING_FEE' OR NEW.status NOT IN ('PAID', 'OUTSTANDING')
        OR ROW(NEW.id, NEW.order_id, NEW.report_id, NEW.seller_id, NEW.created_at)
            IS DISTINCT FROM ROW(OLD.id, OLD.order_id, OLD.report_id, OLD.seller_id, OLD.created_at) THEN
        RAISE EXCEPTION 'Only pending return shipping fees may be assessed once';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_return_shipping_fee_immutable
    BEFORE UPDATE OR DELETE ON return_shipping_fees
    FOR EACH ROW EXECUTE FUNCTION prevent_return_shipping_fee_mutation();
