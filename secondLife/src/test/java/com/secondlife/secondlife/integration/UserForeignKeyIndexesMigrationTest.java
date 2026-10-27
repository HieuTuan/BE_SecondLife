package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseAvailable")
class UserForeignKeyIndexesMigrationTest {
    private static final String DATABASE_ENV = "USER_FK_INDEXES_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() {
        return GhnTestDatabase.available(DATABASE_ENV);
    }

    @AfterAll
    static void closeDatabase() {
        database.close();
    }

    @Test
    void upgradePreservesRowsAndAddsOnlyTheThreeSelectedHistoryIndexes() throws Exception {
        inDatabase((url, jdbc) -> {
            UUID seller = user(jdbc), buyer = user(jdbc), listing = UUID.randomUUID();
            jdbc.update("INSERT INTO posts(id,user_id,title,status,created_at) VALUES (?,?,'Listing','DRAFT',now())",
                    listing, seller);
            jdbc.update("""
                    INSERT INTO negotiations(id,post_id,buyer_id,offered_price,status,created_at,updated_at)
                    VALUES (?,?,?,1000,'PENDING',now(),now())
                    """, UUID.randomUUID(), listing, buyer);
            jdbc.update("""
                    INSERT INTO orders(id,post_id,buyer_id,seller_id,final_price,status,escrow_status,created_at,updated_at)
                    VALUES (?,?,?,?,1000,'PENDING_PAYMENT','HELD',now(),now())
                    """, UUID.randomUUID(), listing, buyer, seller);
            Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
            for (String table : List.of("users", "posts", "orders", "negotiations")) {
                rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
            }
            int before = indexCount(jdbc);

            Flyway migration = upgrade(url);
            assertEquals(1, migration.migrate().migrationsExecuted);

            rows.forEach((table, expected) ->
                    assertEquals(expected, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"), table));
            assertEquals(before + 3, indexCount(jdbc));
            assertIndex(jdbc, "idx_orders_buyer_created", "buyer_id, created_at DESC");
            assertIndex(jdbc, "idx_orders_seller_created", "seller_id, created_at DESC");
            assertIndex(jdbc, "idx_negotiations_buyer_created", "buyer_id, created_at DESC");
            assertEquals(0, migration.migrate().migrationsExecuted);
            assertTrue(migration.validateWithResult().validationSuccessful);
        });
    }

    @Test
    void skipsDifferentlyNamedEquivalentAndLongerPrefixIndexes() throws Exception {
        inDatabase((url, jdbc) -> {
            jdbc.execute("CREATE INDEX existing_buyer_history ON orders(buyer_id,created_at DESC) INCLUDE (status)");
            jdbc.execute("CREATE INDEX existing_seller_history ON orders(seller_id,created_at DESC,id)");
            jdbc.execute("CREATE INDEX existing_negotiation_history ON negotiations(buyer_id,created_at ASC)");
            int before = indexCount(jdbc);

            assertEquals(1, upgrade(url).migrate().migrationsExecuted);

            assertEquals(before, indexCount(jdbc));
            assertEquals(0, jdbc.queryForObject("""
                    SELECT count(*) FROM pg_class WHERE relnamespace=current_schema()::regnamespace
                        AND relname IN ('idx_orders_buyer_created','idx_orders_seller_created',
                            'idx_negotiations_buyer_created')
                    """, Integer.class));
        });
    }

    @Test
    void selectedIndexesImproveHistoryPlansWithoutChangingPagesOrCounts(TestReporter reporter) throws Exception {
        inDatabase((url, jdbc) -> {
            jdbc.execute("""
                    INSERT INTO users(id,email,password_hash)
                    SELECT md5('history-user-'||g)::uuid,'history-'||g||'@example.test','test-hash'
                    FROM generate_series(1,1000) g
                    """);
            UUID listing = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO posts(id,user_id,title,status,created_at)
                    VALUES (?,md5('history-user-1')::uuid,'History fixture','DRAFT',now())
                    """, listing);
            jdbc.update("""
                    INSERT INTO orders(id,post_id,buyer_id,seller_id,final_price,status,escrow_status,created_at,updated_at)
                    SELECT md5('history-order-'||g)::uuid,?,
                        md5('history-user-'||(1+g%1000))::uuid,
                        md5('history-user-'||(1+(g+500)%1000))::uuid,
                        1000,'CANCELLED','REFUNDED',
                        timestamptz '2026-01-01'+g*interval '1 second',timestamptz '2026-01-01'
                    FROM generate_series(1,100000) g
                    """, listing);
            jdbc.update("""
                    INSERT INTO negotiations(id,post_id,buyer_id,offered_price,status,created_at,updated_at)
                    SELECT md5('history-negotiation-'||g)::uuid,?,
                        md5('history-user-'||(1+g%1000))::uuid,1000,'REJECTED',
                        timestamptz '2026-01-01'+g*interval '1 second',timestamptz '2026-01-01'
                    FROM generate_series(1,100000) g
                    """, listing);
            jdbc.execute("ANALYZE orders");
            jdbc.execute("ANALYZE negotiations");
            Map<String, String> queries = Map.of(
                    "idx_orders_buyer_created", "orders WHERE buyer_id=md5('history-user-1')::uuid",
                    "idx_orders_seller_created", "orders WHERE seller_id=md5('history-user-1')::uuid",
                    "idx_negotiations_buyer_created", "negotiations WHERE buyer_id=md5('history-user-1')::uuid");
            Map<String, List<Map<String, Object>>> pages = new LinkedHashMap<>();
            for (var entry : queries.entrySet()) {
                String select = "SELECT id,created_at FROM " + entry.getValue() + " ORDER BY created_at DESC LIMIT 20";
                pages.put(entry.getKey(), jdbc.queryForList(select));
                String plan = explain(jdbc, select);
                reporter.publishEntry(entry.getKey() + " before", plan);
                assertTrue(plan.contains("Seq Scan"), plan);
                assertEquals(100, jdbc.queryForObject("SELECT count(*) FROM " + entry.getValue(), Integer.class));
            }

            assertEquals(1, upgrade(url).migrate().migrationsExecuted);

            for (var entry : queries.entrySet()) {
                String select = "SELECT id,created_at FROM " + entry.getValue() + " ORDER BY created_at DESC LIMIT 20";
                assertEquals(pages.get(entry.getKey()), jdbc.queryForList(select));
                assertEquals(100, jdbc.queryForObject("SELECT count(*) FROM " + entry.getValue(), Integer.class));
                String plan = explain(jdbc, select);
                reporter.publishEntry(entry.getKey() + " after", plan);
                assertTrue(plan.contains("Index Scan using " + entry.getKey()), plan);
                assertFalse(plan.contains("Sort"), plan);
            }
        });
    }

    private String explain(JdbcTemplate jdbc, String select) {
        return String.join("\n", jdbc.queryForList("EXPLAIN (ANALYZE,BUFFERS) " + select, String.class));
    }

    @Test
    void partialExpressionIncludeOnlyAndWrongKeyOrderIndexesDoNotReplaceHistoryIndexes() throws Exception {
        inDatabase((url, jdbc) -> {
            jdbc.execute("CREATE INDEX partial_buyer_history ON orders(buyer_id,created_at DESC) WHERE request_id IS NOT NULL");
            jdbc.execute("CREATE INDEX include_seller_history ON orders(created_at DESC) INCLUDE (seller_id)");
            jdbc.execute("CREATE INDEX expression_buyer_history ON negotiations((buyer_id::text),created_at DESC)");
            jdbc.execute("CREATE INDEX wrong_key_order_buyer_history ON negotiations(created_at DESC,buyer_id)");
            int before = indexCount(jdbc);

            assertEquals(1, upgrade(url).migrate().migrationsExecuted);

            assertEquals(before + 3, indexCount(jdbc));
        });
    }

    @Test
    void readOnlyAuditChecksCompositeLeadingKeysAndRejectsPartialInvalidAndIncludedKeys() throws Exception {
        inDatabase((url, jdbc) -> {
            UUID owner = user(jdbc);
            jdbc.execute("ALTER TABLE users ADD CONSTRAINT audit_users_id_email UNIQUE(id,email)");
            jdbc.execute("""
                    CREATE TABLE audit_user_refs(
                        id integer, owner_id uuid REFERENCES users(id), email varchar(320),
                        CONSTRAINT audit_composite_owner FOREIGN KEY(owner_id,email) REFERENCES users(id,email))
                    """);
            jdbc.update("INSERT INTO audit_user_refs VALUES (1,?,?),(1,?,?)",
                    owner, owner + "@example.test", owner, owner + "@example.test");
            String single = "audit_user_refs_owner_id_fkey";
            assertFalse(covered(jdbc, single));
            assertFalse(covered(jdbc, "audit_composite_owner"));
            jdbc.execute("CREATE INDEX audit_included ON audit_user_refs(id) INCLUDE(owner_id,email)");
            jdbc.execute("CREATE INDEX audit_trailing ON audit_user_refs(id,owner_id,email)");
            jdbc.execute("CREATE INDEX audit_partial ON audit_user_refs(owner_id,email) WHERE id>0");
            jdbc.execute("CREATE INDEX audit_expression ON audit_user_refs((owner_id::text),email)");
            assertThrows(DataAccessException.class, () -> jdbc.execute(
                    "CREATE UNIQUE INDEX CONCURRENTLY audit_invalid ON audit_user_refs(owner_id,email)"));
            assertFalse(jdbc.queryForObject("SELECT indisvalid FROM pg_index WHERE indexrelid='audit_invalid'::regclass",
                    Boolean.class));
            assertFalse(covered(jdbc, single));
            assertFalse(covered(jdbc, "audit_composite_owner"));

            jdbc.execute("CREATE INDEX audit_leading_single ON audit_user_refs(owner_id,id) INCLUDE(email)");
            assertTrue(covered(jdbc, single));
            assertFalse(covered(jdbc, "audit_composite_owner"));
            jdbc.execute("CREATE INDEX audit_leading_composite ON audit_user_refs(owner_id,email,id)");
            assertTrue(covered(jdbc, "audit_composite_owner"));
            jdbc.execute("BEGIN READ ONLY");
            try {
                assertTrue(covered(jdbc, "audit_composite_owner"));
            } finally {
                jdbc.execute("ROLLBACK");
            }
        });
    }

    private boolean covered(JdbcTemplate jdbc, String constraint) throws Exception {
        String audit = Files.readString(Path.of("scripts/database/audit_user_foreign_key_indexes.sql"));
        return jdbc.queryForList(audit).stream()
                .filter(row -> constraint.equals(row.get("constraint_name")))
                .map(row -> (Boolean) row.get("has_covering_index"))
                .findFirst().orElseThrow();
    }

    private int indexCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM pg_class WHERE relnamespace=current_schema()::regnamespace AND relkind='i'
                """, Integer.class);
    }

    private void assertIndex(JdbcTemplate jdbc, String name, String keys) {
        String definition = jdbc.queryForObject(
                "SELECT pg_get_indexdef(?::regclass)", String.class, name);
        assertTrue(definition.contains("USING btree (" + keys + ")"), definition);
    }

    private UUID user(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')", id, id + "@example.test");
        return id;
    }

    private Flyway upgrade(String url) {
        return Flyway.configure().dataSource(url, database.getUsername(), database.getPassword())
                .target("63").load();
    }

    private interface DatabaseTest {
        void run(String url, JdbcTemplate jdbc) throws Exception;
    }

    private void inDatabase(DatabaseTest test) throws Exception {
        String name = "secondlife_ghn_test_user_fk_v63_" + UUID.randomUUID().toString().replace("-", "");
        String base = database.getJdbcUrl();
        String url = base.substring(0, base.lastIndexOf('/') + 1) + name;
        try (var connection = DriverManager.getConnection(base, database.getUsername(), database.getPassword())) {
            JdbcTemplate admin = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            admin.execute("CREATE DATABASE " + name);
            try {
                Flyway.configure().dataSource(url, database.getUsername(), database.getPassword())
                        .target("62").load().migrate();
                try (var testConnection = DriverManager.getConnection(url, database.getUsername(), database.getPassword())) {
                    test.run(url, new JdbcTemplate(new SingleConnectionDataSource(testConnection, true)));
                }
            } finally {
                admin.execute("DROP DATABASE " + name);
            }
        }
    }
}
