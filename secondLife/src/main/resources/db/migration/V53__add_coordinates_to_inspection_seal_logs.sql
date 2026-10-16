-- V53: Add latitude and longitude coordinates to inspection_seal_logs for GPS tracking

ALTER TABLE inspection_seal_logs
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7);
