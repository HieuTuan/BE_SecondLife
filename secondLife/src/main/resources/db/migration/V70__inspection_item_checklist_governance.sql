SET LOCAL lock_timeout = '5s';

ALTER TABLE items
    ADD COLUMN is_selectable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN replacement_for_item_id UUID REFERENCES items(id),
    ADD COLUMN variant_code VARCHAR(60);
CREATE INDEX ix_items_replacement_for ON items(replacement_for_item_id);

ALTER TABLE inspection_checklist_templates
    ADD COLUMN catalog_item_id UUID REFERENCES items(id),
    ADD COLUMN complexity_level VARCHAR(2),
    ADD COLUMN approval_status VARCHAR(30) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN supersedes_id UUID REFERENCES inspection_checklist_templates(id),
    ADD COLUMN revision INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN reviewed_by UUID REFERENCES users(id),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN reject_reason VARCHAR(2000),
    ADD CONSTRAINT ck_inspection_checklist_complexity
        CHECK (complexity_level IS NULL OR complexity_level IN ('L1','L2','L3','L4')),
    ADD CONSTRAINT ck_inspection_checklist_status
        CHECK (approval_status IN ('LEGACY','PENDING_APPROVAL','APPROVED','REJECTED','WITHDRAWN')),
    ADD CONSTRAINT ck_inspection_checklist_approval
        CHECK (approval_status <> 'APPROVED'
            OR (catalog_item_id IS NOT NULL AND complexity_level IS NOT NULL
                AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    ADD CONSTRAINT ck_inspection_checklist_revision CHECK (revision > 0);
-- Existing templates remain explicitly LEGACY; none is falsely recorded as Admin-approved.
ALTER TABLE inspection_checklist_templates ALTER COLUMN approval_status SET DEFAULT 'PENDING_APPROVAL';
CREATE UNIQUE INDEX uq_inspection_checklist_active_item
    ON inspection_checklist_templates(catalog_item_id)
    WHERE is_active AND approval_status = 'APPROVED';
CREATE INDEX ix_inspection_checklist_author_status
    ON inspection_checklist_templates(created_by,approval_status,created_at DESC);
CREATE INDEX ix_inspection_checklist_supersedes ON inspection_checklist_templates(supersedes_id);
CREATE INDEX ix_inspection_checklist_reviewer ON inspection_checklist_templates(reviewed_by);

ALTER TABLE inspection_fee_proposals
    ADD COLUMN complexity_level VARCHAR(2),
    ADD CONSTRAINT ck_inspection_fee_complexity CHECK
        (complexity_level IS NULL OR complexity_level IN ('L1','L2','L3','L4')),
    ADD CONSTRAINT ck_inspection_fee_level_scope CHECK
        (complexity_level IS NULL OR (category_id IS NULL AND base_inspection_fee >= 0));
CREATE INDEX ix_inspection_fee_center_level
    ON inspection_fee_proposals(proposed_by,complexity_level,status,reviewed_at DESC);

ALTER TABLE inspection_orders
    ADD COLUMN inspection_center_id UUID REFERENCES users(id),
    ADD COLUMN checklist_template_id UUID REFERENCES inspection_checklist_templates(id),
    ADD COLUMN fee_proposal_id UUID REFERENCES inspection_fee_proposals(id),
    ADD COLUMN complexity_level VARCHAR(2),
    ADD COLUMN checklist_snapshot_json TEXT,
    ADD CONSTRAINT ck_inspection_order_complexity CHECK
        (complexity_level IS NULL OR complexity_level IN ('L1','L2','L3','L4'));
CREATE INDEX ix_inspection_orders_center ON inspection_orders(inspection_center_id);
CREATE INDEX ix_inspection_orders_checklist ON inspection_orders(checklist_template_id);
CREATE INDEX ix_inspection_orders_fee_proposal ON inspection_orders(fee_proposal_id);
