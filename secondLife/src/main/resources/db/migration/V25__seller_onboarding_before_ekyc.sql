CREATE TABLE seller_onboarding (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    shop_name VARCHAR(30) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(16) NOT NULL,
    pickup_address_json TEXT NOT NULL,
    email_verified_at TIMESTAMPTZ,
    email_otp_hash VARCHAR(255),
    email_otp_expires_at TIMESTAMPTZ,
    email_otp_sent_at TIMESTAMPTZ,
    email_otp_attempts INTEGER NOT NULL DEFAULT 0 CHECK (email_otp_attempts >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
