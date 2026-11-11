ALTER TABLE inspection_orders
    ADD COLUMN home_check_in_code UUID,
    ADD COLUMN home_checked_in_at TIMESTAMPTZ,
    ADD COLUMN evidence_photo_urls JSONB;
