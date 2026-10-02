-- Staff may inspect verification exceptions, but only Admin may approve, reject or retry them.
DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id AND rp.permission_id = p.id
  AND r.code = 'STAFF' AND p.code = 'SELLER_VERIFICATION_REVIEW';
