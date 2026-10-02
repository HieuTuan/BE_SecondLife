-- Access tokens issued before a security-sensitive account change must stop working immediately.
ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
