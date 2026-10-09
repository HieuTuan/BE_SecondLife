-- No prices/rates are seeded: ADMIN must explicitly configure a policy before new checkout.
CREATE TABLE IF NOT EXISTS commission_rules (
    id UUID PRIMARY KEY, name VARCHAR(150) NOT NULL, type VARCHAR(30) NOT NULL,
    category_id UUID REFERENCES categories(id), transaction_value_from NUMERIC(18,2), transaction_value_to NUMERIC(18,2),
    rate NUMERIC(9,6) NOT NULL CHECK (rate BETWEEN 0 AND 1),
    min_commission NUMERIC(18,2) NOT NULL CHECK (min_commission >= 0),
    max_commission NUMERIC(18,2) CHECK (max_commission >= min_commission),
    active BOOLEAN NOT NULL, revision BIGINT NOT NULL CHECK (revision > 0), updated_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    CHECK (
        (type = 'DEFAULT' AND category_id IS NULL AND transaction_value_from IS NULL AND transaction_value_to IS NULL)
        OR (type = 'CATEGORY' AND category_id IS NOT NULL AND transaction_value_from IS NULL AND transaction_value_to IS NULL)
        OR (type = 'TRANSACTION_VALUE' AND category_id IS NULL AND transaction_value_from >= 0 AND transaction_value_from IS NOT NULL
            AND (transaction_value_to IS NULL OR transaction_value_to > transaction_value_from))
    )
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_commission_active_default ON commission_rules(type) WHERE active AND type = 'DEFAULT';
CREATE UNIQUE INDEX IF NOT EXISTS uq_commission_active_category ON commission_rules(category_id) WHERE active AND type = 'CATEGORY';
-- Prevent overlapping ranges even for concurrent/direct database writes; no PostgreSQL extension required.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'commission_rules'::regclass AND conname = 'ex_commission_active_ranges') THEN
        ALTER TABLE commission_rules ADD CONSTRAINT ex_commission_active_ranges
            EXCLUDE USING gist (numrange(transaction_value_from, transaction_value_to, '[)') WITH &&)
            WHERE (active AND type = 'TRANSACTION_VALUE');
    END IF;
END;
$$;

CREATE TABLE IF NOT EXISTS commission_rule_audits (
    id UUID PRIMARY KEY, rule_id UUID NOT NULL REFERENCES commission_rules(id), actor_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(20) NOT NULL, old_value TEXT, new_value TEXT NOT NULL, reason VARCHAR(1000) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_commission_audit_rule_time ON commission_rule_audits(rule_id, changed_at DESC);

CREATE TABLE IF NOT EXISTS order_commission_snapshots (
    id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE REFERENCES orders(id), rule_id UUID NOT NULL REFERENCES commission_rules(id),
    rule_revision BIGINT NOT NULL, rule_name VARCHAR(150) NOT NULL, rule_type VARCHAR(30) NOT NULL,
    category_id UUID, transaction_value_from NUMERIC(18,2), transaction_value_to NUMERIC(18,2),
    rate NUMERIC(9,6) NOT NULL CHECK (rate BETWEEN 0 AND 1), min_commission NUMERIC(18,2) NOT NULL CHECK (min_commission >= 0),
    max_commission NUMERIC(18,2) CHECK (max_commission >= min_commission),
    base_type VARCHAR(30) NOT NULL CHECK (base_type = 'PRODUCT_PRICE'), commission_base NUMERIC(18,2) NOT NULL CHECK (commission_base > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'VND'), snapshotted_at TIMESTAMPTZ NOT NULL,
    captured_by UUID REFERENCES users(id), reason VARCHAR(1000), UNIQUE (id, order_id)
);
CREATE TABLE IF NOT EXISTS order_settlements (
    id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE REFERENCES orders(id), commission_snapshot_id UUID NOT NULL,
    buyer_id UUID NOT NULL REFERENCES users(id), seller_id UUID NOT NULL REFERENCES users(id),
    commission_base NUMERIC(18,2) NOT NULL CHECK (commission_base > 0), raw_commission NUMERIC(18,2) NOT NULL CHECK (raw_commission >= 0),
    platform_commission NUMERIC(18,2) NOT NULL CHECK (platform_commission >= 0 AND platform_commission <= commission_base),
    seller_payout NUMERIC(18,2) NOT NULL CHECK (seller_payout >= 0 AND seller_payout + platform_commission = commission_base),
    shipping_fee NUMERIC(18,2) NOT NULL CHECK (shipping_fee >= 0), currency VARCHAR(3) NOT NULL CHECK (currency = 'VND'),
    rounding_mode VARCHAR(20) NOT NULL CHECK (rounding_mode = 'HALF_UP'), settled_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (commission_snapshot_id, order_id) REFERENCES order_commission_snapshots(id, order_id)
);

CREATE OR REPLACE FUNCTION prevent_commission_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Commission snapshots, settlements and policy audit records are immutable';
END;
$$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid = 'order_commission_snapshots'::regclass AND tgname = 'trg_commission_snapshot_immutable') THEN
        CREATE TRIGGER trg_commission_snapshot_immutable BEFORE UPDATE OR DELETE ON order_commission_snapshots
            FOR EACH ROW EXECUTE FUNCTION prevent_commission_audit_mutation();
    END IF;
END;
$$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid = 'order_settlements'::regclass AND tgname = 'trg_order_settlement_immutable') THEN
        CREATE TRIGGER trg_order_settlement_immutable BEFORE UPDATE OR DELETE ON order_settlements
            FOR EACH ROW EXECUTE FUNCTION prevent_commission_audit_mutation();
    END IF;
END;
$$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid = 'commission_rule_audits'::regclass AND tgname = 'trg_commission_rule_audit_immutable') THEN
        CREATE TRIGGER trg_commission_rule_audit_immutable BEFORE UPDATE OR DELETE ON commission_rule_audits
            FOR EACH ROW EXECUTE FUNCTION prevent_commission_audit_mutation();
    END IF;
END;
$$;

INSERT INTO permissions(id, code, name, description)
VALUES (gen_random_uuid(), 'ADMIN_COMMISSION_MANAGE', 'Manage Commission', 'Manage commission policies and view settlement calculations')
ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND p.code = 'ADMIN_COMMISSION_MANAGE' ON CONFLICT DO NOTHING;
