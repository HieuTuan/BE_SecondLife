-- Add audit protection without modifying the already-applied V19 migration.
ALTER TABLE ai_price_estimates ADD CONSTRAINT ck_ai_estimate_valid_request CHECK (
    request_id IS NULL OR (
        input_fingerprint IS NOT NULL AND input_snapshot IS NOT NULL AND model_version IS NOT NULL
        AND fair_price_min IS NOT NULL AND fair_price_max IS NOT NULL AND suggested_price IS NOT NULL
        AND fair_price_min > 0 AND fair_price_min <= suggested_price AND suggested_price <= fair_price_max
        AND created_at IS NOT NULL
    )
);
-- Audit rows are append-only, including direct SQL writes.
CREATE FUNCTION prevent_ai_estimate_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'AI price estimates are immutable audit records';
END;
$$;
CREATE TRIGGER trg_ai_estimate_immutable BEFORE UPDATE OR DELETE ON ai_price_estimates
FOR EACH ROW EXECUTE FUNCTION prevent_ai_estimate_mutation();

INSERT INTO permission_assignable_roles(permission_id, role_code)
SELECT id, 'SELLER' FROM permissions WHERE code = 'LISTING_VALUATION_SELF'
ON CONFLICT DO NOTHING;
