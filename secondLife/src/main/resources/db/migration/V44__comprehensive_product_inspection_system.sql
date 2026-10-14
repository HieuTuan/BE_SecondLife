-- ==============================================================================
-- V44: COMPREHENSIVE PRODUCT INSPECTION SYSTEM
-- SecondLife Marketplace
-- ==============================================================================

-- 1. Bảng bậc thang tính phí di chuyển KTV kiểm định tại nhà
CREATE TABLE IF NOT EXISTS inspection_travel_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_number INT NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    from_km NUMERIC(8,2) NOT NULL,
    to_km NUMERIC(8,2), -- NULL nghĩa là vô cực (trên X km)
    price_per_km NUMERIC(18,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Seed sẵn 6 bậc chuẩn do user cấu hình
INSERT INTO inspection_travel_tiers
    (id, tier_number, name, from_km, to_km, price_per_km, status, created_at, updated_at) VALUES
(gen_random_uuid(), 1, '0-5 km đầu', 0.0, 5.0, 2000.00, 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 2, 'Trên 5-10 km', 5.0, 10.0, 2500.00, 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 3, 'Trên 10-15 km', 10.0, 15.0, 3000.00, 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 4, 'Trên 15-20 km', 15.0, 20.0, 3500.00, 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 5, 'Trên 20-30 km', 20.0, 30.0, 4000.00, 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 6, 'Trên 30 km', 30.0, NULL, 4500.00, 'ACTIVE', NOW(), NOW())
ON CONFLICT (tier_number) DO NOTHING;

-- 2. Bảng đề xuất biểu phí & duyệt biểu phí giữa Trung tâm và Admin Sàn
CREATE TABLE IF NOT EXISTS inspection_fee_proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID REFERENCES categories(id),
    base_inspection_fee NUMERIC(18,2) NOT NULL,
    tiers_json TEXT NOT NULL,
    proposed_by UUID NOT NULL REFERENCES users(id),
    reviewed_by UUID REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL', -- PENDING_APPROVAL, APPROVED, REJECTED
    reject_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    reviewed_at TIMESTAMP WITH TIME ZONE
);

-- 3. Bảng Template Checklist Kiểm Định
CREATE TABLE IF NOT EXISTS inspection_checklist_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID REFERENCES categories(id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 4. Bảng Tiêu chí chi tiết trong Checklist
CREATE TABLE IF NOT EXISTS inspection_checklist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES inspection_checklist_templates(id) ON DELETE CASCADE,
    item_title VARCHAR(255) NOT NULL,
    criteria_description TEXT,
    is_mandatory BOOLEAN DEFAULT TRUE,
    order_index INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 5. Mở rộng bảng inspection_orders
ALTER TABLE inspection_orders 
    ADD COLUMN IF NOT EXISTS order_type VARCHAR(30) DEFAULT 'AT_CENTER', -- AT_HOME, AT_CENTER
    ADD COLUMN IF NOT EXISTS fee_payer VARCHAR(30) DEFAULT 'BUYER_PAYS', -- BUYER_PAYS, SELLER_PAYS, SPLIT_50_50
    ADD COLUMN IF NOT EXISTS buyer_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS seller_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS order_id UUID REFERENCES orders(id),
    ADD COLUMN IF NOT EXISTS buyer_fee_share NUMERIC(18,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS seller_fee_share NUMERIC(18,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS travel_distance_km NUMERIC(8,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS travel_fee NUMERIC(18,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS return_shipping_reserve_fee NUMERIC(18,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_deadline TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS buyer_paid_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS seller_paid_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS appointment_scheduled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS checklist_result_json TEXT,
    ADD COLUMN IF NOT EXISTS video_proof_url TEXT,
    ADD COLUMN IF NOT EXISTS unboxing_video_url TEXT,
    ADD COLUMN IF NOT EXISTS buyer_consent_zin BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS seller_consent_zin BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS final_payment_deadline TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS final_payment_paid_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS final_item_price NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS final_shipping_fee NUMERIC(18,2);

CREATE INDEX IF NOT EXISTS idx_inspection_orders_buyer ON inspection_orders(buyer_id);
CREATE INDEX IF NOT EXISTS idx_inspection_orders_seller ON inspection_orders(seller_id);
CREATE INDEX IF NOT EXISTS idx_inspection_orders_status ON inspection_orders(status);
CREATE INDEX IF NOT EXISTS idx_inspection_orders_order ON inspection_orders(order_id);

-- 6. Bổ sung Quyền hạn PBAC cho kiểm định
INSERT INTO permissions (id, code, name, description) VALUES
    (gen_random_uuid(), 'INSPECTION_CHECKLIST_MANAGE', 'Manage Inspection Checklist', 'Create, update and delete inspection checklists'),
    (gen_random_uuid(), 'INSPECTION_FEE_MANAGE', 'Manage Inspection Fees', 'Propose or review inspection fee schedules'),
    (gen_random_uuid(), 'INSPECTION_REQUEST_SELF', 'Request Inspection', 'Request and negotiate inspection on purchase')
ON CONFLICT (code) DO NOTHING;

-- Gán quyền vào role_permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r CROSS JOIN permissions p
WHERE (r.code = 'INSPECTION_CENTER' AND p.code IN ('INSPECTION_CHECKLIST_MANAGE', 'INSPECTION_FEE_MANAGE'))
   OR (r.code = 'ADMIN' AND p.code IN ('INSPECTION_CHECKLIST_MANAGE', 'INSPECTION_FEE_MANAGE'))
   OR (r.code IN ('BUYER', 'SELLER') AND p.code = 'INSPECTION_REQUEST_SELF')
ON CONFLICT DO NOTHING;
