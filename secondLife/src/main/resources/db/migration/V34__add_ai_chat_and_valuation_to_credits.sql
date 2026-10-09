-- V34__add_ai_chat_and_valuation_to_credits.sql
-- Drop the legacy user_credits table as it's no longer used
DROP TABLE IF EXISTS user_credits;

-- Adopt columns already created by Hibernate or an earlier AI credit deployment.
-- Existing quantities and purchase price snapshots must remain unchanged.
ALTER TABLE credit_purchases
    ADD COLUMN IF NOT EXISTS ai_chat_quantity INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ai_chat_unit_price DECIMAL(19,2) NOT NULL DEFAULT 0.00;

-- Flyway runs before Hibernate; this legacy table is absent on a fresh database.
CREATE TABLE IF NOT EXISTS topup_packages (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    post_credits INT NOT NULL,
    chat_credits INT NOT NULL,
    price NUMERIC(38,2) NOT NULL,
    discount_percentage DOUBLE PRECISION
);

-- Add valuation_credits to topup_packages
ALTER TABLE topup_packages
    ADD COLUMN IF NOT EXISTS valuation_credits INT NOT NULL DEFAULT 0;
