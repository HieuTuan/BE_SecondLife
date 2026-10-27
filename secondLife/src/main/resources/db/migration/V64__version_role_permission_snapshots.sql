-- Version cached authorities without changing JWT claims or revoking valid sessions.
ALTER TABLE roles ADD COLUMN permissions_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE roles ADD CONSTRAINT chk_roles_permissions_version_nonnegative
    CHECK (permissions_version >= 0);
