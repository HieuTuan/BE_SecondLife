CREATE TABLE inspection_shipping_quotes (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id),
    inspection_center_id UUID NOT NULL REFERENCES users(id),
    outbound_payload TEXT NOT NULL,
    return_payload TEXT NOT NULL,
    outbound_response TEXT NOT NULL,
    return_response TEXT NOT NULL,
    outbound_fee NUMERIC(18, 2) NOT NULL CHECK (outbound_fee >= 0),
    return_fee NUMERIC(18, 2) NOT NULL CHECK (return_fee >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_inspection_shipping_quotes_post ON inspection_shipping_quotes(post_id);
CREATE INDEX idx_inspection_shipping_quotes_center ON inspection_shipping_quotes(inspection_center_id);
ALTER TABLE inspection_orders ADD COLUMN inspection_shipping_quote_id UUID
    REFERENCES inspection_shipping_quotes(id);
CREATE UNIQUE INDEX uq_inspection_order_shipping_quote ON inspection_orders(inspection_shipping_quote_id)
    WHERE inspection_shipping_quote_id IS NOT NULL;
