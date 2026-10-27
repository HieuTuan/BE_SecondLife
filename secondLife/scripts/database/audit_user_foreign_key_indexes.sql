-- READ ONLY: report general B-tree equality coverage of FKs referencing this schema's users.
-- A gap is a candidate for workload review, not an instruction to create an index.
-- Conservative: partial, expression, invalid and non-B-tree indexes do not count.
WITH user_foreign_keys AS (
    SELECT c.oid, c.conname, c.conrelid, c.conkey, c.convalidated,
           namespace.nspname AS table_schema, child.relname AS table_name
    FROM pg_constraint c
    JOIN pg_class child ON child.oid = c.conrelid
    JOIN pg_namespace namespace ON namespace.oid = child.relnamespace
    WHERE c.contype = 'f'
        AND c.confrelid = to_regclass(format('%I.users', current_schema()))
), coverage AS (
    SELECT fk.*,
           ARRAY(
               SELECT attribute.attname::text
               FROM unnest(fk.conkey) WITH ORDINALITY AS required(key_column, position)
               JOIN pg_attribute attribute ON attribute.attrelid = fk.conrelid
                   AND attribute.attnum = required.key_column
               ORDER BY required.position
           ) AS foreign_key_columns,
           ARRAY(
               SELECT index_relation.relname::text
               FROM pg_index i
               JOIN pg_class index_relation ON index_relation.oid = i.indexrelid
               JOIN pg_am method ON method.oid = index_relation.relam
               WHERE i.indrelid = fk.conrelid AND method.amname = 'btree'
                   AND i.indisvalid AND i.indisready AND i.indislive
                   AND i.indpred IS NULL AND i.indexprs IS NULL
                   AND i.indnkeyatts >= cardinality(fk.conkey)
                   AND ARRAY(
                       SELECT key_column
                       FROM unnest(i.indkey) WITH ORDINALITY AS indexed(key_column, position)
                       WHERE position <= cardinality(fk.conkey)
                       ORDER BY position
                   ) = fk.conkey
               ORDER BY index_relation.relname
           ) AS covering_index_names
    FROM user_foreign_keys fk
)
SELECT table_schema, table_name, conname::text AS constraint_name,
       foreign_key_columns, convalidated AS foreign_key_validated,
       cardinality(covering_index_names) > 0 AS has_covering_index,
       covering_index_names
FROM coverage
ORDER BY table_schema, table_name, constraint_name;
