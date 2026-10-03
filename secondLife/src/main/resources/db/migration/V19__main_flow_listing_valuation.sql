-- Bridge domain tables previously managed by Hibernate without changing existing rows.
CREATE TABLE IF NOT EXISTS categories (id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL, description TEXT);
CREATE TABLE IF NOT EXISTS items (id UUID PRIMARY KEY, category_id UUID NOT NULL REFERENCES categories(id), name VARCHAR(255) NOT NULL);
CREATE TABLE IF NOT EXISTS category_question_templates (
    id UUID PRIMARY KEY, category_id UUID NOT NULL, item_id UUID, template_text TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS posts (
    id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), category_id UUID, item_id UUID,
    title VARCHAR(255), description TEXT, image_url VARCHAR(255), status VARCHAR(255) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ, price NUMERIC(18,2), item_condition VARCHAR(50),
    ai_description TEXT, ai_suggested_price NUMERIC(18,2), ai_chat_session_id UUID, template_id UUID,
    rejection_reason TEXT
);
ALTER TABLE posts ADD COLUMN IF NOT EXISTS listing_credit_charged BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS image_fingerprint VARCHAR(64);
-- Historical published posts must never be charged again after deployment.
UPDATE posts SET listing_credit_charged = TRUE, published_at = COALESCE(published_at, created_at)
WHERE status IN ('ACTIVE', 'PENDING_INSPECTION');
CREATE INDEX IF NOT EXISTS idx_posts_seller_status ON posts (user_id, status);

CREATE TABLE IF NOT EXISTS ai_chat_sessions (
    session_id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), post_id UUID,
    title VARCHAR(255), message_count INT NOT NULL DEFAULT 0, is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS ai_chat_messages (
    message_id UUID PRIMARY KEY, session_id UUID NOT NULL REFERENCES ai_chat_sessions(session_id),
    role VARCHAR(50) NOT NULL, message_content TEXT NOT NULL, sent_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_messages_session_time ON ai_chat_messages(session_id, sent_at);
CREATE INDEX IF NOT EXISTS idx_ai_chat_sessions_user ON ai_chat_sessions(user_id);
CREATE TABLE IF NOT EXISTS inspection_orders (
    id UUID PRIMARY KEY, post_id UUID NOT NULL REFERENCES posts(id), inspector_id UUID REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', note TEXT, inspection_fee NUMERIC(18,2) NOT NULL,
    shipping_fee NUMERIC(18,2) NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS ai_price_estimates (
    estimate_id UUID PRIMARY KEY, listing_id UUID NOT NULL, model_version VARCHAR(100),
    fair_price_min NUMERIC(18,2), fair_price_max NUMERIC(18,2), suggested_price NUMERIC(18,2),
    expected_sell_time VARCHAR(100), created_at TIMESTAMPTZ
);
ALTER TABLE ai_price_estimates ADD COLUMN IF NOT EXISTS request_id UUID;
ALTER TABLE ai_price_estimates ADD COLUMN IF NOT EXISTS input_fingerprint VARCHAR(64);
ALTER TABLE ai_price_estimates ADD COLUMN IF NOT EXISTS input_snapshot TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_estimate_post_request ON ai_price_estimates(listing_id, request_id);
CREATE INDEX IF NOT EXISTS idx_ai_estimate_post_created ON ai_price_estimates(listing_id, created_at DESC);
INSERT INTO permissions(id, code, name, description)
VALUES(gen_random_uuid(), 'LISTING_VALUATION_SELF', 'Value Own Listing', 'Request and read AI valuations of owned listings')
ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('SELLER', 'ADMIN') AND p.code = 'LISTING_VALUATION_SELF'
ON CONFLICT(role_id, permission_id) DO NOTHING;
