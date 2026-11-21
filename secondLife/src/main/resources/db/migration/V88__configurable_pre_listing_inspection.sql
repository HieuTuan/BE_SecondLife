CREATE TABLE high_value_inspection_policy (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    threshold_price NUMERIC(18,2) NOT NULL CHECK (threshold_price > 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID REFERENCES users(id)
);
INSERT INTO high_value_inspection_policy(id, threshold_price, updated_at)
VALUES (1, 10000000, CURRENT_TIMESTAMP);

ALTER TABLE posts
    ADD COLUMN mandatory_inspection BOOLEAN,
    ADD COLUMN inspection_threshold_snapshot NUMERIC(18,2),
    ADD COLUMN inspection_policy_version BIGINT;

UPDATE posts SET mandatory_inspection = TRUE, inspection_threshold_snapshot = 10000000,
    inspection_policy_version = 0 WHERE status = 'PENDING_INSPECTION';

CREATE TABLE pre_listing_inspection_quotes (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id),
    order_type VARCHAR(30) NOT NULL CHECK (order_type IN ('AT_HOME', 'AT_CENTER')),
    expires_at TIMESTAMPTZ NOT NULL,
    quote_json TEXT NOT NULL,
    seller_pickup_json TEXT NOT NULL
);
CREATE INDEX idx_pre_listing_quotes_post ON pre_listing_inspection_quotes(post_id);
ALTER TABLE inspection_orders
    ADD COLUMN pre_listing_quote_id UUID UNIQUE REFERENCES pre_listing_inspection_quotes(id);

ALTER TABLE inspection_fee_settlements DROP CONSTRAINT IF EXISTS inspection_fee_settlements_outcome_check;
ALTER TABLE inspection_fee_settlements ADD CONSTRAINT inspection_fee_settlements_outcome_check CHECK (outcome IN (
    'PAYMENT_EXPIRED', 'CANCELLED_BEFORE_INSPECTION', 'FAILED_INSPECTION',
    'PASSED_PURCHASED', 'PASSED_BUYER_DEFAULTED', 'PRE_LISTING_PASSED'
));
