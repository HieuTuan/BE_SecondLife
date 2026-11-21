-- Remove appeal deadline for inspection orders
UPDATE inspection_orders
SET appeal_deadline = NULL
WHERE appeal_deadline IS NOT NULL;
