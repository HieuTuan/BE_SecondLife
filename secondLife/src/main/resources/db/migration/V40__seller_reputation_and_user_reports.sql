CREATE TABLE user_reports (
    id UUID PRIMARY KEY,
    reporter_id UUID NOT NULL REFERENCES users(id),
    request_id UUID NOT NULL,
    seller_id UUID NOT NULL REFERENCES users(id),
    post_id UUID NOT NULL REFERENCES posts(id),
    order_id UUID REFERENCES orders(id),
    reason_code VARCHAR(40) NOT NULL CHECK (reason_code IN ('MISLEADING_DESCRIPTION','COUNTERFEIT','FRAUD','ITEM_NOT_RECEIVED','OTHER')),
    description VARCHAR(2000) NOT NULL,
    evidence_urls JSONB NOT NULL CHECK (jsonb_typeof(evidence_urls) = 'array'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN','IN_REVIEW','CONFIRMED','DISMISSED')),
    reviewer_id UUID REFERENCES users(id),
    responsible_party VARCHAR(20) CHECK (responsible_party IN ('SELLER','BUYER','CARRIER','NONE')),
    decision_reason VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    UNIQUE (reporter_id, request_id),
    CHECK (reporter_id <> seller_id),
    CHECK ((status IN ('OPEN','IN_REVIEW') AND resolved_at IS NULL AND responsible_party IS NULL)
        OR (status IN ('CONFIRMED','DISMISSED') AND resolved_at IS NOT NULL AND reviewer_id IS NOT NULL AND decision_reason IS NOT NULL AND responsible_party IS NOT NULL)),
    CHECK (status <> 'IN_REVIEW' OR reviewer_id IS NOT NULL)
);
CREATE INDEX idx_reports_staff_queue ON user_reports(status, created_at, id);
CREATE UNIQUE INDEX uq_reports_pending_target ON user_reports(reporter_id,post_id,COALESCE(order_id,'00000000-0000-0000-0000-000000000000'::uuid),reason_code)
WHERE status IN ('OPEN','IN_REVIEW');
CREATE INDEX idx_reports_reporter ON user_reports(reporter_id, created_at DESC, id DESC);

CREATE TABLE seller_reputations (
    seller_id UUID PRIMARY KEY REFERENCES users(id),
    good_transactions BIGINT NOT NULL DEFAULT 0 CHECK (good_transactions >= 0),
    bad_transactions BIGINT NOT NULL DEFAULT 0 CHECK (bad_transactions >= 0),
    confirmed_incidents BIGINT NOT NULL DEFAULT 0 CHECK (confirmed_incidents >= 0),
    serious_incidents BIGINT NOT NULL DEFAULT 0 CHECK (serious_incidents >= 0 AND serious_incidents <= confirmed_incidents),
    selling_restricted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE reputation_order_outcomes (
    order_id UUID PRIMARY KEY REFERENCES orders(id),
    seller_id UUID NOT NULL REFERENCES users(id),
    outcome VARCHAR(4) NOT NULL CHECK (outcome IN ('GOOD','BAD')),
    source_report_id UUID REFERENCES user_reports(id),
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE reputation_incidents (
    incident_key VARCHAR(150) PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES users(id),
    source_report_id UUID NOT NULL REFERENCES user_reports(id),
    serious BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE seller_reputation_events (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES users(id),
    event_type VARCHAR(40) NOT NULL,
    order_id UUID REFERENCES orders(id),
    report_id UUID REFERENCES user_reports(id),
    actor_id UUID REFERENCES users(id),
    reason VARCHAR(2000) NOT NULL,
    good_before BIGINT NOT NULL,
    bad_before BIGINT NOT NULL,
    good_after BIGINT NOT NULL,
    bad_after BIGINT NOT NULL,
    score_before NUMERIC(5,2),
    score_after NUMERIC(5,2),
    prior_good BIGINT,
    prior_total BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (good_before >= 0 AND bad_before >= 0 AND good_after >= 0 AND bad_after >= 0)
);
CREATE INDEX idx_reputation_events_seller ON seller_reputation_events(seller_id, created_at DESC, id DESC);

-- Import completed historical sales without changing wallets or settlements.
INSERT INTO reputation_order_outcomes(order_id,seller_id,outcome,updated_at)
SELECT id,seller_id,'GOOD',COALESCE(updated_at,created_at) FROM orders
WHERE status = 'COMPLETED' AND escrow_status = 'RELEASED';
INSERT INTO seller_reputations(seller_id,good_transactions)
SELECT seller_id,COUNT(*) FROM reputation_order_outcomes GROUP BY seller_id;
INSERT INTO seller_reputation_events(id,seller_id,event_type,order_id,reason,good_before,bad_before,good_after,bad_after,created_at)
SELECT gen_random_uuid(),seller_id,'HISTORICAL_ORDER_IMPORTED',order_id,'Completed sale imported from existing order history',
    ROW_NUMBER() OVER (PARTITION BY seller_id ORDER BY updated_at,order_id) - 1,0,
    ROW_NUMBER() OVER (PARTITION BY seller_id ORDER BY updated_at,order_id),0,updated_at
FROM reputation_order_outcomes;

CREATE FUNCTION prevent_reputation_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Seller reputation events are immutable';
END;
$$;
CREATE TRIGGER seller_reputation_events_immutable BEFORE UPDATE OR DELETE ON seller_reputation_events
FOR EACH ROW EXECUTE FUNCTION prevent_reputation_event_mutation();
