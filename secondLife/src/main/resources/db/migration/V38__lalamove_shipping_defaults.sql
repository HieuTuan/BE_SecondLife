-- New rows use Lalamove; preserve the carrier identity of existing shipments.
ALTER TABLE shipping_quotes ALTER COLUMN provider SET DEFAULT 'LALAMOVE';
ALTER TABLE shipments ALTER COLUMN provider SET DEFAULT 'LALAMOVE';
