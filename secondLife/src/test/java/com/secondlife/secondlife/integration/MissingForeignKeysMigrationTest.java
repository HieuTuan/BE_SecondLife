package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

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
class MissingForeignKeysMigrationTest {
    private static final String DATABASE_ENV = "MISSING_FOREIGN_KEYS_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() {
        return GhnTestDatabase.available(DATABASE_ENV);
    }

    @AfterAll
    static void closeDatabase() {
        database.close();
    }

    @Test
    void upgradeRestoresMissingRelationsWithoutChangingRowsOrPolymorphicReferences() throws Exception {
        inDatabase((url, jdbc) -> {
            dropForeignKey(jdbc, "credit_balances", "user_id", "users", "id");
            dropForeignKey(jdbc, "shipping_quotes", "buyer_id", "users", "id");
            dropForeignKey(jdbc, "shipping_quotes", "post_id", "posts", "id");
            UUID owner = user(jdbc), buyer = user(jdbc), listing = post(jdbc, owner);
            UUID balance = creditBalance(jdbc, owner), quote = quote(jdbc, buyer, listing);
            UUID wallet = UUID.randomUUID(), transaction = UUID.randomUUID(), reference = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO user_wallets(id,user_id,balance,created_at,updated_at)
                    VALUES (?,?,75,now(),now())
                    """, wallet, owner);
            jdbc.update("""
                    INSERT INTO wallet_transactions(id,wallet_id,type,amount,reference_id,reference_type,
                        balance_before,balance_after,created_at)
                    VALUES (?,?,'PAYMENT',-25,?,'ORDER',100,75,now())
                    """, transaction, wallet, reference);
            Map<String, List<Map<String, Object>>> before = new LinkedHashMap<>();
            for (String table : List.of("users", "posts", "credit_balances", "shipping_quotes",
                    "user_wallets", "wallet_transactions")) {
                before.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
            }

            Flyway migration = upgrade(url);
            assertEquals(1, migration.migrate().migrationsExecuted);

            before.forEach((table, rows) ->
                    assertEquals(rows, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"), table));
            assertTrue(validated(jdbc, "credit_balances", "user_id", "users", "id"));
            assertTrue(validated(jdbc, "shipping_quotes", "buyer_id", "users", "id"));
            assertTrue(validated(jdbc, "shipping_quotes", "post_id", "posts", "id"));
            assertEquals(0, foreignKeysOnColumn(jdbc, "wallet_transactions", "reference_id"));
            assertEquals(reference, jdbc.queryForObject(
                    "SELECT reference_id FROM wallet_transactions WHERE id=?", UUID.class, transaction));
            assertThrows(DataIntegrityViolationException.class, () -> creditBalance(jdbc, UUID.randomUUID()));
            assertThrows(DataIntegrityViolationException.class, () -> quote(jdbc, UUID.randomUUID(), listing));
            assertThrows(DataIntegrityViolationException.class, () -> quote(jdbc, buyer, UUID.randomUUID()));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE credit_balances SET user_id=? WHERE id=?", UUID.randomUUID(), balance));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE shipping_quotes SET post_id=? WHERE id=?", UUID.randomUUID(), quote));
            assertEquals(0, migration.migrate().migrationsExecuted);
            assertTrue(migration.validateWithResult().validationSuccessful);
        });
    }

    @Test
    void preservesLegacyOrphansAndRejectsNewInvalidReferences() throws Exception {
        inDatabase((url, jdbc) -> {
            dropForeignKey(jdbc, "credit_balances", "user_id", "users", "id");
            UUID missingUser = UUID.randomUUID(), oldBalance = creditBalance(jdbc, missingUser);
            var before = jdbc.queryForMap("SELECT * FROM credit_balances WHERE id=?", oldBalance);

            assertEquals(1, upgrade(url).migrate().migrationsExecuted);

            assertEquals(before, jdbc.queryForMap("SELECT * FROM credit_balances WHERE id=?", oldBalance));
            assertFalse(validated(jdbc, "credit_balances", "user_id", "users", "id"));
            assertThrows(DataIntegrityViolationException.class, () -> creditBalance(jdbc, UUID.randomUUID()));
            assertEquals(1, jdbc.update("UPDATE credit_balances SET quantity=quantity+1 WHERE id=?", oldBalance));
            assertEquals(5L, jdbc.queryForObject(
                    "SELECT quantity FROM credit_balances WHERE id=?", Long.class, oldBalance));
            UUID validUser = user(jdbc);
            creditBalance(jdbc, validUser);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM credit_balances", Integer.class));
        });
    }

    @Test
    void preservesEquivalentExistingForeignKeyWithDifferentNameAndDeleteBehavior() throws Exception {
        inDatabase((url, jdbc) -> {
            var existing = foreignKey(jdbc, "credit_balances", "user_id", "users", "id");
            jdbc.execute("ALTER TABLE credit_balances RENAME CONSTRAINT " + existing.get("name")
                    + " TO existing_credit_balance_owner_fk");
            var before = foreignKey(jdbc, "credit_balances", "user_id", "users", "id");
            UUID owner = user(jdbc);
            creditBalance(jdbc, owner);

            assertEquals(1, upgrade(url).migrate().migrationsExecuted);

            assertEquals(before, foreignKey(jdbc, "credit_balances", "user_id", "users", "id"));
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("DELETE FROM users WHERE id=?", owner));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, owner));
        });
    }

    @Test
    void draftDeletionContinuesToPreserveImmutableValuationAndChatAudit() throws Exception {
        inDatabase((url, jdbc) -> {
            UUID owner = user(jdbc), listing = post(jdbc, owner);
            UUID session = UUID.randomUUID(), estimate = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO ai_chat_sessions(session_id,user_id,post_id,title,message_count,is_completed,created_at)
                    VALUES (?,?,?,'Retained draft audit',0,true,now())
                    """, session, owner, listing);
            jdbc.update("""
                    INSERT INTO ai_price_estimates(estimate_id,listing_id,model_version,suggested_price,created_at)
                    VALUES (?,?,'audit-model',1000,now())
                    """, estimate, listing);
            var sessionBefore = jdbc.queryForMap("SELECT * FROM ai_chat_sessions WHERE session_id=?", session);
            var estimateBefore = jdbc.queryForMap("SELECT * FROM ai_price_estimates WHERE estimate_id=?", estimate);

            assertEquals(1, upgrade(url).migrate().migrationsExecuted);

            assertEquals(0, foreignKeysOnColumn(jdbc, "ai_price_estimates", "listing_id"));
            assertEquals(0, foreignKeysOnColumn(jdbc, "ai_chat_sessions", "post_id"));
            assertEquals(1, jdbc.update("DELETE FROM posts WHERE id=?", listing));
            assertEquals(sessionBefore, jdbc.queryForMap("SELECT * FROM ai_chat_sessions WHERE session_id=?", session));
            assertEquals(estimateBefore, jdbc.queryForMap("SELECT * FROM ai_price_estimates WHERE estimate_id=?", estimate));
        });
    }

    @Test
    void restoresCatalogCompositeKeysAndRejectsAnExistingItemFromAnotherCategory() throws Exception {
        inDatabase((url, jdbc) -> {
            for (String child : List.of("posts", "category_question_templates")) {
                dropForeignKey(jdbc, child, List.of("item_id", "category_id"),
                        "items", List.of("id", "category_id"));
            }
            jdbc.execute("DROP INDEX uq_items_id_category");
            UUID owner = user(jdbc), category = category(jdbc), otherCategory = category(jdbc);
            UUID item = UUID.randomUUID(), template = UUID.randomUUID(), listing = post(jdbc, owner);
            jdbc.update("INSERT INTO items(id,category_id,name) VALUES (?,?,'Catalog item')", item, category);
            jdbc.update("UPDATE posts SET category_id=?,item_id=? WHERE id=?", category, item, listing);
            jdbc.update("""
                    INSERT INTO category_question_templates(id,category_id,item_id,template_text)
                    VALUES (?,?,?,'Describe this item')
                    """, template, category, item);
            var listingBefore = jdbc.queryForMap("SELECT * FROM posts WHERE id=?", listing);
            var templateBefore = jdbc.queryForMap("SELECT * FROM category_question_templates WHERE id=?", template);

            assertEquals(1, upgrade(url).migrate().migrationsExecuted);

            assertEquals(listingBefore, jdbc.queryForMap("SELECT * FROM posts WHERE id=?", listing));
            assertEquals(templateBefore,
                    jdbc.queryForMap("SELECT * FROM category_question_templates WHERE id=?", template));
            for (String child : List.of("posts", "category_question_templates")) {
                assertTrue((Boolean) foreignKey(jdbc, child, List.of("item_id", "category_id"),
                        "items", List.of("id", "category_id")).get("validated"));
            }
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE posts SET category_id=? WHERE id=?", otherCategory, listing));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO category_question_templates(id,category_id,item_id,template_text)
                    VALUES (?,?,?,'Wrong category for existing item')
                    """, UUID.randomUUID(), otherCategory, item));
            UUID anotherListing = post(jdbc, owner);
            assertEquals(1, jdbc.update("UPDATE posts SET category_id=?,item_id=? WHERE id=?",
                    category, item, anotherListing));
            assertEquals(1, jdbc.update("""
                    INSERT INTO category_question_templates(id,category_id,item_id,template_text)
                    VALUES (?,?,?,'Valid category and item')
                    """, UUID.randomUUID(), category, item));
        });
    }

    @Test
    void restoresSettlementCompositeKeyAndRejectsSnapshotFromAnotherExistingOrder() throws Exception {
        inDatabase((url, jdbc) -> {
            dropForeignKey(jdbc, "order_settlements", List.of("commission_snapshot_id", "order_id"),
                    "order_commission_snapshots", List.of("id", "order_id"));
            jdbc.execute("ALTER TABLE order_commission_snapshots "
                    + "DROP CONSTRAINT order_commission_snapshots_id_order_id_key");
            UUID seller = user(jdbc), buyer = user(jdbc), rule = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO commission_rules(id,name,type,rate,min_commission,active,revision,
                        updated_by,created_at,updated_at)
                    VALUES (?,'Test commission','DEFAULT',0.05,0,false,1,?,now(),now())
                    """, rule, seller);
            UUID firstOrder = order(jdbc, seller, buyer), secondOrder = order(jdbc, seller, buyer);
            UUID firstSnapshot = snapshot(jdbc, firstOrder, rule), secondSnapshot = snapshot(jdbc, secondOrder, rule);
            var snapshotsBefore = jdbc.queryForList("SELECT * FROM order_commission_snapshots ORDER BY id");
            var ordersBefore = jdbc.queryForList("SELECT * FROM orders ORDER BY id");

            assertEquals(1, upgrade(url).migrate().migrationsExecuted);

            assertEquals(snapshotsBefore, jdbc.queryForList("SELECT * FROM order_commission_snapshots ORDER BY id"));
            assertEquals(ordersBefore, jdbc.queryForList("SELECT * FROM orders ORDER BY id"));
            assertTrue((Boolean) foreignKey(jdbc, "order_settlements", List.of("commission_snapshot_id", "order_id"),
                    "order_commission_snapshots", List.of("id", "order_id")).get("validated"));
            settlement(jdbc, firstOrder, firstSnapshot, buyer, seller);
            assertThrows(DataIntegrityViolationException.class,
                    () -> settlement(jdbc, secondOrder, firstSnapshot, buyer, seller));
            settlement(jdbc, secondOrder, secondSnapshot, buyer, seller);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM order_settlements", Integer.class));
        });
    }

    @Test
    void restoresSetNullDeleteBehaviorWithoutRemovingPricingHistory() throws Exception {
        inDatabase((url, jdbc) -> {
            dropForeignKey(jdbc, "credit_pricing_rules", "updated_by", "users", "id");
            UUID actor = user(jdbc);
            jdbc.update("UPDATE credit_pricing_rules SET updated_by=? WHERE credit_type='LISTING'", actor);
            var expected = new LinkedHashMap<>(jdbc.queryForMap(
                    "SELECT * FROM credit_pricing_rules WHERE credit_type='LISTING'"));
            expected.put("updated_by", null);

            assertEquals(1, upgrade(url).migrate().migrationsExecuted);

            assertEquals(1, jdbc.update("DELETE FROM users WHERE id=?", actor));
            assertEquals(expected, jdbc.queryForMap(
                    "SELECT * FROM credit_pricing_rules WHERE credit_type='LISTING'"));
        });
    }

    private interface DatabaseTest {
        void run(String url, JdbcTemplate jdbc) throws Exception;
    }

    private void inDatabase(DatabaseTest test) throws Exception {
        String name = "secondlife_ghn_test_fk_v61_" + UUID.randomUUID().toString().replace("-", "");
        String base = database.getJdbcUrl();
        String url = base.substring(0, base.lastIndexOf('/') + 1) + name;
        try (var adminConnection = DriverManager.getConnection(base, database.getUsername(), database.getPassword())) {
            JdbcTemplate admin = new JdbcTemplate(new SingleConnectionDataSource(adminConnection, true));
            admin.execute("CREATE DATABASE " + name);
            try {
                Flyway.configure().dataSource(url, database.getUsername(), database.getPassword())
                        .target("60").load().migrate();
                try (var connection = DriverManager.getConnection(url, database.getUsername(), database.getPassword())) {
                    test.run(url, new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
                }
            } finally {
                admin.execute("DROP DATABASE " + name);
            }
        }
    }

    private Flyway upgrade(String url) {
        return Flyway.configure().dataSource(url, database.getUsername(), database.getPassword())
                .target("61").load();
    }

    private UUID user(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')", id, id + "@example.test");
        return id;
    }

    private UUID post(JdbcTemplate jdbc, UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO posts(id,user_id,title,status,created_at) VALUES (?,?,'Draft listing','DRAFT',now())",
                id, owner);
        return id;
    }

    private UUID creditBalance(JdbcTemplate jdbc, UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO credit_balances(id,user_id,credit_type,quantity,version,updated_at)
                VALUES (?,?,'LISTING',4,0,now())
                """, id, owner);
        return id;
    }

    private UUID quote(JdbcTemplate jdbc, UUID buyer, UUID listing) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shipping_quotes(id,buyer_id,post_id,provider,payload,delivery_address,
                    seller_pickup_address,leg,fee,expires_at,created_at)
                VALUES (?,?,?,'LALAMOVE','{}','{}','{}','SELLER_TO_BUYER',10000,now()+interval '30 minutes',now())
                """, id, buyer, listing);
        return id;
    }

    private UUID category(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Test category')", id);
        return id;
    }

    private UUID order(JdbcTemplate jdbc, UUID seller, UUID buyer) {
        UUID id = UUID.randomUUID(), listing = post(jdbc, seller);
        jdbc.update("""
                INSERT INTO orders(id,post_id,buyer_id,seller_id,final_price,status,escrow_status,created_at,updated_at)
                VALUES (?,?,?,?,1000,'PENDING_PAYMENT','HELD',now(),now())
                """, id, listing, buyer, seller);
        return id;
    }

    private UUID snapshot(JdbcTemplate jdbc, UUID order, UUID rule) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO order_commission_snapshots(id,order_id,rule_id,rule_revision,rule_name,rule_type,
                    rate,min_commission,base_type,commission_base,currency,snapshotted_at)
                VALUES (?,?,?,1,'Test commission','DEFAULT',0.05,0,'PRODUCT_PRICE',1000,'VND',now())
                """, id, order, rule);
        return id;
    }

    private void settlement(JdbcTemplate jdbc, UUID order, UUID snapshot, UUID buyer, UUID seller) {
        jdbc.update("""
                INSERT INTO order_settlements(id,order_id,commission_snapshot_id,buyer_id,seller_id,
                    commission_base,raw_commission,platform_commission,seller_payout,shipping_fee,
                    currency,rounding_mode,settled_at)
                VALUES (?,?,?,?,?,1000,50,50,950,0,'VND','HALF_UP',now())
                """, UUID.randomUUID(), order, snapshot, buyer, seller);
    }

    private void dropForeignKey(JdbcTemplate jdbc, String table, String column, String parent, String parentColumn) {
        dropForeignKey(jdbc, table, List.of(column), parent, List.of(parentColumn));
    }

    private void dropForeignKey(JdbcTemplate jdbc, String table, List<String> columns,
                                String parent, List<String> parentColumns) {
        var key = foreignKey(jdbc, table, columns, parent, parentColumns);
        jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + key.get("name"));
    }

    private boolean validated(JdbcTemplate jdbc, String table, String column, String parent, String parentColumn) {
        return (Boolean) foreignKey(jdbc, table, column, parent, parentColumn).get("validated");
    }

    private Map<String, Object> foreignKey(JdbcTemplate jdbc, String table, String column,
                                           String parent, String parentColumn) {
        return foreignKey(jdbc, table, List.of(column), parent, List.of(parentColumn));
    }

    private Map<String, Object> foreignKey(JdbcTemplate jdbc, String table, List<String> columns,
                                           String parent, List<String> parentColumns) {
        var keys = jdbc.queryForList("""
                SELECT c.oid::bigint AS oid,c.conname AS name,c.convalidated AS validated,
                    pg_get_constraintdef(c.oid) AS definition
                FROM pg_constraint c
                WHERE c.contype='f' AND c.conrelid=CAST(? AS regclass) AND c.confrelid=CAST(? AS regclass)
                    AND ARRAY(
                        SELECT a.attname::text FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum,position)
                        JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum ORDER BY k.position
                    )=CAST(? AS text[])
                    AND ARRAY(
                        SELECT a.attname::text FROM unnest(c.confkey) WITH ORDINALITY AS k(attnum,position)
                        JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.attnum ORDER BY k.position
                    )=CAST(? AS text[])
                """, table, parent, "{" + String.join(",", columns) + "}", "{" + String.join(",", parentColumns) + "}");
        assertEquals(1, keys.size(), table + columns + " -> " + parent + parentColumns);
        return keys.getFirst();
    }

    private int foreignKeysOnColumn(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint c
                JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=ANY(c.conkey)
                WHERE c.contype='f' AND c.conrelid=CAST(? AS regclass) AND a.attname=?
                """, Integer.class, table, column);
    }
}
