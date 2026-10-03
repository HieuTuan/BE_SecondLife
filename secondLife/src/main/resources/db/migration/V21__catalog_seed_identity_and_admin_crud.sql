-- New catalog metadata; V19/V20 remain unchanged after application.
ALTER TABLE categories ADD COLUMN seed_key VARCHAR(100);
ALTER TABLE items ADD COLUMN seed_key VARCHAR(100);
CREATE UNIQUE INDEX uq_categories_seed_key ON categories(seed_key);
CREATE UNIQUE INDEX uq_items_seed_key ON items(seed_key);
CREATE INDEX idx_items_category ON items(category_id);
-- Existing rows are retained, even if legacy data has orphaned IDs. New writes and
-- deletes are checked by PostgreSQL to prevent races between catalog CRUD and drafts.
ALTER TABLE posts ADD CONSTRAINT fk_posts_catalog_category FOREIGN KEY(category_id) REFERENCES categories(id) NOT VALID;
CREATE UNIQUE INDEX uq_items_id_category ON items(id, category_id);
ALTER TABLE posts ADD CONSTRAINT fk_posts_catalog_item_category FOREIGN KEY(item_id, category_id) REFERENCES items(id, category_id) NOT VALID;
ALTER TABLE category_question_templates ADD CONSTRAINT fk_templates_catalog_category FOREIGN KEY(category_id) REFERENCES categories(id) NOT VALID;
ALTER TABLE category_question_templates ADD CONSTRAINT fk_templates_catalog_item_category FOREIGN KEY(item_id, category_id) REFERENCES items(id, category_id) NOT VALID;

INSERT INTO permissions(id, code, name, description)
VALUES(gen_random_uuid(), 'ADMIN_CATALOG_MANAGE', 'Manage Product Catalog', 'Create, update and delete categories and product item types')
ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND p.code = 'ADMIN_CATALOG_MANAGE'
ON CONFLICT(role_id, permission_id) DO NOTHING;
