ALTER TABLE return_shipping_fees ADD COLUMN last_collected_at TIMESTAMPTZ;

-- Multiple partial collections can reference the same return. The service locks the assessment
-- before collecting, and commits the installment and its wallet entries together.
DROP INDEX uq_wallet_return_shipping_fee_reference;
CREATE INDEX idx_wallet_return_shipping_fee_reference
    ON wallet_transactions (reference_id) WHERE type = 'RETURN_SHIPPING_FEE';

ALTER TABLE system_wallet_transactions DROP CONSTRAINT system_wallet_transactions_order_id_type_key;
CREATE UNIQUE INDEX uq_system_wallet_order_non_return_entry
    ON system_wallet_transactions (order_id, type)
    WHERE order_id IS NOT NULL AND type <> 'RETURN_SHIPPING_FEE';

CREATE INDEX idx_return_shipping_fees_collectible
    ON return_shipping_fees (id) WHERE status = 'OUTSTANDING';

CREATE OR REPLACE FUNCTION prevent_return_shipping_fee_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Return shipping fee assessments cannot be deleted';
    END IF;
    IF ROW(NEW.id, NEW.order_id, NEW.report_id, NEW.seller_id, NEW.created_at)
        IS DISTINCT FROM ROW(OLD.id, OLD.order_id, OLD.report_id, OLD.seller_id, OLD.created_at) THEN
        RAISE EXCEPTION 'Return shipping fee identity cannot be changed';
    END IF;
    IF OLD.status = 'PENDING_FEE' AND NEW.status IN ('PAID', 'OUTSTANDING') THEN
        RETURN NEW;
    END IF;
    IF OLD.status = 'OUTSTANDING' AND NEW.status IN ('OUTSTANDING', 'PAID')
        AND ROW(NEW.fee_amount, NEW.assessed_at, NEW.assessed_by)
            IS NOT DISTINCT FROM ROW(OLD.fee_amount, OLD.assessed_at, OLD.assessed_by)
        AND NEW.collected_amount > OLD.collected_amount AND NEW.debt_amount < OLD.debt_amount
        AND NEW.last_collected_at IS NOT NULL
        AND (OLD.last_collected_at IS NULL OR NEW.last_collected_at >= OLD.last_collected_at) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Only assessment or repayment can change a return shipping fee';
END $$;
