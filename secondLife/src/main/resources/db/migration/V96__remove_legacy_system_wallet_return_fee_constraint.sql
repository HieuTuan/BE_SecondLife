-- Hibernate schema-update could leave a duplicate UNIQUE (order_id, type) constraint.
-- V95 still enforces uniqueness for the other order transaction types via a partial index.
ALTER TABLE system_wallet_transactions DROP CONSTRAINT IF EXISTS uk7nwe35xaqcxui3ajku36kf42t;
