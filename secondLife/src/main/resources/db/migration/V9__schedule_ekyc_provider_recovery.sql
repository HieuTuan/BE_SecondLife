ALTER TABLE seller_verifications
    ADD COLUMN recovery_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_retried_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_seller_verifications_due_recovery
    ON seller_verifications (next_retry_at)
    WHERE status = 'EKYC_PENDING' AND ekyc_status = 'PROVIDER_ERROR';
