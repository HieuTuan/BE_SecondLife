-- Deadlines are assigned only to newly paid orders; historical orders are not penalized retroactively.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS seller_confirmed_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS confirmation_deadline TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS confirmation_cancel_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS confirmation_late_penalty NUMERIC(5,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS confirmation_timeout_penalty NUMERIC(5,2);
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK
    (status IN ('PENDING_PAYMENT','PROCESSING','CONFIRMED','SHIPPED','DELIVERED','COMPLETED','CANCELLED'));
CREATE INDEX idx_orders_due_confirmation ON orders(confirmation_deadline,id)
    WHERE seller_confirmed_at IS NULL AND status='PROCESSING' AND escrow_status='HELD';

ALTER TABLE seller_reputations ADD COLUMN confirmation_penalty_points NUMERIC(18,2) NOT NULL DEFAULT 0
    CHECK (confirmation_penalty_points>=0);
ALTER TABLE seller_reputation_events ADD COLUMN penalty_before NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE seller_reputation_events ADD COLUMN penalty_after NUMERIC(18,2) NOT NULL DEFAULT 0;
CREATE TABLE order_confirmation_penalties (
    order_id UUID NOT NULL REFERENCES orders(id),
    milestone VARCHAR(20) NOT NULL CHECK (milestone IN ('LATE','TIMEOUT')),
    seller_id UUID NOT NULL REFERENCES users(id),
    points NUMERIC(5,2) NOT NULL CHECK(points>0 AND points<=100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(order_id,milestone)
);
CREATE INDEX idx_confirmation_penalties_seller ON order_confirmation_penalties(seller_id);
CREATE TRIGGER order_confirmation_penalties_immutable BEFORE UPDATE OR DELETE ON order_confirmation_penalties
FOR EACH ROW EXECUTE FUNCTION prevent_reputation_event_mutation();
