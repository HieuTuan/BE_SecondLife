ALTER TABLE user_reports ADD COLUMN related_post_id UUID REFERENCES posts(id);

ALTER TABLE user_reports DROP CONSTRAINT user_reports_reason_code_check;
ALTER TABLE user_reports ADD CONSTRAINT user_reports_reason_code_check CHECK (reason_code IN (
    'MISLEADING_DESCRIPTION','COUNTERFEIT','FRAUD','ITEM_NOT_RECEIVED','OTHER',
    'LATE_HANDOVER','SELLER_CANCELLATION','RETURN_NOT_AS_DESCRIBED','DUPLICATE_LISTING'));

-- Existing COUNTERFEIT reports remain readable; new listing submissions are restricted by the service.
ALTER TABLE user_reports ADD CONSTRAINT ck_reports_duplicate_listing_target
    CHECK (reason_code<>'DUPLICATE_LISTING' OR order_id IS NULL);
ALTER TABLE user_reports ADD CONSTRAINT ck_reports_related_listing
    CHECK (related_post_id IS NULL OR (order_id IS NULL AND reason_code='DUPLICATE_LISTING' AND related_post_id<>post_id));

CREATE INDEX idx_reports_listing_queue ON user_reports(status,created_at DESC,id DESC) WHERE order_id IS NULL;
