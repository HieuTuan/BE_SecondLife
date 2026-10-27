# Restricted PostgreSQL runtime role

The backend should connect as a non-owner login that inherits only the `secondlife_runtime` role. Flyway uses a separate schema owner connection. The optional `runtime` Spring profile enables this configuration; the existing default profile is unchanged.

`scripts/database/provision_runtime_role.sql` is an administrator-run **psql** script, not a Flyway migration. It creates a restricted **NOLOGIN** role and grants access only to named SecondLife tables in one application schema. It grants `SELECT`, `INSERT`, and `UPDATE` on `users`; other reviewed application tables retain normal `SELECT`, `INSERT`, `UPDATE`, and `DELETE`. It grants `USAGE`/`SELECT` only on sequences owned by those tables. Existing immutable ledger triggers, foreign keys, and business rules still apply. No runtime role receives table ownership, schema creation, `TRUNCATE`, trigger management, or Flyway history access.

Account locking/disabling remains an ordinary update to `users.account_status`. Wallets, credits, payments, order settlements, withdrawals, account registration, token cleanup, draft deletion, and RBAC updates retain their required application DML. This is a database privilege boundary, not a replacement for API authorization or financial transaction rules.

## 1. Review the target and prepare the owner

Use a disposable local database first, then a staging clone with a verified backup. Confirm the database, schema, migration owner, PostgreSQL version, current grants, and role memberships before applying changes. Run existing Flyway migrations with the owner account before provisioning the runtime role. The administrator must be able to create roles, grant privileges on the application objects, and alter default privileges for the actual migration owner.

Do not run this script against Supabase `auth`, `storage`, or another platform schema. Do not assign `anon`, `authenticated`, `service_role`, an owner role, superuser privileges, or `BYPASSRLS` to the application login. Existing RLS policies must permit the intended application operations without bypassing them.

Older databases may grant `CREATE` on `public` to `PUBLIC`. The script detects effective schema/database creation privileges and aborts instead of changing shared grants. An administrator must review and remove those grants separately, or move SecondLife to a dedicated schema through an approved migration. The same review applies to `PUBLIC` DML, globally permissive default grants, and callable application `SECURITY DEFINER` functions. Do not change platform-wide ACLs just to make the script pass.

## 2. Provision the NOLOGIN privilege role

From the backend directory, run psql as an administrator/schema owner. Use a password prompt or an external secret store; never put passwords in the command, repository, or this script:

```powershell
psql -X --host=localhost --port=5432 --dbname=secondlife --username=secondlife_owner -W `
  --set=runtime_role=secondlife_runtime `
  --set=app_schema=public `
  --set=migration_owner=secondlife_owner `
  --file=scripts/database/provision_runtime_role.sql
```

All three variables are required. The script uses quoted psql values and PostgreSQL identifier formatting. Names are never executed as raw SQL. It runs in one transaction with `ON_ERROR_STOP`; a failed guard rolls back role creation and grants. It can be rerun to reconcile direct grants after migrations. A pre-existing runtime role with ownership, elevated attributes, or memberships in another role is rejected; repair the role deliberately rather than weakening a guard.

The allowlist intentionally excludes `flyway_schema_history` and unknown/platform tables. Tables added later receive **no automatic runtime grants**. Review a new table, add its name to the script's allowlist, and rerun provisioning after the owner migration. Schema-specific defaults for the runtime role are removed; unsafe global defaults are rejected, because schema-level revocation cannot override a global default grant. This keeps newly created history/administrative tables private and prevents an automatic `DELETE` grant on a replacement `users` table.

## 3. Create a separate application login

An administrator creates the login separately, sets its password interactively or through the deployment secret manager, and assigns only the runtime group. Example in an administrator psql session:

```sql
CREATE ROLE secondlife_app LOGIN INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\password secondlife_app
GRANT secondlife_runtime TO secondlife_app;
```

Do not reuse an existing owner/admin login. The NOLOGIN role's safeguards do not remove extra privileges or memberships from a login that inherits it. Audit the actual application login independently, including inherited memberships and `PUBLIC` grants. Never grant the migration owner role to the runtime group or login. Restrict `CONNECT` and authentication to the intended database/network using the administrator's database and connection policy.

## 4. Validate permissions as the real application login

Open a fresh connection using `secondlife_app`, not an owner connection. Check the following before activating the profile (replace the schema if needed):

```sql
SELECT current_database(), session_user, current_user;
SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls
FROM pg_roles WHERE rolname = current_user;
SELECT r.rolname AS reachable_role
FROM pg_roles r
WHERE r.rolname <> current_user AND pg_has_role(current_user, r.oid, 'MEMBER');
SELECT has_schema_privilege(current_user, 'public', 'CREATE') AS can_create_app_objects,
       has_database_privilege(current_user, current_database(), 'CREATE') AS can_create_schemas,
       has_table_privilege(current_user, 'public.users', 'DELETE') AS can_delete_users,
       has_table_privilege(current_user, 'public.users', 'TRUNCATE') AS can_truncate_users,
       has_table_privilege(current_user, 'public.flyway_schema_history', 'INSERT,UPDATE,DELETE,TRUNCATE') AS can_write_history;
```

All elevated attributes and privilege booleans must be false; the only reachable application role should be `secondlife_runtime`. In a local/staging fixture, verify user reads/inserts, locking/disabling, normal cleanup deletes, wallet updates, credit ledger inserts and identity sequence allocation. Verify direct `DELETE FROM users` and `TRUNCATE users CASCADE` fail with SQLSTATE `42501`, including a user with no dependent rows. Also verify application-table DDL and Flyway history writes fail. Do not run destructive probes against production data.

## 5. Activate the optional Spring profile in staging

Provide these values through deployment secrets/environment settings (do not edit or commit `.env`):

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=runtime` | Activates the optional profile; combine with other required profiles when appropriate. |
| `APP_DB_SCHEMA` | Application schema; defaults to `public`. |
| `RUNTIME_DB_URL` | JDBC URL for the application connection. |
| `RUNTIME_DB_USERNAME` / `RUNTIME_DB_PASSWORD` | Separate non-owner application login credentials. |
| `MIGRATION_DB_URL` | JDBC URL for the **same database**, using the migration endpoint as needed. |
| `MIGRATION_DB_USERNAME` / `MIGRATION_DB_PASSWORD` | Schema owner/migrator credentials used only by Flyway's datasource. |

The profile fixes Hibernate to `ddl-auto=validate`, disables script initialization, enables Flyway, and supplies all three Flyway datasource properties explicitly. It does not fall back to runtime credentials for migrations. Check startup logs and database activity to confirm Flyway connects as the owner while Hikari/JPA connect as the application login. Confirm Hibernate validation succeeds, then run the normal account, authentication, wallet, credit, payment, order/escrow, withdrawal, and cleanup smoke tests. If validation exposes existing schema/entity drift, supply a reviewed owner migration; do not switch Hibernate back to `update` to hide it.

Known pre-existing drift: `FraudFlag` maps `fraud_flags`, but V1–V64 contain no migration creating that table. A database previously maintained by Hibernate may already have it; a database built solely from these migrations will not. Review this mapping and supply the appropriate schema migration before expecting a fresh database to pass full Hibernate validation. This change does not silently create or delete an unused business table to mask that existing mismatch.

Provision grants after a migration adds tables and before application traffic uses them. For a new deployment, bootstrap migrations and role provisioning before starting the runtime profile. Stage configuration and deployment changes before changing production credentials, and retain the prior deployment configuration for rollback. Do not switch the application back to an owner login as a permission workaround.

Separate datasources limit what ordinary runtime SQL can do. An application process that also receives migration credentials still has those credentials in its environment. Strong process isolation requires running migrations in a separate deployment job and withholding owner secrets from the long-lived application; that deployment arrangement is outside this optional startup profile.

## Limits, maintenance, and recovery

PostgreSQL owners and superusers can change ACLs, disable triggers, drop objects, or grant privileges again. This role does not prevent an administrator from deleting data. Use reviewed owner/admin access for migrations, backup/restore, disaster recovery, and any exceptional legally authorized maintenance. Restore object ownership to the migration owner, rerun provisioning, and recheck the actual login after a restore. Keep backups and test restoration separately from runtime authorization.

The script does not add a blanket user-deletion trigger. Existing historical migrations deliberately use `ON DELETE SET NULL` for some audit actors; that contract remains available to administrator-controlled maintenance. Runtime user deletion is blocked by privileges even when no foreign key references that user. Runtime writes to other rows are still governed by their existing constraints and immutable history triggers.

These guards cover the configured application schema and migration owner's defaults. They do not audit arbitrary extensions, platform `SECURITY DEFINER` functions in other schemas, grants added later by administrators, or a login's unrelated memberships. Review those separately. Temporary objects, if permitted through the database's existing `PUBLIC TEMPORARY` grant, do not confer ownership or DDL privileges on application tables.

## Disposable local integration test

The test is opt-in and rejects remote JDBC hosts and connection query parameters. Use a disposable local PostgreSQL database with an administrative test account; it creates unique schemas and NOLOGIN roles, migrates through Flyway, invokes the real psql script, and removes only those test objects. It never starts the application or contacts integrations. Without `RUNTIME_DB_TEST_URL`, this class is skipped.

```powershell
$env:RUNTIME_DB_TEST_URL = 'jdbc:postgresql://127.0.0.1:5432/secondlife_runtime_test'
$env:RUNTIME_DB_TEST_USERNAME = 'local_test_admin'
# Supply RUNTIME_DB_TEST_PASSWORD from the local test secret configuration.
$env:RUNTIME_DB_TEST_PSQL = 'C:\path\to\PostgreSQL\bin\psql.exe'
.\mvnw.cmd -Dtest=RuntimeDatabasePrivilegesIntegrationTest test
```

The administrator must be allowed to create roles and assign ownership/membership for negative fixtures. Run this test serially with other Maven commands that share `target/`.

References: [PostgreSQL privileges](https://www.postgresql.org/docs/16/ddl-priv.html), [default privileges and global/schema scope](https://www.postgresql.org/docs/current/sql-alterdefaultprivileges.html), and [Spring Boot separate Flyway datasource](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
