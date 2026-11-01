-- Preserve package prices, quantities and existing wallet/credit history.
-- Old discount_percentage is retained for compatibility; API discounts are derived.
ALTER TABLE topup_packages
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE topup_package_purchases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE NO ACTION,
    request_id UUID NOT NULL,
    package_id UUID NOT NULL REFERENCES topup_packages(id) ON DELETE NO ACTION,
    package_version BIGINT NOT NULL CHECK (package_version >= 0),
    package_name VARCHAR(255) NOT NULL,
    package_description VARCHAR(255),
    post_credits INTEGER NOT NULL CHECK (post_credits >= 0),
    chat_credits INTEGER NOT NULL CHECK (chat_credits >= 0),
    valuation_credits INTEGER NOT NULL CHECK (valuation_credits >= 0),
    price NUMERIC(38,2) NOT NULL CHECK (price >= 1000),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'VND'),
    reference_price NUMERIC(38,2) NOT NULL CHECK (reference_price >= price),
    discount_percentage NUMERIC(5,2) NOT NULL CHECK (discount_percentage BETWEEN 0 AND 100),
    listing_unit_price NUMERIC(19,2) NOT NULL CHECK (listing_unit_price >= 0),
    chat_unit_price NUMERIC(19,2) NOT NULL CHECK (chat_unit_price >= 0),
    valuation_unit_price NUMERIC(19,2) NOT NULL CHECK (valuation_unit_price >= 0),
    wallet_balance_before NUMERIC(18,2) NOT NULL CHECK (wallet_balance_before >= 0),
    wallet_balance_after NUMERIC(18,2) NOT NULL CHECK (wallet_balance_after >= 0),
    post_balance_after BIGINT NOT NULL CHECK (post_balance_after >= 0),
    chat_balance_after BIGINT NOT NULL CHECK (chat_balance_after >= 0),
    valuation_balance_after BIGINT NOT NULL CHECK (valuation_balance_after >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_topup_purchase_request UNIQUE (user_id, request_id),
    CONSTRAINT ck_topup_purchase_credits CHECK (
        post_credits::BIGINT + chat_credits::BIGINT + valuation_credits::BIGINT > 0),
    CONSTRAINT ck_topup_purchase_wallet CHECK (wallet_balance_before - wallet_balance_after = price)
);
CREATE INDEX ix_topup_purchase_history ON topup_package_purchases(user_id, created_at DESC, id DESC);
CREATE INDEX ix_topup_purchase_package ON topup_package_purchases(package_id);
