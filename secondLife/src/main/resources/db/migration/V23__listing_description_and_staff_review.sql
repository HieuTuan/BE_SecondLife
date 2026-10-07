ALTER TABLE posts ADD COLUMN description_accepted BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE posts SET description_accepted = TRUE WHERE description IS NOT NULL AND btrim(description) <> '';
ALTER TABLE posts ADD COLUMN review_reason TEXT;
ALTER TABLE posts ADD COLUMN duplicate_post_ids TEXT;
ALTER TABLE posts ADD COLUMN reviewed_by UUID REFERENCES users(id);
ALTER TABLE posts ADD COLUMN reviewed_at TIMESTAMPTZ;
ALTER TABLE post_images ADD COLUMN perceptual_fingerprint TEXT;
CREATE INDEX idx_posts_review_queue ON posts(status, created_at);
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('STAFF', 'ADMIN') AND p.code = 'STAFF_LISTING_REVIEW'
ON CONFLICT(role_id, permission_id) DO NOTHING;
