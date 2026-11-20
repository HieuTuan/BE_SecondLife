ALTER TABLE inspection_orders
    ADD COLUMN inspector_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN inspector_confirmed_at TIMESTAMPTZ,
    ADD COLUMN assignment_locked BOOLEAN NOT NULL DEFAULT FALSE;

-- Lock historical inspections that have already started without inventing a confirmation time.
UPDATE inspection_orders
SET assignment_locked = TRUE
WHERE home_checked_in_at IS NOT NULL
   OR status IN ('INSPECTING', 'INSPECTION_IN_PROGRESS', 'PASSED', 'FAILED',
                 'COMPLETED', 'DELIVERING_TO_BUYER', 'DISPUTED');
