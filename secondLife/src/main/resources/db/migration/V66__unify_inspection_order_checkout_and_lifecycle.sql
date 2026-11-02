-- ==============================================================================
-- V66: UNIFY INSPECTION ORDER CHECKOUT AND LIFECYCLE
-- SecondLife Marketplace
-- ==============================================================================

-- 1. Mở rộng bảng inspection_orders phục vụ phân loại nguồn gốc, thương lượng giá, timeout phản hồi và cọc cam kết
ALTER TABLE inspection_orders
    ADD COLUMN IF NOT EXISTS inspection_scope VARCHAR(30) NOT NULL DEFAULT 'BUYER_REQUEST',
    ADD COLUMN IF NOT EXISTS negotiation_id UUID REFERENCES negotiations(id),
    ADD COLUMN IF NOT EXISTS approval_deadline TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS buyer_delivery_address_json TEXT,
    ADD COLUMN IF NOT EXISTS buyer_commitment_deposit NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fee_settled_at TIMESTAMPTZ;

-- Backfill dữ liệu lịch sử: các đơn kiểm định bài đăng giá trị cao không có buyer_id là PRE_LISTING
UPDATE inspection_orders
SET inspection_scope = 'PRE_LISTING',
    seller_id = COALESCE(seller_id, (SELECT p.user_id FROM posts p WHERE p.id = inspection_orders.post_id))
WHERE buyer_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_inspection_orders_scope') THEN
        ALTER TABLE inspection_orders
            ADD CONSTRAINT ck_inspection_orders_scope
                CHECK (inspection_scope IN ('PRE_LISTING', 'BUYER_REQUEST'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_inspection_orders_commitment_deposit') THEN
        ALTER TABLE inspection_orders
            ADD CONSTRAINT ck_inspection_orders_commitment_deposit
                CHECK (buyer_commitment_deposit >= 0);
    END IF;
END $$;

-- 2. Dọn dẹp trùng lặp active cũ nếu có trước khi tạo partial unique index
WITH active_duplicates AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY post_id
               ORDER BY created_at DESC, id DESC
           ) AS rn
    FROM inspection_orders
    WHERE status NOT IN ('CANCELLED', 'FAILED', 'PAYMENT_EXPIRED', 'COMPLETED')
)
UPDATE inspection_orders
SET status = 'CANCELLED',
    note = COALESCE(note, '') || ' | System cancelled superseded active duplicate during migration V66'
WHERE id IN (
    SELECT id FROM active_duplicates WHERE rn > 1
);

-- Partial Unique Index: Chặn tuyệt đối race-condition tạo >= 2 đơn kiểm định đang hoạt động trên cùng 1 bài đăng
CREATE UNIQUE INDEX IF NOT EXISTS uq_inspection_orders_one_active_per_post
    ON inspection_orders (post_id)
    WHERE status NOT IN ('CANCELLED', 'FAILED', 'PAYMENT_EXPIRED', 'COMPLETED');

-- 3. Index tăng tốc cho Scheduler quét hết hạn phản hồi 2 chiều và tra cứu theo bài đăng
CREATE INDEX IF NOT EXISTS idx_inspection_orders_post_created
    ON inspection_orders (post_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_inspection_orders_approval_deadline
    ON inspection_orders (status, approval_deadline)
    WHERE status IN ('WAITING_SELLER_APPROVAL', 'WAITING_BUYER_APPROVAL')
      AND approval_deadline IS NOT NULL;

-- 4. Bảng sổ cái đối soát tất toán phí kiểm định (Idempotent & Conservation of Money)
CREATE TABLE IF NOT EXISTS inspection_fee_settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_order_id UUID NOT NULL UNIQUE REFERENCES inspection_orders(id),
    outcome VARCHAR(40) NOT NULL CHECK (outcome IN (
        'PAYMENT_EXPIRED',
        'CANCELLED_BEFORE_INSPECTION',
        'FAILED_INSPECTION',
        'PASSED_PURCHASED',
        'PASSED_BUYER_DEFAULTED'
    )),
    total_collected NUMERIC(18, 2) NOT NULL CHECK (total_collected >= 0),
    center_recipient_user_id UUID REFERENCES users(id),
    center_payout_amount NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (center_payout_amount >= 0),
    outbound_shipping_allocated NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (outbound_shipping_allocated >= 0),
    return_reserve_allocated NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (return_reserve_allocated >= 0),
    buyer_refund_amount NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (buyer_refund_amount >= 0),
    seller_refund_amount NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (seller_refund_amount >= 0),
    seller_compensation_amount NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (seller_compensation_amount >= 0),
    platform_retained_amount NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (platform_retained_amount >= 0),
    settled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_inspection_fee_conservation CHECK (
        total_collected = center_payout_amount
                        + outbound_shipping_allocated
                        + return_reserve_allocated
                        + buyer_refund_amount
                        + seller_refund_amount
                        + seller_compensation_amount
                        + platform_retained_amount
    )
);

