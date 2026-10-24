ALTER TABLE report_evidence_uploads DROP CONSTRAINT report_evidence_uploads_content_type_check;
ALTER TABLE report_evidence_uploads ADD CONSTRAINT report_evidence_uploads_content_type_check
    CHECK (content_type IN ('image/jpeg','image/png','image/webp','video/mp4','video/quicktime','video/webm'));

CREATE TABLE video_upload_sessions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    request_id UUID NOT NULL,
    public_id VARCHAR(200) NOT NULL UNIQUE,
    signature_data JSONB NOT NULL CHECK (jsonb_typeof(signature_data) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    completed_at TIMESTAMPTZ,
    video_url VARCHAR(2048),
    asset_id VARCHAR(200) UNIQUE,
    size_bytes BIGINT,
    format VARCHAR(10),
    duration DOUBLE PRECISION,
    provider_version BIGINT,
    UNIQUE (owner_id,request_id),
    CHECK (completed_at IS NULL OR (video_url IS NOT NULL AND asset_id IS NOT NULL
        AND size_bytes IS NOT NULL AND size_bytes > 0 AND format IN ('mp4','mov','webm')
        AND duration IS NOT NULL AND duration > 0 AND duration < 'Infinity'::float8
        AND provider_version IS NOT NULL AND provider_version > 0))
);
CREATE INDEX idx_video_upload_owner_created ON video_upload_sessions(owner_id,created_at);
