INSERT INTO permissions (code, name, description)
VALUES ('POST_REVIEW', 'Review Posts', 'Allows approving or rejecting submitted posts')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'POST_REVIEW'
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
