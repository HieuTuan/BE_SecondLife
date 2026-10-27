-- Restore relationships declared by V11/V13/V16/V18/V21/V23/V24/V25/V32.
-- Older Hibernate-created tables may exist without those migration constraints.
-- Do not rewrite rows, existing foreign keys, or intentional historical/polymorphic IDs.
DO $$
DECLARE
    relation RECORD;
    child_table REGCLASS;
    parent_table REGCLASS;
    child_keys SMALLINT[];
    parent_keys SMALLINT[];
    child_columns_sql TEXT;
    parent_columns_sql TEXT;
BEGIN
    -- These composite keys are required by the original V21/V32 foreign keys.
    -- Their leading id is already a primary key, so no data cleanup is needed.
    FOR relation IN
        SELECT * FROM (VALUES
            ('items', ARRAY['id','category_id']::text[], 'uq_v61_items_id_category'),
            ('order_commission_snapshots', ARRAY['id','order_id']::text[], 'uq_v61_snapshot_id_order')
        ) AS keys(table_name, column_names, index_name)
    LOOP
        parent_table := format('%I.%I', current_schema(), relation.table_name)::regclass;
        SELECT array_agg(a.attnum ORDER BY requested.ordinality),
               string_agg(format('%I', a.attname), ', ' ORDER BY requested.ordinality)
        INTO parent_keys, parent_columns_sql
        FROM unnest(relation.column_names) WITH ORDINALITY AS requested(column_name, ordinality)
        JOIN pg_attribute a ON a.attrelid = parent_table
            AND a.attname = requested.column_name AND NOT a.attisdropped;

        IF cardinality(parent_keys) IS DISTINCT FROM cardinality(relation.column_names) THEN
            RAISE EXCEPTION 'Required key columns are missing on %', parent_table;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_index i
            WHERE i.indrelid = parent_table AND i.indisunique AND i.indisvalid
                AND i.indimmediate AND i.indpred IS NULL AND i.indexprs IS NULL
                AND ARRAY(SELECT key_column FROM unnest(i.indkey) WITH ORDINALITY
                    AS indexed(key_column, position) WHERE position <= i.indnkeyatts ORDER BY key_column)
                    = ARRAY(SELECT key_column FROM unnest(parent_keys) AS required(key_column) ORDER BY key_column)
        ) THEN
            EXECUTE format('CREATE UNIQUE INDEX %I ON %s (%s)',
                relation.index_name, parent_table, parent_columns_sql);
        END IF;
    END LOOP;

    FOR relation IN
        SELECT * FROM (VALUES
            ('category_question_templates', ARRAY['category_id']::text[], 'categories', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_category_question_templates_category_id'),
            ('category_question_templates', ARRAY['item_id','category_id']::text[], 'items', ARRAY['id','category_id']::text[], 'NO ACTION', 'fk_v61_category_question_templates_item_id_category_id'),
            ('commission_rule_audits', ARRAY['actor_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_commission_rule_audits_actor_id'),
            ('commission_rule_audits', ARRAY['rule_id']::text[], 'commission_rules', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_commission_rule_audits_rule_id'),
            ('commission_rules', ARRAY['category_id']::text[], 'categories', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_commission_rules_category_id'),
            ('commission_rules', ARRAY['updated_by']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_commission_rules_updated_by'),
            ('credit_balances', ARRAY['credit_type']::text[], 'credit_types', ARRAY['code']::text[], 'NO ACTION', 'fk_v61_credit_balances_credit_type'),
            ('credit_balances', ARRAY['user_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_credit_balances_user_id'),
            ('credit_discount_tiers', ARRAY['updated_by']::text[], 'users', ARRAY['id']::text[], 'SET NULL', 'fk_v61_credit_discount_tiers_updated_by'),
            ('credit_ledger', ARRAY['credit_type']::text[], 'credit_types', ARRAY['code']::text[], 'NO ACTION', 'fk_v61_credit_ledger_credit_type'),
            ('credit_ledger', ARRAY['purchase_id']::text[], 'credit_purchases', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_credit_ledger_purchase_id'),
            ('credit_ledger', ARRAY['user_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_credit_ledger_user_id'),
            ('credit_pricing_rules', ARRAY['credit_type']::text[], 'credit_types', ARRAY['code']::text[], 'NO ACTION', 'fk_v61_credit_pricing_rules_credit_type'),
            ('credit_pricing_rules', ARRAY['updated_by']::text[], 'users', ARRAY['id']::text[], 'SET NULL', 'fk_v61_credit_pricing_rules_updated_by'),
            ('credit_purchases', ARRAY['discount_tier_id']::text[], 'credit_discount_tiers', ARRAY['id']::text[], 'SET NULL', 'fk_v61_credit_purchases_discount_tier_id'),
            ('credit_purchases', ARRAY['user_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_credit_purchases_user_id'),
            ('order_commission_snapshots', ARRAY['captured_by']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_order_commission_snapshots_captured_by'),
            ('order_commission_snapshots', ARRAY['order_id']::text[], 'orders', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_order_commission_snapshots_order_id'),
            ('order_commission_snapshots', ARRAY['rule_id']::text[], 'commission_rules', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_order_commission_snapshots_rule_id'),
            ('order_settlements', ARRAY['buyer_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_order_settlements_buyer_id'),
            ('order_settlements', ARRAY['commission_snapshot_id','order_id']::text[], 'order_commission_snapshots', ARRAY['id','order_id']::text[], 'NO ACTION', 'fk_v61_order_settlements_commission_snapshot_id_order_id'),
            ('order_settlements', ARRAY['order_id']::text[], 'orders', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_order_settlements_order_id'),
            ('order_settlements', ARRAY['seller_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_order_settlements_seller_id'),
            ('orders', ARRAY['shipping_quote_id']::text[], 'shipping_quotes', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_orders_shipping_quote_id'),
            ('payment_callback_events', ARRAY['payment_intent_id']::text[], 'payment_intents', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_payment_callback_events_payment_intent_id'),
            ('payment_intents', ARRAY['purchase_id']::text[], 'credit_purchases', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_payment_intents_purchase_id'),
            ('permission_assignable_roles', ARRAY['role_code']::text[], 'roles', ARRAY['code']::text[], 'NO ACTION', 'fk_v61_permission_assignable_roles_role_code'),
            ('posts', ARRAY['category_id']::text[], 'categories', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_posts_category_id'),
            ('posts', ARRAY['item_id','category_id']::text[], 'items', ARRAY['id','category_id']::text[], 'NO ACTION', 'fk_v61_posts_item_id_category_id'),
            ('posts', ARRAY['reviewed_by']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_posts_reviewed_by'),
            ('role_permission_audit', ARRAY['changed_by']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_role_permission_audit_changed_by'),
            ('role_permission_audit', ARRAY['role_id']::text[], 'roles', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_role_permission_audit_role_id'),
            ('seller_onboarding', ARRAY['user_id']::text[], 'users', ARRAY['id']::text[], 'CASCADE', 'fk_v61_seller_onboarding_user_id'),
            ('shipment_events', ARRAY['shipment_id']::text[], 'shipments', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_shipment_events_shipment_id'),
            ('shipments', ARRAY['buyer_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_shipments_buyer_id'),
            ('shipments', ARRAY['inspection_order_id']::text[], 'inspection_orders', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_shipments_inspection_order_id'),
            ('shipments', ARRAY['order_id']::text[], 'orders', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_shipments_order_id'),
            ('shipments', ARRAY['requested_by']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_shipments_requested_by'),
            ('shipments', ARRAY['seller_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_shipments_seller_id'),
            ('shipping_pickup_addresses', ARRAY['user_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_shipping_pickup_addresses_user_id'),
            ('shipping_quotes', ARRAY['buyer_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_shipping_quotes_buyer_id'),
            ('shipping_quotes', ARRAY['consumed_order_id']::text[], 'orders', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_shipping_quotes_consumed_order_id'),
            ('shipping_quotes', ARRAY['post_id']::text[], 'posts', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_shipping_quotes_post_id'),
            ('user_role_audit', ARRAY['changed_by']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_user_role_audit_changed_by'),
            ('user_role_audit', ARRAY['role_code']::text[], 'roles', ARRAY['code']::text[], 'NO ACTION', 'fk_v61_user_role_audit_role_code'),
            ('user_role_audit', ARRAY['user_id']::text[], 'users', ARRAY['id']::text[], 'NO ACTION', 'fk_v61_user_role_audit_user_id')
        ) AS relationships(child_name, child_columns, parent_name, parent_columns, delete_action, constraint_name)
    LOOP
        child_table := format('%I.%I', current_schema(), relation.child_name)::regclass;
        parent_table := format('%I.%I', current_schema(), relation.parent_name)::regclass;
        SELECT array_agg(a.attnum ORDER BY requested.ordinality),
               string_agg(format('%I', a.attname), ', ' ORDER BY requested.ordinality)
        INTO child_keys, child_columns_sql
        FROM unnest(relation.child_columns) WITH ORDINALITY AS requested(column_name, ordinality)
        JOIN pg_attribute a ON a.attrelid = child_table
            AND a.attname = requested.column_name AND NOT a.attisdropped;
        SELECT array_agg(a.attnum ORDER BY requested.ordinality),
               string_agg(format('%I', a.attname), ', ' ORDER BY requested.ordinality)
        INTO parent_keys, parent_columns_sql
        FROM unnest(relation.parent_columns) WITH ORDINALITY AS requested(column_name, ordinality)
        JOIN pg_attribute a ON a.attrelid = parent_table
            AND a.attname = requested.column_name AND NOT a.attisdropped;

        IF cardinality(child_keys) IS DISTINCT FROM cardinality(relation.child_columns)
            OR cardinality(parent_keys) IS DISTINCT FROM cardinality(relation.parent_columns) THEN
            RAISE EXCEPTION 'Required foreign key columns are missing: % -> %', child_table, parent_table;
        END IF;

        -- Compare the relationship, not the name: Hibernate and Flyway use different names.
        -- An existing equivalent FK retains its validation state and update/delete behavior.
        IF EXISTS (
            SELECT 1 FROM pg_constraint c
            WHERE c.contype = 'f' AND c.conrelid = child_table AND c.confrelid = parent_table
                AND c.conkey = child_keys AND c.confkey = parent_keys
        ) THEN
            CONTINUE;
        END IF;

        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (%s) REFERENCES %s (%s) ON DELETE %s NOT VALID',
            child_table, relation.constraint_name, child_columns_sql,
            parent_table, parent_columns_sql, relation.delete_action);

        BEGIN
            EXECUTE format('ALTER TABLE %s VALIDATE CONSTRAINT %I', child_table, relation.constraint_name);
        EXCEPTION WHEN foreign_key_violation THEN
            -- Keep historical orphan rows intact; new/changed references are still checked.
            -- Catch only this expected legacy-data case; other failures roll the migration back.
            RAISE NOTICE 'Foreign key % on % remains NOT VALID; review existing orphan references before validation',
                relation.constraint_name, child_table;
        END;
    END LOOP;
END;
$$;
