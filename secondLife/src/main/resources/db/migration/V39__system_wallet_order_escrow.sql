CREATE TABLE system_wallets (
    id VARCHAR(20) PRIMARY KEY CHECK (id = 'MAIN'),
    held_balance NUMERIC(18,2) NOT NULL CHECK (held_balance >= 0),
    shipping_balance NUMERIC(18,2) NOT NULL CHECK (shipping_balance >= 0),
    revenue_balance NUMERIC(18,2) NOT NULL CHECK (revenue_balance >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'VND'),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE system_order_funds (
    order_id UUID PRIMARY KEY REFERENCES orders(id),
    product_held NUMERIC(18,2) NOT NULL CHECK (product_held >= 0),
    shipping_held NUMERIC(18,2) NOT NULL CHECK (shipping_held >= 0)
);

CREATE TABLE system_wallet_transactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    type VARCHAR(30) NOT NULL CHECK (type IN ('OPENING_BALANCE', 'PAYMENT_HOLD', 'SETTLEMENT', 'REFUND')),
    held_delta NUMERIC(18,2) NOT NULL,
    shipping_delta NUMERIC(18,2) NOT NULL,
    revenue_delta NUMERIC(18,2) NOT NULL,
    held_balance_after NUMERIC(18,2) NOT NULL CHECK (held_balance_after >= 0),
    shipping_balance_after NUMERIC(18,2) NOT NULL CHECK (shipping_balance_after >= 0),
    revenue_balance_after NUMERIC(18,2) NOT NULL CHECK (revenue_balance_after >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (order_id, type)
);

-- Reconcile existing paid orders from actual debits, refunds and seller earnings.
-- Never create held money merely from an order row that has no payment history.
CREATE TEMPORARY TABLE system_wallet_opening ON COMMIT DROP AS
WITH cash AS (
    SELECT o.id, o.created_at, o.final_price, o.shipping_fee, o.escrow_status,
           -SUM(t.amount) AS retained_cash
    FROM orders o JOIN wallet_transactions t ON t.reference_id = o.id
    WHERE t.type IN ('PAYMENT', 'REFUND', 'EARNING')
    GROUP BY o.id
    HAVING COUNT(*) FILTER (WHERE t.type = 'PAYMENT' AND t.amount < 0) > 0
), product AS (
    SELECT *, CASE WHEN escrow_status IN ('HELD', 'FROZEN') THEN final_price ELSE 0 END AS product_held
    FROM cash
), shipping AS (
    SELECT *, LEAST(shipping_fee, GREATEST(retained_cash - product_held, 0)) AS shipping_held
    FROM product
)
SELECT *, retained_cash - product_held - shipping_held AS revenue FROM shipping;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM system_wallet_opening
               WHERE retained_cash < product_held OR product_held < 0 OR shipping_held < 0 OR revenue < 0) THEN
        RAISE EXCEPTION 'Existing order payment history cannot cover escrow; reconcile wallet transactions before migration';
    END IF;
END $$;

INSERT INTO system_order_funds (order_id, product_held, shipping_held)
SELECT id, product_held, shipping_held FROM system_wallet_opening;

INSERT INTO system_wallet_transactions (order_id, type, held_delta, shipping_delta, revenue_delta,
    held_balance_after, shipping_balance_after, revenue_balance_after, created_at)
SELECT id, 'OPENING_BALANCE', product_held, shipping_held, revenue,
       SUM(product_held) OVER (ORDER BY created_at, id),
       SUM(shipping_held) OVER (ORDER BY created_at, id),
       SUM(revenue) OVER (ORDER BY created_at, id), created_at
FROM system_wallet_opening ORDER BY created_at, id;

INSERT INTO system_wallets (id, held_balance, shipping_balance, revenue_balance, currency, updated_at)
SELECT 'MAIN', COALESCE(SUM(product_held), 0), COALESCE(SUM(shipping_held), 0), COALESCE(SUM(revenue), 0), 'VND', now()
FROM system_wallet_opening;

CREATE FUNCTION prevent_system_wallet_transaction_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'System wallet transactions are immutable';
END $$;
CREATE TRIGGER trg_system_wallet_transaction_immutable BEFORE UPDATE OR DELETE ON system_wallet_transactions
FOR EACH ROW EXECUTE FUNCTION prevent_system_wallet_transaction_mutation();

CREATE INDEX idx_orders_due_settlement ON orders (shipping_delivered_at, id)
WHERE status = 'DELIVERED' AND escrow_status = 'HELD';
