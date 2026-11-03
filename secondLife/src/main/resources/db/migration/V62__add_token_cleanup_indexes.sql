-- V62__add_token_cleanup_indexes.sql
-- Indexes to optimize scheduled deletion of expired and revoked tokens

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at 
    ON refresh_tokens (expires_at);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked_at 
    ON refresh_tokens (revoked_at) 
    WHERE revoked_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_cleanup 
    ON email_verification_tokens (expires_at, used_at);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_cleanup 
    ON password_reset_tokens (expires_at, used_at);
