ALTER TABLE orders ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'WALLET'
    CHECK (payment_method IN ('WALLET','BANK_TRANSFER'));
ALTER TABLE orders DROP CONSTRAINT orders_escrow_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_escrow_status_check
    CHECK (escrow_status IN ('PENDING','HELD','FROZEN','RELEASED','REFUNDED'));

CREATE TABLE IF NOT EXISTS deposit_requests (
    id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), amount NUMERIC(18,2) NOT NULL,
    code VARCHAR(255) NOT NULL UNIQUE, status VARCHAR(255) NOT NULL,
    sepay_transaction_id BIGINT, created_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE deposit_requests ADD COLUMN order_id UUID UNIQUE REFERENCES orders(id);
ALTER TABLE deposit_requests ADD COLUMN expires_at TIMESTAMPTZ;
ALTER TABLE deposit_requests ADD COLUMN received_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (received_amount>=0);
ALTER TABLE deposit_requests ADD COLUMN allocated_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (allocated_amount>=0);
ALTER TABLE deposit_requests ADD COLUMN wallet_credited_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (wallet_credited_amount>=0);
ALTER TABLE deposit_requests ADD CONSTRAINT ck_order_bank_payment
    CHECK (order_id IS NULL OR (expires_at IS NOT NULL AND amount>0 AND allocated_amount<=amount
        AND allocated_amount+wallet_credited_amount<=received_amount));
CREATE INDEX idx_bank_payment_expiry ON deposit_requests(expires_at,order_id) WHERE order_id IS NOT NULL;
CREATE UNIQUE INDEX uq_pending_payment_post ON orders(post_id) WHERE status='PENDING_PAYMENT';

CREATE TABLE sepay_transfer_receipts (
    transaction_id BIGINT PRIMARY KEY CHECK (transaction_id>0), code VARCHAR(255) NOT NULL,
    amount NUMERIC(18,2) NOT NULL CHECK (amount>0), received_at TIMESTAMPTZ NOT NULL
);
-- Prevent replay of wallet deposits processed before this feature was installed.
INSERT INTO sepay_transfer_receipts(transaction_id,code,amount,received_at)
SELECT sepay_transaction_id,code,amount,created_at FROM deposit_requests
WHERE sepay_transaction_id IS NOT NULL AND sepay_transaction_id>0 AND amount>0
ON CONFLICT(transaction_id) DO NOTHING;
