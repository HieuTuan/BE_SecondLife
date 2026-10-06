ALTER TABLE seller_onboarding ADD COLUMN pickup_province_id INTEGER;
ALTER TABLE seller_onboarding ADD COLUMN pickup_ward_id INTEGER;
ALTER TABLE seller_onboarding ADD CONSTRAINT ck_seller_pickup_province_positive
    CHECK (pickup_province_id IS NULL OR pickup_province_id > 0);
ALTER TABLE seller_onboarding ADD CONSTRAINT ck_seller_pickup_ward_positive
    CHECK (pickup_ward_id IS NULL OR pickup_ward_id > 0);
