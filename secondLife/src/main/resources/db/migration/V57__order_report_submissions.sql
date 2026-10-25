CREATE TABLE report_submissions (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES user_reports(id),
    author_id UUID NOT NULL REFERENCES users(id),
    author_role VARCHAR(10) NOT NULL CHECK (author_role IN ('BUYER', 'SELLER')),
    request_id UUID NOT NULL,
    description VARCHAR(2000) NOT NULL CHECK (length(btrim(description)) > 0),
    evidence_urls JSONB NOT NULL CHECK (jsonb_typeof(evidence_urls) = 'array' AND jsonb_array_length(evidence_urls) <= 10),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_report_submission_request UNIQUE (report_id, author_id, request_id)
);

CREATE INDEX idx_report_submissions_timeline ON report_submissions(report_id, created_at, id);

CREATE FUNCTION prevent_report_submission_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Report submissions are immutable';
END;
$$;

CREATE TRIGGER report_submissions_immutable BEFORE UPDATE OR DELETE ON report_submissions
FOR EACH ROW EXECUTE FUNCTION prevent_report_submission_mutation();
