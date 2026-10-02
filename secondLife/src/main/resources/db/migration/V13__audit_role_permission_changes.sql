CREATE TABLE role_permission_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL REFERENCES roles(id),
    permission_code VARCHAR(100) NOT NULL,
    action VARCHAR(10) NOT NULL,
    changed_by UUID NOT NULL REFERENCES users(id),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_role_permission_audit_action CHECK (action IN ('GRANT', 'REVOKE'))
);

CREATE INDEX idx_role_permission_audit_role_time
    ON role_permission_audit (role_id, changed_at DESC);
