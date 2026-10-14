-- V42 preserves original confirmed report evidence. Classify closed seller-fault orders
-- for NFR without inventing responsibility for historical buyer/carrier cancellations.
INSERT INTO reputation_order_faults(order_id,seller_id,fault_type,source_report_id,actor_id,reason,created_at)
SELECT f.order_id,f.seller_id,
    CASE WHEN o.return_responsible_party IS NOT NULL OR EXISTS(SELECT 1 FROM shipments s WHERE s.order_id=o.id AND s.leg='BUYER_TO_SELLER')
        THEN 'RETURN' ELSE 'CANCELLATION' END,
    f.source_report_id,f.actor_id,f.reason,f.created_at
FROM reputation_order_faults f JOIN orders o ON o.id=f.order_id
WHERE f.fault_type='PRODUCT_OR_REPORT' AND o.status='CANCELLED' AND o.escrow_status='REFUNDED'
ON CONFLICT(order_id,fault_type) DO NOTHING;
