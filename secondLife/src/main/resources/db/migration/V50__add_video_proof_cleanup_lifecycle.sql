-- Migration V50: Add video proof cleanup lifecycle fields to inspection_orders
ALTER TABLE inspection_orders
    ADD COLUMN IF NOT EXISTS video_proof_public_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS unboxing_video_public_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS video_cleanup_status VARCHAR(30) DEFAULT 'ACTIVE', -- ACTIVE, CLEANED, EXEMPTED
    ADD COLUMN IF NOT EXISTS video_cleaned_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS video_retention_until TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_inspection_orders_video_cleanup 
    ON inspection_orders(video_cleanup_status, video_retention_until);
