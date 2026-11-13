ALTER TABLE inspection_orders
    ADD COLUMN appointment_shift VARCHAR(10),
    ADD COLUMN appointment_note VARCHAR(500),
    ADD COLUMN appointment_status VARCHAR(30),
    ADD COLUMN counter_scheduled_at TIMESTAMPTZ,
    ADD COLUMN counter_shift VARCHAR(10),
    ADD COLUMN counter_note VARCHAR(500),
    ADD COLUMN appointment_rejection_reason VARCHAR(500),
    ADD CONSTRAINT ck_inspection_appointment_shift CHECK (appointment_shift IN ('MORNING', 'AFTERNOON')),
    ADD CONSTRAINT ck_inspection_counter_shift CHECK (counter_shift IN ('MORNING', 'AFTERNOON')),
    ADD CONSTRAINT ck_inspection_appointment_status CHECK (
        appointment_status IN ('PENDING_CENTER_CONFIRMATION', 'COUNTERED_BY_CENTER', 'CONFIRMED', 'REJECTED')
    ),
    ADD CONSTRAINT ck_inspection_appointment_counter CHECK (
        (appointment_status IS NOT DISTINCT FROM 'COUNTERED_BY_CENTER'
            AND counter_scheduled_at IS NOT NULL AND counter_shift IS NOT NULL AND counter_note IS NOT NULL)
        OR (appointment_status IS DISTINCT FROM 'COUNTERED_BY_CENTER'
            AND counter_scheduled_at IS NULL AND counter_shift IS NULL AND counter_note IS NULL)
    );

-- The former scheduling endpoint recorded confirmed dates immediately.
UPDATE inspection_orders
SET appointment_status = 'CONFIRMED',
    appointment_shift = CASE
        WHEN (appointment_scheduled_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time >= TIME '08:00'
         AND (appointment_scheduled_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time < TIME '12:00' THEN 'MORNING'
        WHEN (appointment_scheduled_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time >= TIME '13:00'
         AND (appointment_scheduled_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time < TIME '17:00' THEN 'AFTERNOON'
    END
WHERE order_type = 'AT_HOME' AND appointment_scheduled_at IS NOT NULL;
