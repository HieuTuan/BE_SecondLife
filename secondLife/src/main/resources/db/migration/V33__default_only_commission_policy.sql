-- Keep historical scoped policies and immutable order snapshots for audit.
-- New orders can only use the ADMIN-configured default policy.
UPDATE commission_rules
SET active = false, revision = revision + 1, updated_at = now()
WHERE active AND type <> 'DEFAULT';

ALTER TABLE commission_rules ADD CONSTRAINT ck_commission_active_default_only
    CHECK (NOT active OR type = 'DEFAULT');
