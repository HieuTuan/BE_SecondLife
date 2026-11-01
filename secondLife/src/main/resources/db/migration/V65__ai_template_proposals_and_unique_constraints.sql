-- Deduplicate historical category_question_templates while preserving posts.template_id references.
WITH ranked AS (
    SELECT id,
           FIRST_VALUE(id) OVER (
               PARTITION BY category_id, item_id
               ORDER BY id
           ) AS canonical_id
    FROM category_question_templates
),
duplicates AS (
    SELECT id, canonical_id
    FROM ranked
    WHERE id <> canonical_id
),
repointed AS (
    UPDATE posts p
    SET template_id = d.canonical_id
    FROM duplicates d
    WHERE p.template_id = d.id
)
DELETE FROM category_question_templates t
USING duplicates d
WHERE t.id = d.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_category_question_templates_cat_item
    ON category_question_templates(category_id, item_id)
    WHERE item_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_category_question_templates_cat_only
    ON category_question_templates(category_id)
    WHERE item_id IS NULL;

CREATE TABLE category_question_template_proposals (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    item_id UUID,
    template_text TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    triggered_by UUID REFERENCES users(id) ON DELETE SET NULL,
    request_count INT NOT NULL DEFAULT 1,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_template_proposals_item_category
        FOREIGN KEY (item_id, category_id) REFERENCES items(id, category_id) ON DELETE CASCADE,
    CONSTRAINT chk_template_proposals_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_template_proposals_request_count_positive
        CHECK (request_count >= 1)
);

CREATE UNIQUE INDEX uq_template_proposals_pending_cat_item
    ON category_question_template_proposals(category_id, item_id)
    WHERE status = 'PENDING' AND item_id IS NOT NULL;

CREATE UNIQUE INDEX uq_template_proposals_pending_cat_only
    ON category_question_template_proposals(category_id)
    WHERE status = 'PENDING' AND item_id IS NULL;

CREATE INDEX idx_template_proposals_status_updated
    ON category_question_template_proposals(status, updated_at DESC);

CREATE INDEX idx_template_proposals_item_category
    ON category_question_template_proposals(item_id, category_id);

CREATE INDEX idx_template_proposals_triggered_by
    ON category_question_template_proposals(triggered_by);

CREATE INDEX idx_template_proposals_reviewed_by
    ON category_question_template_proposals(reviewed_by);
