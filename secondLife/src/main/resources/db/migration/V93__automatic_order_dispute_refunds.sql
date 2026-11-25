-- A null actor on a completed refund or fee assessment identifies an automatic operation.
ALTER TABLE user_reports
    DROP CONSTRAINT ck_reports_refund_audit,
    ADD CONSTRAINT ck_reports_refund_audit
        CHECK ((refunded_at IS NULL AND refunded_by IS NULL AND refund_amount IS NULL)
            OR (refunded_at IS NOT NULL AND refund_amount IS NOT NULL AND refund_amount > 0
                AND resolution_action = 'RETURN_AND_REFUND' AND return_shipment_id IS NOT NULL));

ALTER TABLE return_shipping_fees
    DROP CONSTRAINT ck_return_shipping_fee_state,
    ADD CONSTRAINT ck_return_shipping_fee_state
        CHECK ((status = 'PENDING_FEE' AND fee_amount IS NULL AND collected_amount = 0 AND debt_amount = 0
                    AND assessed_at IS NULL AND assessed_by IS NULL)
            OR (status IN ('PAID', 'OUTSTANDING') AND fee_amount IS NOT NULL
                    AND fee_amount = collected_amount + debt_amount
                    AND assessed_at IS NOT NULL
                    AND ((status = 'PAID' AND debt_amount = 0)
                        OR (status = 'OUTSTANDING' AND debt_amount > 0))));

CREATE INDEX idx_shipments_dispute_returns_delivered ON shipments (id)
    WHERE leg = 'BUYER_TO_SELLER' AND status = 'DELIVERED' AND delivered_at IS NOT NULL AND report_id IS NOT NULL;
