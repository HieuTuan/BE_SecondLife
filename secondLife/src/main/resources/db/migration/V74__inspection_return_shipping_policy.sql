CREATE TABLE inspection_return_shipping_policy (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    markup_percentage NUMERIC(5, 2) NOT NULL CHECK (markup_percentage BETWEEN 0 AND 100)
);

INSERT INTO inspection_return_shipping_policy (id, markup_percentage) VALUES (1, 20.00);

ALTER TABLE inspection_fee_settlements ADD COLUMN return_shipping_settled_at TIMESTAMPTZ;
