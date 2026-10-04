-- Preserve databases where the legacy wallet/order entities were created by Hibernate.
CREATE TABLE IF NOT EXISTS negotiations (
  id UUID PRIMARY KEY, post_id UUID NOT NULL REFERENCES posts(id), buyer_id UUID NOT NULL REFERENCES users(id),
  offered_price NUMERIC(18,2) NOT NULL, status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, expired_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS orders (
  id UUID PRIMARY KEY, post_id UUID NOT NULL REFERENCES posts(id), buyer_id UUID NOT NULL REFERENCES users(id),
  seller_id UUID NOT NULL REFERENCES users(id), negotiation_id UUID REFERENCES negotiations(id),
  final_price NUMERIC(18,2) NOT NULL, status VARCHAR(20) NOT NULL, escrow_status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS user_wallets (
  id UUID PRIMARY KEY, user_id UUID NOT NULL UNIQUE REFERENCES users(id), balance NUMERIC(18,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS wallet_transactions (
  id UUID PRIMARY KEY, wallet_id UUID NOT NULL REFERENCES user_wallets(id), amount NUMERIC(18,2) NOT NULL,
  type VARCHAR(20) NOT NULL, reference_id UUID, created_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN ('PENDING_PAYMENT','PROCESSING','SHIPPED','DELIVERED','COMPLETED','CANCELLED'));
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_escrow_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_escrow_status_check CHECK (escrow_status IN ('HELD','FROZEN','RELEASED','REFUNDED'));
ALTER TABLE posts ADD COLUMN IF NOT EXISTS shipping_weight INTEGER;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS shipping_length INTEGER;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS shipping_width INTEGER;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS shipping_height INTEGER;

-- Clean up any empty partial tables that may have been created by Hibernate ddl-auto before migration
DROP TABLE IF EXISTS shipment_events CASCADE;
DROP TABLE IF EXISTS shipments CASCADE;
DROP TABLE IF EXISTS shipping_quotes CASCADE;
DROP TABLE IF EXISTS shipping_pickup_addresses CASCADE;

CREATE TABLE IF NOT EXISTS shipping_pickup_addresses (
  user_id UUID PRIMARY KEY REFERENCES users(id), address_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS shipping_quotes (
  id UUID PRIMARY KEY, buyer_id UUID NOT NULL REFERENCES users(id), post_id UUID NOT NULL REFERENCES posts(id),
  payload TEXT NOT NULL, delivery_address TEXT NOT NULL, seller_pickup_address TEXT NOT NULL, leg VARCHAR(40) NOT NULL DEFAULT 'SELLER_TO_BUYER', fee NUMERIC(18,2) NOT NULL CHECK (fee>=0),
  expected_delivery_time TIMESTAMPTZ, expires_at TIMESTAMPTZ NOT NULL, consumed_order_id UUID REFERENCES orders(id), created_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_quote_id UUID;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_shipping_quote_id_fkey;
ALTER TABLE orders ADD CONSTRAINT orders_shipping_quote_id_fkey FOREIGN KEY (shipping_quote_id) REFERENCES shipping_quotes(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_fee NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_address TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_delivered_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS request_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uq_order_buyer_request ON orders(buyer_id,request_id) WHERE request_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_order_shipping_quote ON orders(shipping_quote_id) WHERE shipping_quote_id IS NOT NULL;
CREATE TABLE IF NOT EXISTS shipments (
  id UUID PRIMARY KEY, order_id UUID REFERENCES orders(id), inspection_order_id UUID REFERENCES inspection_orders(id),
  seller_id UUID NOT NULL REFERENCES users(id), buyer_id UUID REFERENCES users(id),
  leg VARCHAR(40) NOT NULL, request_id UUID NOT NULL, client_order_code VARCHAR(50) NOT NULL UNIQUE,
  order_code VARCHAR(100) UNIQUE, status VARCHAR(40) NOT NULL, provider_status VARCHAR(40),
  payload TEXT NOT NULL, reason TEXT, requested_by UUID REFERENCES users(id),
  quoted_fee NUMERIC(18,2), actual_fee NUMERIC(18,2), expected_delivery_time TIMESTAMPTZ,
  last_event_at TIMESTAMPTZ, last_fee_event_at TIMESTAMPTZ, delivered_at TIMESTAMPTZ, pod_url TEXT, last_error VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_shipment_parent CHECK ((order_id IS NULL) <> (inspection_order_id IS NULL)),
  CONSTRAINT ck_shipment_leg CHECK (leg IN ('SELLER_TO_BUYER','SELLER_TO_CENTER','CENTER_TO_BUYER','BUYER_TO_SELLER'))
);
CREATE INDEX IF NOT EXISTS idx_shipments_order ON shipments(order_id,created_at);
CREATE INDEX IF NOT EXISTS idx_shipments_inspection ON shipments(inspection_order_id,created_at);
CREATE TABLE IF NOT EXISTS shipment_events (
  id UUID PRIMARY KEY, shipment_id UUID NOT NULL REFERENCES shipments(id), event_key VARCHAR(64) NOT NULL UNIQUE,
  type VARCHAR(100) NOT NULL, status VARCHAR(40), occurred_at TIMESTAMPTZ NOT NULL, reason TEXT, payload TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_shipment_events_timeline ON shipment_events(shipment_id,occurred_at);
