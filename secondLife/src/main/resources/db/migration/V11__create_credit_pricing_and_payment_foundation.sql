CREATE TABLE credit_types (
    code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

INSERT INTO credit_types (code, name) VALUES
    ('LISTING', 'Listing Credit'),
    ('VALUATION', 'Valuation Credit');

CREATE TABLE credit_pricing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_type VARCHAR(20) NOT NULL UNIQUE REFERENCES credit_types(code),
    unit_price NUMERIC(19, 2),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    active BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ck_credit_pricing_positive CHECK (unit_price IS NULL OR unit_price > 0),
    CONSTRAINT ck_credit_pricing_active_price CHECK (NOT active OR unit_price IS NOT NULL)
);

-- Unit prices are deliberately unconfigured until Admin supplies actual prices.
INSERT INTO credit_pricing_rules (credit_type) VALUES ('LISTING'), ('VALUATION');

CREATE TABLE credit_discount_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    min_quantity INT NOT NULL UNIQUE,
    max_quantity INT,
    discount_rate NUMERIC(5, 4) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ck_credit_tier_range CHECK (min_quantity > 0 AND (max_quantity IS NULL OR max_quantity >= min_quantity)),
    CONSTRAINT ck_credit_tier_rate CHECK (discount_rate >= 0 AND discount_rate < 1)
);

INSERT INTO credit_discount_tiers (min_quantity, max_quantity, discount_rate) VALUES
    (1, 4, 0.0000),
    (5, 9, 0.0500),
    (10, 29, 0.1000),
    (30, 49, 0.1500),
    (50, NULL, 0.2000);

ALTER TABLE credit_discount_tiers ADD CONSTRAINT ex_credit_tier_overlap
    EXCLUDE USING GIST (
        int8range(min_quantity::bigint, COALESCE(max_quantity::bigint, 2147483647), '[]') WITH &&
    ) WHERE (active);

CREATE TABLE credit_purchases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    listing_quantity INT NOT NULL,
    valuation_quantity INT NOT NULL,
    listing_unit_price NUMERIC(19, 2) NOT NULL,
    valuation_unit_price NUMERIC(19, 2) NOT NULL,
    discount_tier_id UUID REFERENCES credit_discount_tiers(id) ON DELETE SET NULL,
    discount_min_quantity INT NOT NULL,
    discount_max_quantity INT,
    discount_rate NUMERIC(5, 4) NOT NULL,
    subtotal NUMERIC(19, 2) NOT NULL,
    discount_amount NUMERIC(19, 2) NOT NULL,
    final_fee NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL DEFAULT 'PAYMENT_PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_credit_purchase_quantities CHECK (listing_quantity >= 0 AND valuation_quantity >= 0 AND listing_quantity + valuation_quantity > 0),
    CONSTRAINT ck_credit_purchase_amounts CHECK (listing_unit_price >= 0 AND valuation_unit_price >= 0 AND subtotal >= 0 AND discount_amount >= 0 AND final_fee >= 0),
    CONSTRAINT ck_credit_purchase_status CHECK (status IN ('PAYMENT_PENDING', 'PAID', 'PAYMENT_FAILED'))
);
CREATE INDEX idx_credit_purchases_user_created ON credit_purchases (user_id, created_at DESC);

CREATE TABLE credit_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    credit_type VARCHAR(20) NOT NULL REFERENCES credit_types(code),
    quantity BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_credit_balances_user_type UNIQUE (user_id, credit_type),
    CONSTRAINT ck_credit_balance_nonnegative CHECK (quantity >= 0)
);

CREATE TABLE credit_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    credit_type VARCHAR(20) NOT NULL REFERENCES credit_types(code),
    purchase_id UUID REFERENCES credit_purchases(id),
    entry_type VARCHAR(20) NOT NULL,
    quantity_delta BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_credit_ledger_entry_type CHECK (entry_type IN ('GRANT', 'CONSUME', 'REFUND')),
    CONSTRAINT ck_credit_ledger_delta CHECK (quantity_delta <> 0 AND balance_after >= 0)
);
CREATE INDEX idx_credit_ledger_user_created ON credit_ledger (user_id, created_at DESC);

CREATE TABLE payment_intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id UUID NOT NULL REFERENCES credit_purchases(id),
    provider VARCHAR(30) NOT NULL,
    provider_intent_id VARCHAR(150) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_intent_provider_ref UNIQUE (provider, provider_intent_id),
    CONSTRAINT ck_payment_intent_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_intent_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);
CREATE INDEX idx_payment_intents_purchase ON payment_intents (purchase_id);

-- Inbox deduplicates trusted provider callbacks. It does not grant credits on Day05.
CREATE TABLE payment_callback_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_intent_id UUID NOT NULL REFERENCES payment_intents(id),
    provider VARCHAR(30) NOT NULL,
    provider_event_id VARCHAR(150) NOT NULL,
    normalized_status VARCHAR(30) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_callback_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT ck_payment_callback_status CHECK (normalized_status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

INSERT INTO permissions (code, name, description)
VALUES ('CREDIT_READ_SELF', 'Read Own Credits', 'Read own credit balances and pricing')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.code = 'CREDIT_READ_SELF' AND r.code IN ('SELLER', 'ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;
