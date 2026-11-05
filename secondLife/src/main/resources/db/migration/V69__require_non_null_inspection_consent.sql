-- Missing historical consent means consent was not granted. Never infer acceptance.
-- Flyway runs these changes atomically; acquire the table lock before normalizing rows.
SET LOCAL lock_timeout = '5s';

ALTER TABLE inspection_orders
    ALTER COLUMN buyer_consent_zin SET DEFAULT FALSE,
    ALTER COLUMN seller_consent_zin SET DEFAULT FALSE;

UPDATE inspection_orders
SET buyer_consent_zin = COALESCE(buyer_consent_zin, FALSE),
    seller_consent_zin = COALESCE(seller_consent_zin, FALSE)
WHERE buyer_consent_zin IS NULL OR seller_consent_zin IS NULL;

ALTER TABLE inspection_orders
    ALTER COLUMN buyer_consent_zin SET NOT NULL,
    ALTER COLUMN seller_consent_zin SET NOT NULL;
