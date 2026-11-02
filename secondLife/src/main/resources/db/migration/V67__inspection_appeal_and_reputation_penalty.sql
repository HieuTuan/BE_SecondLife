-- V67: Add seller appeal window (24h) and reputation penalty tracking for FAILED inspections
ALTER TABLE inspection_orders
    ADD COLUMN IF NOT EXISTS appeal_deadline TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS appeal_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS appeal_reason TEXT,
    ADD COLUMN IF NOT EXISTS appeal_evidence_json TEXT,
    ADD COLUMN IF NOT EXISTS appeal_resolved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS appeal_resolved_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS reputation_penalty_applied BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reputation_penalty_points NUMERIC(6, 2) NOT NULL DEFAULT 0.00;

CREATE INDEX IF NOT EXISTS idx_inspection_orders_appeal_sweep
    ON inspection_orders (status, appeal_status, reputation_penalty_applied, appeal_deadline)
    WHERE status = 'FAILED' AND appeal_status = 'NONE' AND reputation_penalty_applied = FALSE;

