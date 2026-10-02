-- Preserve V16's applied checksum. Role membership is validated against RoleCode by the service.
-- Policy updates lock permissions, while grants lock roles first; a role FK can invert that order.
ALTER TABLE permission_assignable_roles
    DROP CONSTRAINT IF EXISTS permission_assignable_roles_role_code_fkey;

-- Restore the policy check if a development database was generated using Hibernate schema update.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'permission_assignable_roles'::regclass
          AND conname = 'ck_permission_assignable_roles_no_admin'
    ) THEN
        ALTER TABLE permission_assignable_roles
            ADD CONSTRAINT ck_permission_assignable_roles_no_admin CHECK (role_code <> 'ADMIN');
    END IF;
END $$;
