CREATE TABLE user_role_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    role_code VARCHAR(50) NOT NULL REFERENCES roles(code),
    action VARCHAR(10) NOT NULL CHECK (action IN ('GRANT', 'REVOKE')),
    changed_by UUID NOT NULL REFERENCES users(id),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_role_audit_user_changed ON user_role_audit (user_id, changed_at DESC);

-- Existing Hibernate-managed schemas may not retain the SQL UUID default on permissions.id.
INSERT INTO permissions (id, code, name, description) VALUES
    (gen_random_uuid(), 'INSPECTION_ORDER_READ_SELF', 'Read Assigned Inspections', 'Read inspection orders assigned to the current inspector'),
    (gen_random_uuid(), 'INSPECTION_ORDER_READ_ANY', 'Read All Inspections', 'Read and filter inspection orders for management'),
    (gen_random_uuid(), 'INSPECTION_ORDER_ASSIGN', 'Assign Inspection Orders', 'Assign orders to active inspectors'),
    (gen_random_uuid(), 'INSPECTION_STAFF_MANAGE', 'Manage Inspectors', 'Create and list inspector accounts'),
    (gen_random_uuid(), 'AI_CHAT_SELF', 'Use Own AI Chat', 'Use AI chat sessions owned by the current user'),
    (gen_random_uuid(), 'MEDIA_UPLOAD_SELF', 'Upload Media', 'Upload media for the current user')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE (r.code = 'ADMIN' AND p.code IN ('INSPECTION_ORDER_READ_SELF', 'INSPECTION_ORDER_READ_ANY',
       'INSPECTION_ORDER_ASSIGN', 'INSPECTION_STAFF_MANAGE', 'AI_CHAT_SELF', 'MEDIA_UPLOAD_SELF'))
   OR (r.code IN ('BUYER', 'SELLER', 'STAFF', 'INSPECTOR', 'INSPECTION_CENTER') AND p.code IN ('AI_CHAT_SELF', 'MEDIA_UPLOAD_SELF'))
   OR (r.code = 'INSPECTOR' AND p.code = 'INSPECTION_ORDER_READ_SELF')
   OR (r.code = 'INSPECTION_CENTER' AND p.code IN ('INSPECTION_ORDER_READ_ANY', 'INSPECTION_ORDER_ASSIGN', 'INSPECTION_STAFF_MANAGE'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
