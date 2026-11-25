ALTER TABLE user_reports
    ADD COLUMN seller_response_deadline_at TIMESTAMPTZ,
    ADD COLUMN seller_response_requested_by UUID REFERENCES users(id),
    ADD COLUMN seller_responded_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_report_seller_response_request CHECK (
        (seller_response_deadline_at IS NULL AND seller_response_requested_by IS NULL)
        OR (order_id IS NOT NULL AND seller_response_deadline_at IS NOT NULL AND seller_response_requested_by IS NOT NULL)
    );

UPDATE user_reports r
SET seller_responded_at = response.first_response_at
FROM (
    SELECT s.report_id, MIN(s.created_at) AS first_response_at
    FROM report_submissions s
    JOIN user_reports existing ON existing.id = s.report_id AND existing.seller_id = s.author_id
    WHERE s.author_role = 'SELLER' AND existing.order_id IS NOT NULL
    GROUP BY s.report_id
) response
WHERE r.id = response.report_id;
