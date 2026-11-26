DROP VIEW IF EXISTS system_wallet_statement;

CREATE VIEW system_wallet_statement AS
WITH movements AS (
    SELECT t.*,
           COALESCE(hub.amount, 0) AS hub_amount,
           LEAST(COALESCE(hub.amount, 0), GREATEST(-t.held_delta - t.revenue_delta, 0)) AS funded_hub,
           COALESCE(s.seller_compensation_amount + s.center_payout_amount, 0) AS inspection_payout,
           i.fee_payer, i.buyer_fee_percent, i.corporate_shipping, s.outcome,
           return_fee.id AS linked_return_entry_id,
           COALESCE(ABS(return_refund.shipping_delta), 0) AS return_refund
    FROM system_wallet_transactions t
    LEFT JOIN inspection_orders i ON i.id = t.inspection_order_id
    LEFT JOIN inspection_fee_settlements s ON s.inspection_order_id = i.id
    LEFT JOIN inspection_hub_payouts hub ON hub.inspection_order_id = s.inspection_order_id
    LEFT JOIN system_wallet_transactions return_fee ON t.inspection_entry = 'RETURN_REFUND'
        AND return_fee.inspection_order_id = t.inspection_order_id AND return_fee.inspection_entry = 'RETURN_SHIPPING'
    LEFT JOIN system_wallet_transactions return_refund ON t.inspection_entry = 'RETURN_SHIPPING'
        AND return_refund.inspection_order_id = t.inspection_order_id AND return_refund.inspection_entry = 'RETURN_REFUND'
)
SELECT (t.id::text || '-' || line.number)::varchar AS entry_id,
       t.id, line.number AS line_number, COALESCE(t.order_id, t.inspection_order_id) AS order_id,
       t.inspection_order_id, t.type, line.category::varchar AS fund_category,
       ABS(line.amount) AS total_delta,
       (CASE SIGN(line.amount) WHEN 1 THEN 'PLUS' WHEN -1 THEN 'MINUS' ELSE 'ZERO' END)::varchar AS delta_action,
       line.held AS held_delta, line.shipping AS shipping_delta, line.revenue AS revenue_delta,
       t.held_balance_after, t.shipping_balance_after, t.revenue_balance_after,
       line.carrier_fee, line.unfunded AS unfunded_expense, t.created_at,
       (CASE
           WHEN t.type = 'RETURN_SHIPPING_FEE' THEN 'Người bán chi trả cước hoàn hàng'
           WHEN t.order_id IS NOT NULL THEN 'Người mua chi trả cước giao hàng'
           WHEN t.corporate_shipping AND t.outcome = 'FAILED_INSPECTION'
               AND (t.type IN ('INSPECTION_RETURN_SHIPPING', 'INSPECTION_SETTLEMENT')
                   OR t.inspection_entry IN ('SELLER_RETURN_PAYMENT', 'SELLER_SETTLEMENT_PAYMENT', 'RETURN_REFUND'))
               THEN 'Người bán chịu phí vận chuyển sau khi kiểm định không đạt'
           WHEN t.buyer_fee_percent IS NOT NULL
               THEN format('Tỷ lệ Buyer: %s%% - Seller: %s%%', t.buyer_fee_percent, 100 - t.buyer_fee_percent)
           WHEN t.fee_payer = 'SPLIT_50_50' THEN 'Chia đôi Buyer: 50% - Seller: 50%'
           WHEN t.fee_payer = 'SELLER_PAYS' THEN 'Người bán chịu 100% phí kiểm định và cước đã thỏa thuận'
           WHEN t.fee_payer = 'BUYER_PAYS' THEN 'Người mua chịu 100% phí kiểm định và cước đã thỏa thuận'
           ELSE 'Cước theo thỏa thuận kiểm định'
       END)::varchar AS user_shipping_note,
       line.description::varchar AS breakdown_description
FROM movements t
CROSS JOIN LATERAL (
    SELECT 1 AS number,
           CASE WHEN t.inspection_order_id IS NOT NULL OR t.type = 'RETURN_SHIPPING_FEE'
                     OR t.held_delta = 0 AND t.shipping_delta <> 0
                THEN 'USER_SHIPPING' ELSE 'PRODUCT_ESCROW' END AS category,
           CASE WHEN t.inspection_entry = 'RETURN_DEBT_RECOVERY' THEN t.shipping_delta
                WHEN t.type = 'INSPECTION_RETURN_SHIPPING' THEN -(t.carrier_fee + t.return_refund)
                WHEN t.type = 'SETTLEMENT' THEN t.held_delta + t.revenue_delta
                ELSE t.held_delta + t.shipping_delta END AS amount,
           CASE WHEN t.type = 'SETTLEMENT' THEN t.held_delta + t.revenue_delta ELSE t.held_delta END AS held,
           t.shipping_delta - t.return_refund AS shipping, 0::numeric AS revenue, t.carrier_fee, t.unfunded_expense AS unfunded,
           CASE
               WHEN t.type = 'SETTLEMENT' THEN 'Giải ngân tiền hàng cho người bán sau khi trích hoa hồng'
               WHEN t.inspection_entry = 'RETURN_DEBT_RECOVERY' THEN format(
                   'Bù trừ công nợ cước hoàn đã thu từ Buyer/Seller: %s đ.', ABS(t.shipping_delta))
               WHEN t.type = 'INSPECTION_RETURN_SHIPPING' THEN format(
                   'Cước hoàn thực tế %s đ. Trừ quỹ %s đ. Phần thiếu %s đ. Hoàn dự phòng dư %s đ.',
                   t.carrier_fee, ABS(t.shipping_delta), t.unfunded_expense, t.return_refund)
               WHEN t.inspection_entry = 'RETURN_REFUND' THEN format(
                   'Hoàn %s đ dự phòng/thu dư về ví Buyer/Seller.', ABS(t.shipping_delta))
               WHEN t.inspection_entry = 'SELLER_RETURN_PAYMENT' THEN 'Thu bổ sung từ người bán để chi trả cước hoàn'
               WHEN t.inspection_entry = 'SELLER_SETTLEMENT_PAYMENT' THEN format(
                   'Thu bổ sung từ người bán. Phí kiểm định %s đ, quỹ cước %s đ.', t.held_delta, t.shipping_delta)
               WHEN t.inspection_entry = 'RETURN_TOP_UP' THEN format(
                   'Thu bổ sung vượt dự phòng cước hoàn từ Buyer/Seller. Quỹ cước %s đ.', t.shipping_delta)
               WHEN t.inspection_entry = 'RETURN_DEBT_PAYMENT' THEN format(
                   'Thu công nợ cước hoàn từ Buyer/Seller. Quỹ cước %s đ.', t.shipping_delta)
               WHEN t.type = 'RETURN_SHIPPING_FEE' THEN 'Thu cước hoàn hàng từ người bán'
               WHEN t.type = 'REFUND' THEN format(
                   'Hoàn tiền hàng %s đ, hoàn cước %s đ.', ABS(t.held_delta), ABS(t.shipping_delta))
               WHEN t.type = 'INSPECTION_REFUND' THEN format(
                   'Hoàn phí kiểm định hoặc cọc %s đ, hoàn cước hoặc dự phòng %s đ.',
                   ABS(t.held_delta), ABS(t.shipping_delta))
               WHEN t.type = 'INSPECTION_HOLD' THEN format(
                   'Thu từ %s. Phí kiểm định và cọc %s đ, cước và dự phòng %s đ.',
                   CASE WHEN t.inspection_entry = 'BUYER_PAYMENT' THEN 'người mua' ELSE 'người bán' END,
                   t.held_delta, t.shipping_delta)
               ELSE format('Tiền hàng %s đ, cước giao hàng %s đ.', t.held_delta, t.shipping_delta)
           END AS description
    WHERE t.type <> 'INSPECTION_SETTLEMENT' AND t.linked_return_entry_id IS NULL
      AND (t.revenue_delta = 0 OR t.held_delta + t.shipping_delta <> CASE
          WHEN t.type = 'SETTLEMENT' THEN -t.revenue_delta ELSE 0 END)

    UNION ALL

    SELECT 2, 'HUB_PAYOUT', -t.hub_amount, -t.funded_hub, 0, 0, 0,
           t.hub_amount - t.funded_hub,
           'Chi trả phí dịch vụ kiểm định cho Trung tâm Hub'
    WHERE t.type = 'INSPECTION_SETTLEMENT' AND t.hub_amount > 0

    UNION ALL

    SELECT 3, 'USER_SHIPPING', -(t.inspection_payout - t.hub_amount + t.carrier_fee),
           t.held_delta + t.revenue_delta + t.funded_hub, t.shipping_delta, 0,
           t.carrier_fee, t.unfunded_expense - (t.hub_amount - t.funded_hub),
           format('Cước gửi Hub %s đ, chi trả khác từ phí kiểm định hoặc cọc %s đ.',
                  t.carrier_fee, t.inspection_payout - t.hub_amount)
    WHERE t.type = 'INSPECTION_SETTLEMENT' AND (t.carrier_fee <> 0 OR t.inspection_payout > t.hub_amount)

    UNION ALL

    SELECT 4, 'COMMISSION', t.revenue_delta,
           CASE WHEN t.type IN ('SETTLEMENT', 'INSPECTION_SETTLEMENT') THEN -t.revenue_delta ELSE 0 END,
           0, t.revenue_delta, 0, 0, 'Ghi nhận doanh thu hoa hồng sàn'
    WHERE t.revenue_delta <> 0
) line;
