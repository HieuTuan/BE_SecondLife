-- V3__evolve_seller_verification_and_events.sql
-- Evolve seller verification table for eKYC & Risk Decision Engine, and add audit events table

-- 1. Alter seller_verifications table with eKYC, Risk, and Security fields
ALTER TABLE seller_verifications
    ADD COLUMN IF NOT EXISTS ekyc_status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS risk_status VARCHAR(50) NOT NULL DEFAULT 'NOT_EVALUATED',
    ADD COLUMN IF NOT EXISTS review_source VARCHAR(50),
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provider_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provider_reference_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS document_number_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS document_number_masked VARCHAR(50),
    ADD COLUMN IF NOT EXISTS selfie_url VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS face_match_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS liveness_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS document_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS risk_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS resubmission_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ekyc_completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS risk_evaluated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Update default status for new submissions and migrate existing legacy PENDING to NEEDS_REVIEW
ALTER TABLE seller_verifications ALTER COLUMN status SET DEFAULT 'SUBMITTED';
UPDATE seller_verifications SET status = 'NEEDS_REVIEW' WHERE status = 'PENDING';

-- Create performance indexes for eKYC, risk, and fraud lookup
CREATE INDEX IF NOT EXISTS idx_seller_verifications_doc_hash ON seller_verifications (document_number_hash);
CREATE INDEX IF NOT EXISTS idx_seller_verifications_ekyc_status ON seller_verifications (ekyc_status);
CREATE INDEX IF NOT EXISTS idx_seller_verifications_risk_status ON seller_verifications (risk_status);
CREATE INDEX IF NOT EXISTS idx_seller_verifications_reason_code ON seller_verifications (reason_code);

-- 2. Create seller_verification_events table for comprehensive audit trail
CREATE TABLE IF NOT EXISTS seller_verification_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    verification_id UUID NOT NULL REFERENCES seller_verifications(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    reason_code VARCHAR(100),
    actor_type VARCHAR(50) NOT NULL,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_seller_verification_events_verification_id ON seller_verification_events (verification_id);
CREATE INDEX IF NOT EXISTS idx_seller_verification_events_event_type ON seller_verification_events (event_type);
CREATE INDEX IF NOT EXISTS idx_seller_verification_events_created_at ON seller_verification_events (created_at);
