-- V52: Create inspection seal logs, geocoding cache, and add unique constraint on sepay_transaction_id

-- 1. Unique constraint on deposit_requests(sepay_transaction_id) to prevent replay race conditions
CREATE UNIQUE INDEX IF NOT EXISTS idx_deposit_requests_sepay_transaction_id 
    ON deposit_requests(sepay_transaction_id) 
    WHERE sepay_transaction_id IS NOT NULL;

-- 2. Inspection Seal Logs for QR Tamper Seal verification
CREATE TABLE IF NOT EXISTS inspection_seal_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_order_id UUID NOT NULL REFERENCES inspection_orders(id) ON DELETE CASCADE,
    tamper_seal_id VARCHAR(100) NOT NULL,
    scanned_by UUID REFERENCES users(id) ON DELETE SET NULL,
    scan_stage VARCHAR(50) NOT NULL, -- INITIAL_SEAL, CARRIER_HANDOVER, DELIVERED_BUYER
    seal_condition VARCHAR(30) NOT NULL, -- INTACT, TAMPERED
    location_text VARCHAR(255),
    note TEXT,
    scanned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inspection_seal_logs_tamper_seal_id ON inspection_seal_logs(tamper_seal_id);
CREATE INDEX IF NOT EXISTS idx_inspection_seal_logs_order_id ON inspection_seal_logs(inspection_order_id);

-- 3. Geocoded Locations Cache to prevent Lalamove shipping rate calculation bottlenecks
CREATE TABLE IF NOT EXISTS geocoded_locations_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lat_key NUMERIC(7, 4) NOT NULL,
    lon_key NUMERIC(7, 4) NOT NULL,
    formatted_address VARCHAR(500) NOT NULL,
    city VARCHAR(50) NOT NULL,
    country VARCHAR(10) NOT NULL DEFAULT 'VN',
    place_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_geocoded_locations_cache_coords UNIQUE (lat_key, lon_key)
);

CREATE INDEX IF NOT EXISTS idx_geocoded_locations_cache_coords ON geocoded_locations_cache(lat_key, lon_key);
