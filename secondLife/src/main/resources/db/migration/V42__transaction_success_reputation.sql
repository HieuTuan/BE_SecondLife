ALTER TABLE orders ADD COLUMN paid_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN handover_deadline TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN seller_ready_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN carrier_picked_up_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN cancellation_reason_code VARCHAR(40);
ALTER TABLE orders ADD COLUMN cancelled_by UUID REFERENCES users(id);
ALTER TABLE orders ADD COLUMN return_responsible_party VARCHAR(20)
    CHECK (return_responsible_party IN ('SELLER','BUYER','CARRIER','NONE'));
-- Historical orders have no provable pickup SLA. Do not invent deadline or fault attribution.
ALTER TABLE user_reports DROP CONSTRAINT user_reports_reason_code_check;
ALTER TABLE user_reports ADD CONSTRAINT user_reports_reason_code_check CHECK (reason_code IN (
    'MISLEADING_DESCRIPTION','COUNTERFEIT','FRAUD','ITEM_NOT_RECEIVED','OTHER',
    'LATE_HANDOVER','SELLER_CANCELLATION','RETURN_NOT_AS_DESCRIBED'));
CREATE TABLE reputation_order_faults (
    order_id UUID NOT NULL REFERENCES orders(id),
    seller_id UUID NOT NULL REFERENCES users(id),
    fault_type VARCHAR(30) NOT NULL CHECK (fault_type IN ('PRODUCT_OR_REPORT','CANCELLATION','RETURN','LATE_HANDOVER')),
    source_report_id UUID REFERENCES user_reports(id),
    actor_id UUID REFERENCES users(id),
    reason VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id,fault_type)
);
CREATE INDEX idx_reputation_fault_seller ON reputation_order_faults(seller_id);
INSERT INTO reputation_order_faults(order_id,seller_id,fault_type,source_report_id,actor_id,reason,created_at)
SELECT DISTINCT ON (r.order_id) r.order_id,r.seller_id,'PRODUCT_OR_REPORT',r.id,r.reviewer_id,r.decision_reason,r.resolved_at
FROM user_reports r WHERE r.order_id IS NOT NULL AND r.status='CONFIRMED' AND r.responsible_party='SELLER'
ORDER BY r.order_id,r.resolved_at,r.id;
DELETE FROM reputation_order_outcomes o USING orders x WHERE x.id=o.order_id
    AND NOT ((x.status='COMPLETED' AND x.escrow_status='RELEASED') OR (x.status='CANCELLED' AND x.escrow_status='REFUNDED'));
INSERT INTO reputation_order_outcomes(order_id,seller_id,outcome,source_report_id,updated_at)
SELECT o.id,o.seller_id,CASE WHEN EXISTS(SELECT 1 FROM reputation_order_faults f WHERE f.order_id=o.id) THEN 'BAD' ELSE 'GOOD' END,
    (SELECT f.source_report_id FROM reputation_order_faults f WHERE f.order_id=o.id ORDER BY f.created_at LIMIT 1),CURRENT_TIMESTAMP
FROM orders o WHERE (o.status='COMPLETED' AND o.escrow_status='RELEASED')
    OR (o.status='CANCELLED' AND o.escrow_status='REFUNDED' AND EXISTS(SELECT 1 FROM reputation_order_faults f WHERE f.order_id=o.id))
ON CONFLICT(order_id) DO UPDATE SET outcome=EXCLUDED.outcome,source_report_id=EXCLUDED.source_report_id,updated_at=EXCLUDED.updated_at;
INSERT INTO seller_reputations(seller_id) SELECT DISTINCT seller_id FROM reputation_order_outcomes ON CONFLICT DO NOTHING;
ALTER TABLE seller_reputation_events ADD COLUMN formula_version VARCHAR(50) NOT NULL DEFAULT 'SMOOTHED_SUCCESS_RATE_V1';
WITH counts AS (
    SELECT seller_id,COUNT(*) FILTER(WHERE outcome='GOOD') AS good,COUNT(*) FILTER(WHERE outcome='BAD') AS bad
    FROM reputation_order_outcomes GROUP BY seller_id
)
INSERT INTO seller_reputation_events(id,seller_id,event_type,reason,good_before,bad_before,good_after,bad_after,
    score_after,formula_version,created_at)
SELECT gen_random_uuid(),s.seller_id,'REPUTATION_POLICY_CHANGED',
    'Changed to actual transaction success rate; pending orders excluded; previous audit remains unchanged',
    s.good_transactions,s.bad_transactions,COALESCE(c.good,0),COALESCE(c.bad,0),
    CASE WHEN COALESCE(c.good,0)+COALESCE(c.bad,0)>0 THEN ROUND(100.0*c.good/(c.good+c.bad),2) ELSE NULL END,
    'TRANSACTION_SUCCESS_RATE_V2',CURRENT_TIMESTAMP
FROM seller_reputations s LEFT JOIN counts c ON c.seller_id=s.seller_id;
UPDATE seller_reputations s SET
    good_transactions=(SELECT COUNT(*) FROM reputation_order_outcomes o WHERE o.seller_id=s.seller_id AND o.outcome='GOOD'),
    bad_transactions=(SELECT COUNT(*) FROM reputation_order_outcomes o WHERE o.seller_id=s.seller_id AND o.outcome='BAD'),
    updated_at=CURRENT_TIMESTAMP;
