CREATE TABLE report_evidence_uploads (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    storage_id VARCHAR(200) NOT NULL UNIQUE,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('image/jpeg','image/png','image/webp')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    report_id UUID REFERENCES user_reports(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_report_evidence_report ON report_evidence_uploads(report_id);
