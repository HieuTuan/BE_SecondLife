-- Reserve a hashed identity key for future seller eligibility and abuse controls.
-- Never store the raw document number in this table.
CREATE TABLE identity_restrictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_number_hash VARCHAR(64) NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_identity_restrictions_expiry CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT ck_identity_restrictions_revocation CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX idx_identity_restrictions_active_document_hash
    ON identity_restrictions (document_number_hash)
    WHERE revoked_at IS NULL;
