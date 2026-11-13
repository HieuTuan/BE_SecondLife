-- ============================================================================
-- V75: Chuyển đổi sang chuẩn Code-First JPA/ORM & Làm sạch khóa ngoại (FK)
-- 1. Gỡ bỏ các bảng dư thừa & bảng DB-First không dùng trong JPA Entity
-- 2. Gỡ bỏ các khóa ngoại phức hợp (Composite FK), khóa ngoại vòng (Circular FK),
--    và khóa ngoại trỏ vào cột không phải Primary Key (Non-PK FK)
-- 3. Thay thế bằng các khóa ngoại đơn cột (Single-Column PK FK) chuẩn JPA/Hibernate
-- 4. Gỡ bỏ các Trigger & Stored Function PL/pgSQL (chuyển về @Immutable & Service Java)
-- ============================================================================
SET LOCAL lock_timeout = '5s';

-- ============================================================================
-- PHẦN 1: GỠ BỎ CÁC KHÓA NGOẠI TRỎ TỚI BẢNG DƯ THỪA & XÓA BẢNG DƯ THỪA / DB-FIRST
-- ============================================================================

-- 1.1. Gỡ cột và FK từ credit_purchases trỏ tới credit_discount_tiers
ALTER TABLE credit_purchases
    DROP CONSTRAINT IF EXISTS fk_v61_credit_purchases_discount_tier_id,
    DROP CONSTRAINT IF EXISTS credit_purchases_discount_tier_id_fkey,
    DROP COLUMN IF EXISTS discount_tier_id;

-- 1.2. Gỡ các FK trỏ tới bảng danh mục DB-First credit_types(code)
-- (Trong Code-First, credit_type được quản lý bởi Java @Enumerated(EnumType.STRING) CreditType)
ALTER TABLE credit_balances
    DROP CONSTRAINT IF EXISTS fk_v61_credit_balances_credit_type,
    DROP CONSTRAINT IF EXISTS credit_balances_credit_type_fkey;

ALTER TABLE credit_ledger
    DROP CONSTRAINT IF EXISTS fk_v61_credit_ledger_credit_type,
    DROP CONSTRAINT IF EXISTS credit_ledger_credit_type_fkey;

ALTER TABLE credit_pricing_rules
    DROP CONSTRAINT IF EXISTS fk_v61_credit_pricing_rules_credit_type,
    DROP CONSTRAINT IF EXISTS credit_pricing_rules_credit_type_fkey;

-- 1.3. Xóa các bảng dư thừa và các bảng lịch sử không còn Entity trong Code-First
DROP TABLE IF EXISTS post_image_urls;
DROP TABLE IF EXISTS fraud_flags;
DROP TABLE IF EXISTS credit_discount_tiers;
DROP TABLE IF EXISTS payment_callback_events;
DROP TABLE IF EXISTS payment_intents;
DROP TABLE IF EXISTS credit_types;
DROP TABLE IF EXISTS inspection_order_checklists;
DROP TABLE IF EXISTS inspection_report_details;
DROP TABLE IF EXISTS inspection_appeals;
DROP TABLE IF EXISTS inspection_reports;

-- ============================================================================
-- PHẦN 2: DỌN DẸP RÀNG BUỘC KHÓA NGOẠI "TÙM LUM" (COMPOSITE / CIRCULAR / NON-PK)
-- ============================================================================

-- 2.1. Gỡ các khóa ngoại phức hợp (Composite Foreign Keys) gây xung đột với chuẩn @ManyToOne JPA
ALTER TABLE posts
    DROP CONSTRAINT IF EXISTS fk_posts_catalog_item_category,
    DROP CONSTRAINT IF EXISTS fk_v61_posts_item_id_category_id;

ALTER TABLE category_question_templates
    DROP CONSTRAINT IF EXISTS fk_templates_catalog_item_category,
    DROP CONSTRAINT IF EXISTS fk_v61_category_question_templates_item_id_category_id;

ALTER TABLE category_question_template_proposals
    DROP CONSTRAINT IF EXISTS fk_template_proposals_item_category;

DROP INDEX IF EXISTS uq_items_id_category;

ALTER TABLE order_settlements
    DROP CONSTRAINT IF EXISTS fk_v61_order_settlements_commission_snapshot_id_order_id,
    DROP CONSTRAINT IF EXISTS order_settlements_commission_snapshot_id_order_id_fkey;

ALTER TABLE order_commission_snapshots
    DROP CONSTRAINT IF EXISTS order_commission_snapshots_id_order_id_key;

-- 2.2. Gỡ khóa ngoại vòng (Circular FK) giữa shipping_quotes và orders
-- Giữ lại chiều chuẩn JPA: orders.shipping_quote_id -> shipping_quotes(id)
ALTER TABLE shipping_quotes
    DROP CONSTRAINT IF EXISTS fk_v61_shipping_quotes_consumed_order_id,
    DROP CONSTRAINT IF EXISTS shipping_quotes_consumed_order_id_fkey;

-- 2.3. Gỡ khóa ngoại trỏ vào cột VARCHAR code (Non-PK) trên bảng roles
ALTER TABLE user_role_audit
    DROP CONSTRAINT IF EXISTS fk_v61_user_role_audit_role_code,
    DROP CONSTRAINT IF EXISTS user_role_audit_role_code_fkey;

ALTER TABLE permission_assignable_roles
    DROP CONSTRAINT IF EXISTS fk_v61_permission_assignable_roles_role_code,
    DROP CONSTRAINT IF EXISTS permission_assignable_roles_role_code_fkey;

-- ============================================================================
-- PHẦN 3: BỔ SUNG KHÓA NGOẠI ĐƠN CỘT (SINGLE-COLUMN FK) ĐỒNG BỘ VỚI JPA @ManyToOne
-- ============================================================================

DO $$
BEGIN
    -- 3.1. posts.item_id -> items(id)
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_posts_item_id'
    ) THEN
        ALTER TABLE posts
            ADD CONSTRAINT fk_posts_item_id
            FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE RESTRICT NOT VALID;
    END IF;

    -- 3.2. category_question_templates.item_id -> items(id)
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_category_question_templates_item_id'
    ) THEN
        ALTER TABLE category_question_templates
            ADD CONSTRAINT fk_category_question_templates_item_id
            FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE RESTRICT NOT VALID;
    END IF;

    -- 3.3. category_question_template_proposals.item_id -> items(id)
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_category_question_template_proposals_item_id'
    ) THEN
        ALTER TABLE category_question_template_proposals
            ADD CONSTRAINT fk_category_question_template_proposals_item_id
            FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE NOT VALID;
    END IF;

    -- 3.4. order_settlements.commission_snapshot_id -> order_commission_snapshots(id)
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_order_settlements_commission_snapshot_id'
    ) THEN
        ALTER TABLE order_settlements
            ADD CONSTRAINT fk_order_settlements_commission_snapshot_id
            FOREIGN KEY (commission_snapshot_id) REFERENCES order_commission_snapshots(id) ON DELETE RESTRICT NOT VALID;
    END IF;
END $$;

-- ============================================================================
-- PHẦN 4: GỠ BỎ CÁC TRIGGER & STORED FUNCTION DB-FIRST
-- (Đã chuyển sang quản lý Code-First bằng @Immutable trên Entity & Service/Repository Java)
-- ============================================================================

DROP TRIGGER IF EXISTS trg_ai_estimate_immutable ON ai_price_estimates;
DROP FUNCTION IF EXISTS prevent_ai_estimate_mutation();

DROP TRIGGER IF EXISTS trg_commission_snapshot_immutable ON order_commission_snapshots;
DROP TRIGGER IF EXISTS trg_order_settlement_immutable ON order_settlements;
DROP TRIGGER IF EXISTS trg_commission_rule_audit_immutable ON commission_rule_audits;
DROP FUNCTION IF EXISTS prevent_commission_audit_mutation();

DROP TRIGGER IF EXISTS trg_system_wallet_transaction_immutable ON system_wallet_transactions;
DROP FUNCTION IF EXISTS prevent_system_wallet_transaction_mutation();

DROP TRIGGER IF EXISTS seller_reputation_events_immutable ON seller_reputation_events;
DROP TRIGGER IF EXISTS order_confirmation_penalties_immutable ON order_confirmation_penalties;
DROP FUNCTION IF EXISTS prevent_reputation_event_mutation();

DROP TRIGGER IF EXISTS report_submissions_immutable ON report_submissions;
DROP FUNCTION IF EXISTS prevent_report_submission_mutation();

DROP TRIGGER IF EXISTS trg_return_shipping_fee_immutable ON return_shipping_fees;
DROP FUNCTION IF EXISTS prevent_return_shipping_fee_mutation();

DROP FUNCTION IF EXISTS secondlife_split_inspection_catalog_items_v71();
