ALTER TABLE user_bank_accounts
    ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY updated_at DESC, id DESC) AS rn
    FROM user_bank_accounts
)
UPDATE user_bank_accounts u
SET is_primary = (r.rn = 1)
FROM ranked r
WHERE u.id = r.id;

CREATE TABLE IF NOT EXISTS user_bank_account_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    bank_account_id UUID,
    action VARCHAR(30) NOT NULL,
    previous_bank_code VARCHAR(20),
    previous_bank_name VARCHAR(120),
    previous_account_number VARCHAR(30),
    previous_account_holder_name VARCHAR(150),
    bank_code VARCHAR(20),
    bank_name VARCHAR(120),
    account_number VARCHAR(30),
    account_holder_name VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_bank_account_history_user_time
    ON user_bank_account_history(user_id, created_at DESC);

INSERT INTO user_bank_account_history (
    user_id, bank_account_id, action,
    bank_code, bank_name, account_number, account_holder_name, created_at
)
SELECT u.user_id, u.id, 'MIGRATION_BACKFILL',
       u.bank_code, u.bank_name, u.account_number, u.account_holder_name, u.created_at
FROM user_bank_accounts u
WHERE NOT EXISTS (
    SELECT 1 FROM user_bank_account_history h WHERE h.bank_account_id = u.id
);
