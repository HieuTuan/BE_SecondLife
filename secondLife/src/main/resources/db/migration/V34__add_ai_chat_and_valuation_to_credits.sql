-- V32__add_ai_chat_and_valuation_to_credits.sql
-- Drop the legacy user_credits table as it's no longer used
DROP TABLE IF EXISTS user_credits;

-- Add ai_chat_quantity and ai_chat_unit_price to credit_purchases
ALTER TABLE credit_purchases
    ADD COLUMN ai_chat_quantity INT NOT NULL DEFAULT 0,
    ADD COLUMN ai_chat_unit_price DECIMAL(19,2) NOT NULL DEFAULT 0.00;

-- Add valuation_credits to topup_packages
ALTER TABLE topup_packages
    ADD COLUMN valuation_credits INT NOT NULL DEFAULT 0;
