ALTER TABLE user_reports
    ADD COLUMN resolution_action VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN return_shipment_id UUID REFERENCES shipments(id),
    ADD COLUMN return_approved_at TIMESTAMPTZ,
    ADD COLUMN refunded_at TIMESTAMPTZ,
    ADD COLUMN refunded_by UUID REFERENCES users(id),
    ADD COLUMN refund_amount NUMERIC(18,2),
    ADD CONSTRAINT ck_reports_resolution_action
        CHECK (resolution_action IN ('NONE', 'RETURN_AND_REFUND')),
    ADD CONSTRAINT ck_reports_return_order
        CHECK (resolution_action = 'NONE' OR order_id IS NOT NULL),
    ADD CONSTRAINT ck_reports_return_approval
        CHECK ((resolution_action = 'NONE' AND return_approved_at IS NULL)
            OR (resolution_action = 'RETURN_AND_REFUND' AND status = 'CONFIRMED' AND return_approved_at IS NOT NULL)),
    ADD CONSTRAINT ck_reports_return_shipment
        CHECK (return_shipment_id IS NULL OR resolution_action = 'RETURN_AND_REFUND'),
    ADD CONSTRAINT ck_reports_refund_audit
        CHECK ((refunded_at IS NULL AND refunded_by IS NULL AND refund_amount IS NULL)
            OR (refunded_at IS NOT NULL AND refunded_by IS NOT NULL AND refund_amount IS NOT NULL
                AND refund_amount > 0 AND resolution_action = 'RETURN_AND_REFUND' AND return_shipment_id IS NOT NULL));

ALTER TABLE shipments ADD COLUMN report_id UUID REFERENCES user_reports(id);

ALTER TABLE orders ADD COLUMN shipping_hold BOOLEAN NOT NULL DEFAULT FALSE;

-- Historical frozen escrows have no reliable reason; preserve their hold until reviewed.
UPDATE orders SET shipping_hold = TRUE WHERE escrow_status = 'FROZEN';
