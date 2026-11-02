-- Administrator-only psql script. Never run as a Flyway migration or the application login.
-- Required psql variables: runtime_role, app_schema, migration_owner. No passwords are accepted.
\set ON_ERROR_STOP on
\if :{?runtime_role}
\else
    \echo 'Required variable: --set=runtime_role=secondlife_runtime'
    \quit 3
\endif
\if :{?app_schema}
\else
    \echo 'Required variable: --set=app_schema=public'
    \quit 3
\endif
\if :{?migration_owner}
\else
    \echo 'Required variable: --set=migration_owner=secondlife_owner'
    \quit 3
\endif

BEGIN;
SELECT set_config('secondlife.provision_runtime_role', :'runtime_role', true),
       set_config('secondlife.provision_app_schema', :'app_schema', true),
       set_config('secondlife.provision_migration_owner', :'migration_owner', true);

DO $provision$
DECLARE
    runtime_name text := current_setting('secondlife.provision_runtime_role');
    schema_name text := current_setting('secondlife.provision_app_schema');
    owner_name text := current_setting('secondlife.provision_migration_owner');
    runtime_id oid;
    schema_id oid;
    owner_id oid;
    object record;
    required_privileges text;
    -- Review new migration tables here. Never grant on ALL TABLES or unknown/platform tables.
    app_tables constant text[] := ARRAY[
        'ai_chat_messages', 'ai_chat_sessions', 'ai_price_estimates',
        'categories', 'category_question_template_proposals', 'category_question_templates', 'chat_messages', 'chat_rooms',
        'commission_rule_audits', 'commission_rules', 'credit_balances', 'credit_discount_tiers',
        'credit_ledger', 'credit_pricing_rules', 'credit_purchases', 'credit_types',
        'deposit_requests', 'email_verification_tokens', 'fraud_flags', 'geocoded_locations_cache',
        'identity_restrictions', 'inspection_checklist_items', 'inspection_checklist_templates',
        'inspection_fee_proposals', 'inspection_orders', 'inspection_seal_logs', 'inspection_travel_tiers',
        'items', 'negotiations', 'notifications', 'order_commission_snapshots',
        'order_confirmation_penalties', 'order_settlements', 'orders', 'password_reset_tokens',
        'payment_callback_events', 'payment_intents', 'permission_assignable_roles', 'permissions',
        'post_images', 'posts', 'refresh_tokens', 'report_evidence_uploads', 'report_submissions',
        'reputation_incidents', 'reputation_order_faults', 'reputation_order_outcomes',
        'return_shipping_fees', 'role_permission_audit', 'role_permissions', 'roles',
        'seller_onboarding', 'seller_reputation_events', 'seller_reputations',
        'seller_verification_events', 'seller_verifications', 'sepay_transfer_receipts',
        'shipment_events', 'shipments', 'shipping_pickup_addresses', 'shipping_quotes',
        'system_order_funds', 'system_wallet_transactions', 'system_wallets', 'topup_packages',
        'user_device_tokens', 'user_profiles', 'user_reports', 'user_role_audit', 'user_roles',
        'user_wallets', 'users', 'video_upload_sessions', 'video_verification_windows',
        'wallet_transactions', 'wallet_withdrawals'
    ];
BEGIN
    IF runtime_name = '' OR octet_length(runtime_name) > 63
       OR runtime_name IN ('public', 'postgres', 'anon', 'authenticated', 'service_role', 'authenticator',
                           'dashboard_user', 'pgbouncer')
       OR runtime_name LIKE 'pg\_%' ESCAPE '\' OR runtime_name LIKE 'supabase\_%' ESCAPE '\' THEN
        RAISE EXCEPTION 'Use a nonempty, unreserved runtime role name of at most 63 bytes';
    END IF;
    SELECT oid INTO schema_id FROM pg_namespace WHERE nspname = schema_name;
    SELECT oid INTO owner_id FROM pg_roles WHERE rolname = owner_name;
    IF schema_id IS NULL OR schema_name IN ('auth', 'storage', 'information_schema')
       OR schema_name LIKE 'pg\_%' ESCAPE '\' THEN
        RAISE EXCEPTION 'Target must be an existing SecondLife application schema, never a platform/system schema';
    END IF;
    IF owner_id IS NULL OR runtime_name = owner_name THEN
        RAISE EXCEPTION 'Migration owner must exist and differ from runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relnamespace = schema_id
                   AND relname = 'users' AND relkind IN ('r', 'p'))
       OR NOT EXISTS (SELECT 1 FROM pg_class WHERE relnamespace = schema_id
                      AND relname = 'flyway_schema_history' AND relkind = 'r') THEN
        RAISE EXCEPTION 'Run SecondLife owner migrations before runtime provisioning';
    END IF;

    SELECT oid INTO runtime_id FROM pg_roles WHERE rolname = runtime_name;
    IF runtime_id IS NULL THEN
        EXECUTE format('CREATE ROLE %I NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', runtime_name);
        SELECT oid INTO runtime_id FROM pg_roles WHERE rolname = runtime_name;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE oid = runtime_id
               AND (rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)) THEN
        RAISE EXCEPTION 'Runtime role must be NOLOGIN and have no elevated role attributes';
    END IF;
    -- Reject all parent memberships, including NOINHERIT memberships usable with SET ROLE.
    IF EXISTS (SELECT 1 FROM pg_auth_members WHERE member = runtime_id) THEN
        RAISE EXCEPTION 'Runtime role must not be a member of another role';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_namespace WHERE oid = schema_id AND nspowner = runtime_id)
       OR EXISTS (SELECT 1 FROM pg_database WHERE datname = current_database() AND datdba = runtime_id)
       OR EXISTS (SELECT 1 FROM pg_class WHERE relnamespace = schema_id AND relowner = runtime_id)
       OR EXISTS (SELECT 1 FROM pg_proc WHERE pronamespace = schema_id AND proowner = runtime_id)
       OR EXISTS (SELECT 1 FROM pg_type WHERE typnamespace = schema_id AND typowner = runtime_id) THEN
        RAISE EXCEPTION 'Runtime role owns database/application objects; transfer ownership to the migration owner first';
    END IF;

    -- Reconcile only this role's direct ACLs; do not change PUBLIC or hosted-platform roles.
    EXECUTE format('REVOKE ALL ON DATABASE %I FROM %I', current_database(), runtime_name);
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), runtime_name);
    EXECUTE format('REVOKE ALL ON SCHEMA %I FROM %I', schema_name, runtime_name);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', schema_name, runtime_name);
    EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA %I FROM %I', schema_name, runtime_name);
    EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA %I FROM %I', schema_name, runtime_name);
    EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA %I FROM %I', schema_name, runtime_name);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I REVOKE ALL ON TABLES FROM %I', owner_name, schema_name, runtime_name);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I REVOKE ALL ON SEQUENCES FROM %I', owner_name, schema_name, runtime_name);

    -- A schema-level REVOKE cannot cancel a global default GRANT. Fail instead of editing shared defaults.
    IF EXISTS (
        SELECT 1 FROM pg_default_acl d CROSS JOIN LATERAL aclexplode(d.defaclacl) a
        WHERE d.defaclrole = owner_id AND d.defaclnamespace IN (0, schema_id)
          AND d.defaclobjtype IN ('r', 'S') AND a.grantee IN (0, runtime_id)
    ) THEN
        RAISE EXCEPTION 'Unsafe global/PUBLIC default table or sequence grants; administrator must review defaults before provisioning';
    END IF;
    IF has_schema_privilege(runtime_id, schema_id, 'CREATE')
       OR has_database_privilege(runtime_id, current_database(), 'CREATE') THEN
        RAISE EXCEPTION 'Effective PUBLIC/inherited CREATE privilege defeats runtime isolation; administrator must review shared grants';
    END IF;

    FOR object IN
        SELECT oid, relname FROM pg_class
        WHERE relnamespace = schema_id AND relkind IN ('r', 'p') AND relname = ANY(app_tables)
    LOOP
        required_privileges := CASE WHEN object.relname = 'users' THEN 'SELECT, INSERT, UPDATE'
                                    ELSE 'SELECT, INSERT, UPDATE, DELETE' END;
        EXECUTE format('GRANT %s ON TABLE %I.%I TO %I', required_privileges, schema_name, object.relname, runtime_name);
    END LOOP;

    FOR object IN
        SELECT DISTINCT s.relname FROM pg_class s JOIN pg_depend d ON d.objid = s.oid
        JOIN pg_class t ON t.oid = d.refobjid
        WHERE s.relnamespace = schema_id AND s.relkind = 'S'
          AND d.classid = 'pg_class'::regclass AND d.refclassid = 'pg_class'::regclass
          AND d.deptype IN ('a', 'i') AND t.relnamespace = schema_id AND t.relname = ANY(app_tables)
    LOOP
        EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %I.%I TO %I', schema_name, object.relname, runtime_name);
    END LOOP;

    IF has_table_privilege(runtime_id, format('%I.users', schema_name), 'DELETE,TRUNCATE') THEN
        RAISE EXCEPTION 'Effective DELETE/TRUNCATE on users remains; administrator must review PUBLIC/inherited grants';
    END IF;
    -- Check effective ACLs as well as direct ACLs, including column grants on private/history tables.
    FOR object IN
        SELECT oid, relname FROM pg_class
        WHERE relnamespace = schema_id AND relkind IN ('r', 'p', 'v', 'm', 'f')
    LOOP
        IF has_table_privilege(runtime_id, object.oid, 'TRUNCATE,REFERENCES,TRIGGER')
           OR has_any_column_privilege(runtime_id, object.oid, 'REFERENCES')
           OR has_any_column_privilege(runtime_id, object.oid,
               'SELECT WITH GRANT OPTION,INSERT WITH GRANT OPTION,UPDATE WITH GRANT OPTION,REFERENCES WITH GRANT OPTION')
           OR has_table_privilege(runtime_id, object.oid,
               'SELECT WITH GRANT OPTION,INSERT WITH GRANT OPTION,UPDATE WITH GRANT OPTION,DELETE WITH GRANT OPTION') THEN
            RAISE EXCEPTION 'Unsafe effective structural/grant privileges on %.%', schema_name, object.relname;
        END IF;
        IF NOT object.relname = ANY(app_tables)
           AND (has_table_privilege(runtime_id, object.oid, 'SELECT,INSERT,UPDATE,DELETE')
                OR has_any_column_privilege(runtime_id, object.oid, 'SELECT,INSERT,UPDATE')) THEN
            RAISE EXCEPTION 'Runtime can access unreviewed/history table %.%; remove extra/PUBLIC grants first', schema_name, object.relname;
        END IF;
        IF current_setting('server_version_num')::integer >= 170000 THEN
            IF has_table_privilege(runtime_id, object.oid, 'MAINTAIN') THEN
                RAISE EXCEPTION 'Runtime has effective MAINTAIN privilege on %.%', schema_name, object.relname;
            END IF;
        END IF;
    END LOOP;
    FOR object IN
        SELECT s.oid, s.relname, EXISTS (
            SELECT 1 FROM pg_depend d JOIN pg_class t ON t.oid = d.refobjid
            WHERE d.objid = s.oid AND d.classid = 'pg_class'::regclass
              AND d.refclassid = 'pg_class'::regclass AND d.deptype IN ('a', 'i')
              AND t.relnamespace = schema_id AND t.relname = ANY(app_tables)
        ) AS app_owned
        FROM pg_class s WHERE s.relnamespace = schema_id AND s.relkind = 'S'
    LOOP
        IF has_sequence_privilege(runtime_id, object.oid,
                'UPDATE,USAGE WITH GRANT OPTION,SELECT WITH GRANT OPTION')
           OR (NOT object.app_owned AND has_sequence_privilege(runtime_id, object.oid, 'USAGE,SELECT')) THEN
            RAISE EXCEPTION 'Unsafe effective privileges on sequence %.%', schema_name, object.relname;
        END IF;
    END LOOP;
    IF EXISTS (SELECT 1 FROM pg_proc WHERE pronamespace = schema_id AND prosecdef
               AND has_function_privilege(runtime_id, oid, 'EXECUTE')) THEN
        RAISE EXCEPTION 'Callable application SECURITY DEFINER routine defeats the boundary; administrator review required';
    END IF;
    RAISE NOTICE 'Provisioned restricted NOLOGIN role % in schema %. Review the separate application login before deployment.', runtime_name, schema_name;
END
$provision$;
COMMIT;
