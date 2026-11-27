ALTER TABLE user_wallets ADD COLUMN IF NOT EXISTS reserved_balance NUMERIC(18,2) NOT NULL DEFAULT 0;

ALTER TABLE wallet_withdrawals
    ADD COLUMN IF NOT EXISTS request_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS bank_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS bank_info_confirmed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS bank_info_warning_version VARCHAR(40),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS accepted_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS transferred_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS transferred_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS notification_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dispute_deadline TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS evidence_url VARCHAR(300),
    ADD COLUMN IF NOT EXISTS evidence_storage_id VARCHAR(220),
    ADD COLUMN IF NOT EXISTS evidence_content_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS evidence_uploaded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS evidence_uploaded_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS dispute_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS dispute_description TEXT,
    ADD COLUMN IF NOT EXISTS disputed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dispute_resolved_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS dispute_resolution TEXT;

UPDATE wallet_withdrawals SET request_code = 'SLWD-' || upper(substr(replace(id::text, '-', ''), 1, 12)) WHERE request_code IS NULL;
UPDATE wallet_withdrawals SET bank_name = bank_code WHERE bank_name IS NULL;
ALTER TABLE wallet_withdrawals ALTER COLUMN status SET DEFAULT 'PENDING';
-- Stop for reconciliation if the old debit cannot be matched to the owner's ledger.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM wallet_withdrawals wd
        WHERE upper(wd.status) IN ('PENDING_REVIEW','PENDING','PROCESSING','TRANSFER_FAILED')
          AND (SELECT count(*) FROM wallet_transactions t JOIN user_wallets w ON w.id = t.wallet_id
               WHERE w.user_id = wd.user_id AND t.reference_id = wd.id AND t.type = 'WITHDRAW'
                 AND t.amount = -wd.amount) <> 1
    ) THEN RAISE EXCEPTION 'Withdrawal reserve backfill requires ledger reconciliation';
    END IF;
END $$;
UPDATE wallet_withdrawals SET status = CASE upper(status)
    WHEN 'PENDING_REVIEW' THEN 'PENDING'
    WHEN 'APPROVED' THEN 'COMPLETED'
    ELSE upper(status) END;
-- Legacy pending requests already had their amount deducted from balance; expose them as reserved.
UPDATE user_wallets w SET reserved_balance = COALESCE((
    SELECT SUM(wd.amount) FROM wallet_withdrawals wd
    WHERE wd.user_id = w.user_id AND wd.status IN ('PENDING', 'PROCESSING', 'TRANSFER_FAILED')
), 0);
CREATE UNIQUE INDEX IF NOT EXISTS ux_wallet_withdrawals_request_code ON wallet_withdrawals(request_code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_wallet_withdrawals_idempotency ON wallet_withdrawals(user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_wallet_withdrawals_dispute_deadline ON wallet_withdrawals(dispute_deadline) WHERE status = 'TRANSFERRED_AWAITING_REVIEW';

CREATE TABLE IF NOT EXISTS wallet_withdrawal_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    withdrawal_id UUID NOT NULL REFERENCES wallet_withdrawals(id),
    old_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    actor_id UUID REFERENCES users(id),
    action VARCHAR(60) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_withdrawal_history_id_time ON wallet_withdrawal_status_history(withdrawal_id, created_at);

INSERT INTO wallet_withdrawal_status_history(withdrawal_id, old_status, new_status, action, reason, created_at)
SELECT id, NULL, status, 'MIGRATION_BACKFILL', 'Status before withdrawal lifecycle audit', created_at
FROM wallet_withdrawals;

