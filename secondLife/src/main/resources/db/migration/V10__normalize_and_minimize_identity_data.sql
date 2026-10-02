-- Align historical identity hashes/masks with the normalized format used by new submissions.
UPDATE seller_verifications
SET document_number_hash = encode(digest(
        regexp_replace(upper(document_number), '[^A-Z0-9]', '', 'g'), 'sha256'), 'hex'),
    document_number_masked = CASE
        WHEN length(regexp_replace(upper(document_number), '[^A-Z0-9]', '', 'g')) <= 4
            THEN repeat('*', length(regexp_replace(upper(document_number), '[^A-Z0-9]', '', 'g')))
        ELSE repeat('*', length(regexp_replace(upper(document_number), '[^A-Z0-9]', '', 'g')) - 4)
            || right(regexp_replace(upper(document_number), '[^A-Z0-9]', '', 'g'), 4)
    END
WHERE document_number IS NOT NULL
  AND regexp_replace(upper(document_number), '[^A-Z0-9]', '', 'g') <> '';

-- Raw identity numbers are needed during retries, not after a final decision.
ALTER TABLE seller_verifications ALTER COLUMN document_number DROP NOT NULL;
UPDATE seller_verifications
SET document_number = NULL
WHERE status IN ('APPROVED', 'REJECTED');
