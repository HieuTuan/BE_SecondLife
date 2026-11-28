-- Migration V110: Wallet status guard, structured dispute evidence/outcomes, and post-48h exception requests
ALTER TABLE user_wallets
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE wallet_withdrawals
    ADD COLUMN IF NOT EXISTS dispute_evidence_urls TEXT,
    ADD COLUMN IF NOT EXISTS dispute_resolution_outcome VARCHAR(40),
    ADD COLUMN IF NOT EXISTS dispute_resolution_evidence_url TEXT,
    ADD COLUMN IF NOT EXISTS exception_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS exception_reason TEXT,
    ADD COLUMN IF NOT EXISTS exception_evidence_urls TEXT;

CREATE INDEX IF NOT EXISTS idx_wallet_withdrawals_user_status ON wallet_withdrawals(user_id, status);
CREATE INDEX IF NOT EXISTS idx_wallet_withdrawals_exception_requested ON wallet_withdrawals(exception_requested_at) WHERE exception_requested_at IS NOT NULL;
