-- Restore the column on databases whose schema drifted after V23 was recorded.
-- Preserve existing acceptance choices when the column is already present.
ALTER TABLE posts ADD COLUMN IF NOT EXISTS description_accepted BOOLEAN;

UPDATE posts
SET description_accepted = description IS NOT NULL AND btrim(description) <> ''
WHERE description_accepted IS NULL;

ALTER TABLE posts ALTER COLUMN description_accepted SET DEFAULT FALSE;
ALTER TABLE posts ALTER COLUMN description_accepted SET NOT NULL;
