-- Ensure listings that went to an inspection Hub and have not been delivered back to the seller
-- are not left as ACTIVE / RESERVED after inspection failure or cancellation.
UPDATE posts p
SET status = 'RETURNING'
WHERE p.status IN ('ACTIVE', 'RESERVED')
  AND EXISTS (
      SELECT 1
      FROM inspection_orders io
      WHERE io.post_id = p.id
        AND io.order_type = 'AT_CENTER'
        AND io.inspection_scope = 'BUYER_REQUEST'
        AND io.status IN ('FAILED', 'CANCELLED')
        AND io.order_id IS NULL
        AND EXISTS (
            SELECT 1
            FROM shipments inbound
            WHERE inbound.inspection_order_id = io.id
              AND inbound.leg = 'SELLER_TO_CENTER'
              AND inbound.status IN ('PICKED', 'DELIVERED')
        )
        AND NOT EXISTS (
            SELECT 1
            FROM shipments outbound
            WHERE outbound.inspection_order_id = io.id
              AND outbound.leg = 'CENTER_TO_SELLER'
              AND outbound.status = 'DELIVERED'
        )
  );
