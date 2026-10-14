-- ==============================================================================
-- V41: INSPECTION MANAGER ISOLATION & PRODUCT CONDITION REPORT FIELDS
-- ==============================================================================

-- 1. Bo sung quan he Trung tam quan ly (Manager) vao bang users
ALTER TABLE users ADD COLUMN IF NOT EXISTS manager_id UUID NULL REFERENCES users(id);
CREATE INDEX IF NOT EXISTS idx_users_manager_id ON users(manager_id);

-- 2. Bo sung cac truong cham diem, tem niem phong va ma vach cho don kiem dinh
ALTER TABLE inspection_orders
    ADD COLUMN IF NOT EXISTS condition_score INT NULL,
    ADD COLUMN IF NOT EXISTS condition_grade VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS tamper_seal_id VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS barcode VARCHAR(100) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_inspection_orders_barcode ON inspection_orders(barcode) WHERE barcode IS NOT NULL;
