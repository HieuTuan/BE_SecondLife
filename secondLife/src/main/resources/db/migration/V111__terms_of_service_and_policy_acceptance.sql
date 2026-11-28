-- V111: Terms of Service & Operating Policy versioning and immutable user acceptance history

CREATE TABLE IF NOT EXISTS policy_versions (
    id UUID PRIMARY KEY,
    policy_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    content_markdown TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requires_reacceptance BOOLEAN NOT NULL DEFAULT TRUE,
    published_at TIMESTAMPTZ,
    effective_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_policy_versions_type CHECK (policy_type IN ('TERMS_OF_SERVICE')),
    CONSTRAINT chk_policy_versions_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_policy_versions_type_version UNIQUE (policy_type, version)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_policy_versions_active_type
    ON policy_versions (policy_type)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_policy_versions_type_status_published
    ON policy_versions (policy_type, status, published_at DESC);

CREATE OR REPLACE FUNCTION prevent_published_policy_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status IN ('ACTIVE', 'ARCHIVED') THEN
            RAISE EXCEPTION 'Published policy versions cannot be deleted';
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.status IN ('ACTIVE', 'ARCHIVED') THEN
        IF NEW.policy_type IS DISTINCT FROM OLD.policy_type
           OR NEW.version IS DISTINCT FROM OLD.version
           OR NEW.title IS DISTINCT FROM OLD.title
           OR NEW.content_markdown IS DISTINCT FROM OLD.content_markdown
           OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
           OR NEW.published_at IS DISTINCT FROM OLD.published_at THEN
            RAISE EXCEPTION 'Published policy content and metadata are immutable';
        END IF;
        IF OLD.status = 'ARCHIVED' AND NEW.status IS DISTINCT FROM OLD.status THEN
            RAISE EXCEPTION 'Archived policy versions cannot change status';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgrelid = 'policy_versions'::regclass
          AND tgname = 'trg_policy_versions_immutable'
    ) THEN
        CREATE TRIGGER trg_policy_versions_immutable
            BEFORE UPDATE OR DELETE ON policy_versions
            FOR EACH ROW
            EXECUTE FUNCTION prevent_published_policy_mutation();
    END IF;
END;
$$;

-- Canonical acceptance per (user, policy_version): guarantees idempotency on retry/concurrency
CREATE TABLE IF NOT EXISTS user_policy_acceptances (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    policy_version_id UUID NOT NULL REFERENCES policy_versions(id),
    first_accepted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(30) NOT NULL,
    context VARCHAR(30) NOT NULL,
    CONSTRAINT uq_user_policy_acceptance UNIQUE (user_id, policy_version_id),
    CONSTRAINT chk_user_policy_acceptance_source CHECK (source IN ('WEB', 'MOBILE_BUYER', 'MOBILE_SELLER', 'MOBILE_INSPECTOR', 'MOBILE', 'API')),
    CONSTRAINT chk_user_policy_acceptance_context CHECK (context IN ('REGISTER', 'LOGIN', 'GOOGLE_LOGIN', 'POLICY_UPDATE'))
);

CREATE INDEX IF NOT EXISTS idx_user_policy_acceptances_user
    ON user_policy_acceptances (user_id, first_accepted_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_policy_acceptances_version
    ON user_policy_acceptances (policy_version_id);

-- Append-only audit log of explicit user confirmation events (never stores credentials or tokens)
CREATE TABLE IF NOT EXISTS user_policy_acceptance_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    policy_version_id UUID NOT NULL REFERENCES policy_versions(id),
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(30) NOT NULL,
    context VARCHAR(30) NOT NULL,
    CONSTRAINT chk_user_policy_event_source CHECK (source IN ('WEB', 'MOBILE_BUYER', 'MOBILE_SELLER', 'MOBILE_INSPECTOR', 'MOBILE', 'API')),
    CONSTRAINT chk_user_policy_event_context CHECK (context IN ('REGISTER', 'LOGIN', 'GOOGLE_LOGIN', 'POLICY_UPDATE'))
);

CREATE INDEX IF NOT EXISTS idx_user_policy_events_user
    ON user_policy_acceptance_events (user_id, accepted_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_policy_events_user_version
    ON user_policy_acceptance_events (user_id, policy_version_id, accepted_at DESC);
