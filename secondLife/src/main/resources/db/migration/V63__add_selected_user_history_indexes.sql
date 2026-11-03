-- Match the existing buyer/seller history predicates and their createdAt DESC default.
-- Keep this selective: other uncovered user FKs need workload evidence first.
DO $$
DECLARE
    history RECORD;
    history_table REGCLASS;
    history_keys SMALLINT[];
BEGIN
    FOR history IN
        SELECT * FROM (VALUES
            ('orders', 'buyer_id', 'idx_orders_buyer_created'),
            ('orders', 'seller_id', 'idx_orders_seller_created'),
            ('negotiations', 'buyer_id', 'idx_negotiations_buyer_created')
        ) AS selected(table_name, user_column, index_name)
    LOOP
        history_table := format('%I.%I', current_schema(), history.table_name)::regclass;
        SELECT array_agg(a.attnum ORDER BY requested.position)
        INTO history_keys
        FROM unnest(ARRAY[history.user_column, 'created_at']) WITH ORDINALITY
            AS requested(column_name, position)
        JOIN pg_attribute a ON a.attrelid = history_table
            AND a.attname = requested.column_name AND NOT a.attisdropped;

        IF cardinality(history_keys) IS DISTINCT FROM 2 THEN
            RAISE EXCEPTION 'Required history columns are missing on %', history_table;
        END IF;

        -- Compare search keys, not index names. INCLUDE columns do not count.
        -- With a fixed user key, ASC timestamps support DESC by a backward scan.
        IF EXISTS (
            SELECT 1
            FROM pg_index i
            JOIN pg_class index_relation ON index_relation.oid = i.indexrelid
            JOIN pg_am method ON method.oid = index_relation.relam
            JOIN pg_attribute timestamp_column ON timestamp_column.attrelid = history_table
                AND timestamp_column.attnum = history_keys[2]
            WHERE i.indrelid = history_table AND method.amname = 'btree'
                AND i.indisvalid AND i.indisready AND i.indislive
                AND i.indpred IS NULL AND i.indexprs IS NULL AND i.indnkeyatts >= 2
                AND ARRAY(SELECT key_column FROM unnest(i.indkey) WITH ORDINALITY
                    AS indexed(key_column, position) WHERE position <= 2 ORDER BY position) = history_keys
                AND (timestamp_column.attnotnull OR (i.indoption[1] & 3) IN (0, 3))
        ) THEN
            CONTINUE;
        END IF;

        EXECUTE format('CREATE INDEX %I ON %s (%I, created_at DESC)',
            history.index_name, history_table, history.user_column);
    END LOOP;
END;
$$;
