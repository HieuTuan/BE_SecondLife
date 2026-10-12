-- V35__update_credit_type_enum_constraints.sql
-- Update Hibernate-generated check constraints for the CreditType enum to include AI_CHAT

-- For credit_ledger table
ALTER TABLE credit_ledger DROP CONSTRAINT IF EXISTS credit_ledger_credit_type_check;
ALTER TABLE credit_ledger ADD CONSTRAINT credit_ledger_credit_type_check 
CHECK (credit_type IN ('LISTING', 'VALUATION', 'AI_CHAT'));

-- For credit_balances table (if Hibernate generated one here as well)
ALTER TABLE credit_balances DROP CONSTRAINT IF EXISTS credit_balances_credit_type_check;
ALTER TABLE credit_balances ADD CONSTRAINT credit_balances_credit_type_check 
CHECK (credit_type IN ('LISTING', 'VALUATION', 'AI_CHAT'));

-- For credit_pricing_rules table (Hibernate also generated one here)
ALTER TABLE credit_pricing_rules DROP CONSTRAINT IF EXISTS credit_pricing_rules_credit_type_check;
ALTER TABLE credit_pricing_rules ADD CONSTRAINT credit_pricing_rules_credit_type_check 
CHECK (credit_type IN ('LISTING', 'VALUATION', 'AI_CHAT'));

-- Ensure AI_CHAT is in credit_types table if it is used as a foreign key reference
INSERT INTO credit_types (code, name) 
VALUES ('AI_CHAT', 'AI Chat Credit') 
ON CONFLICT (code) DO NOTHING;

-- Also add a pricing rule for it so system won't crash when querying
INSERT INTO credit_pricing_rules (id, credit_type, active, currency, updated_at) 
VALUES (gen_random_uuid(), 'AI_CHAT', false, 'VND', CURRENT_TIMESTAMP) 
ON CONFLICT (credit_type) DO NOTHING;
