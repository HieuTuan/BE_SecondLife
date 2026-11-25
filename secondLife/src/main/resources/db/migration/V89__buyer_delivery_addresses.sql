CREATE TABLE IF NOT EXISTS buyer_delivery_addresses (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    address_json TEXT NOT NULL
);
