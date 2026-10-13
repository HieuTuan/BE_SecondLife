-- Existing accounts keep NULL until the user chooses a gender.
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS gender VARCHAR(20);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid='user_profiles'::regclass AND conname='ck_user_profiles_gender') THEN
        ALTER TABLE user_profiles ADD CONSTRAINT ck_user_profiles_gender
            CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER'));
    END IF;
END $$;
