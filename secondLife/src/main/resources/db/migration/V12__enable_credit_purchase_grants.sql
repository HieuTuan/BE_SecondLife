INSERT INTO permissions (code, name, description)
VALUES ('CREDIT_PURCHASE_SELF', 'Purchase Own Credits', 'Purchase listing and valuation credits for own account')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.code = 'CREDIT_PURCHASE_SELF' AND r.code IN ('SELLER', 'ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE payment_intents
    ADD CONSTRAINT uq_payment_intents_purchase UNIQUE (purchase_id);

CREATE UNIQUE INDEX uq_credit_ledger_grant_purchase_type
    ON credit_ledger (purchase_id, credit_type)
    WHERE entry_type = 'GRANT' AND purchase_id IS NOT NULL;

ALTER TABLE credit_purchases
    ADD CONSTRAINT ck_credit_purchase_positive_fee CHECK (final_fee > 0);

ALTER TABLE payment_intents
    ADD CONSTRAINT ck_payment_intent_positive_amount CHECK (amount > 0);
