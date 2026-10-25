CREATE TABLE video_verification_windows (
    id VARCHAR(80) PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL,
    calls INTEGER NOT NULL CHECK (calls >= 0)
);
