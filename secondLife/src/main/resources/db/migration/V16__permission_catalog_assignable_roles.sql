-- Only custom permissions use this table; built-in permission policies remain fixed in code.
CREATE TABLE permission_assignable_roles (
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    role_code VARCHAR(50) NOT NULL REFERENCES roles(code),
    PRIMARY KEY (permission_id, role_code),
    CONSTRAINT ck_permission_assignable_roles_no_admin CHECK (role_code <> 'ADMIN')
);
