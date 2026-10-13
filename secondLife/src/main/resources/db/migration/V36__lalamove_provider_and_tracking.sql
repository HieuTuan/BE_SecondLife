-- Preserve historical GHN routes; new routes keep their own carrier identity.
ALTER TABLE shipping_quotes ADD COLUMN IF NOT EXISTS provider VARCHAR(20) NOT NULL DEFAULT 'GHN';
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS provider VARCHAR(20) NOT NULL DEFAULT 'GHN';
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS driver_id VARCHAR(100);
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS share_link TEXT;
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS last_reconciled_at TIMESTAMPTZ;
ALTER TABLE shipments DROP CONSTRAINT IF EXISTS shipments_order_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_shipment_provider_order ON shipments(provider, order_code) WHERE order_code IS NOT NULL;
