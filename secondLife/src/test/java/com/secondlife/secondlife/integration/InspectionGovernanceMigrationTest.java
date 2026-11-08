package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseAvailable")
class InspectionGovernanceMigrationTest {
    private static final String DATABASE_ENV = "GHN_FLOW_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }
    @AfterAll static void closeDatabase() { database.close(); }

    @Test
    void upgradeSplitsItemsWithoutChangingHistoricalPostsOrdersOrApprovals() throws Exception {
        inDatabase((url, jdbc) -> {
            UUID category = UUID.randomUUID();
            UUID originalItem = UUID.randomUUID();
            UUID seller = UUID.randomUUID();
            UUID post = UUID.randomUUID();
            UUID order = UUID.randomUUID();
            UUID template = UUID.randomUUID();
            jdbc.update("INSERT INTO categories(id,name) VALUES (?, 'Bếp & Phòng ăn')", category);
            jdbc.update("INSERT INTO items(id,category_id,name) VALUES (?,?,'Nồi cơm điện')", originalItem, category);
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')", seller, seller + "@example.test");
            jdbc.update("""
                    INSERT INTO posts(id,user_id,category_id,item_id,title,status,price,created_at)
                    VALUES (?,?,?,?,'Existing rice cooker','ACTIVE',450000,CURRENT_TIMESTAMP)
                    """, post, seller, category, originalItem);
            jdbc.update("""
                    INSERT INTO inspection_orders(id,post_id,seller_id,status,inspection_fee,shipping_fee,created_at)
                    VALUES (?,?,?,'PENDING',123000,45000,CURRENT_TIMESTAMP)
                    """, order, post, seller);
            jdbc.update("""
                    INSERT INTO inspection_checklist_templates(id,category_id,title,is_active,created_by)
                    VALUES (?,?,'Historical room checklist',TRUE,?)
                    """, template, category, seller);
            List<Map<String, Object>> expectedOrders = jdbc.queryForList("""
                    SELECT id,post_id,seller_id,status,inspection_fee,shipping_fee,created_at FROM inspection_orders ORDER BY id
                    """);

            Flyway upgrade = flyway(url, "72");
            assertEquals(3, upgrade.migrate().migrationsExecuted);
            assertEquals(expectedOrders, jdbc.queryForList("""
                    SELECT id,post_id,seller_id,status,inspection_fee,shipping_fee,created_at FROM inspection_orders ORDER BY id
                    """));
            assertEquals(originalItem, jdbc.queryForObject("SELECT item_id FROM posts WHERE id=?", UUID.class, post));
            assertFalse(jdbc.queryForObject("SELECT is_selectable FROM items WHERE id=?", Boolean.class, originalItem));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM items WHERE replacement_for_item_id=?", Integer.class, originalItem));
            assertEquals("LEGACY", jdbc.queryForObject("SELECT approval_status FROM inspection_checklist_templates WHERE id=?", String.class, template));
            assertNull(jdbc.queryForObject("SELECT reviewed_by FROM inspection_checklist_templates WHERE id=?", UUID.class, template));
            assertNull(jdbc.queryForObject("SELECT checklist_snapshot_json FROM inspection_orders WHERE id=?", String.class, order));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM inspection_fee_proposals WHERE complexity_level IS NOT NULL", Integer.class));
            assertEquals(0, upgrade.migrate().migrationsExecuted);
            assertTrue(upgrade.validateWithResult().validationSuccessful);
            Flyway shippingUpgrade = flyway(url, "73");
            assertEquals(1, shippingUpgrade.migrate().migrationsExecuted);
            assertEquals(expectedOrders, jdbc.queryForList("""
                    SELECT id,post_id,seller_id,status,inspection_fee,shipping_fee,created_at FROM inspection_orders ORDER BY id
                    """));
            assertNull(jdbc.queryForObject("SELECT inspection_shipping_quote_id FROM inspection_orders WHERE id=?", UUID.class, order));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM inspection_shipping_quotes", Integer.class));
            assertEquals(0, shippingUpgrade.migrate().migrationsExecuted);
            assertTrue(shippingUpgrade.validateWithResult().validationSuccessful);
        });
    }

    @Test
    void splitFunctionAlsoWorksAfterFreshInstallationAndIsIdempotent() throws Exception {
        inDatabase((url, jdbc) -> {
            flyway(url, "72").migrate();
            UUID category = UUID.randomUUID();
            UUID original = UUID.randomUUID();
            jdbc.update("INSERT INTO categories(id,name) VALUES (?, 'Kitchen')", category);
            jdbc.update("INSERT INTO items(id,category_id,name) VALUES (?,?,'Rice Cooker')", original, category);
            assertEquals(2, jdbc.queryForObject("SELECT secondlife_split_inspection_catalog_items_v71()", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT secondlife_split_inspection_catalog_items_v71()", Integer.class));
            assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM items WHERE category_id=?", Integer.class, category));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM items WHERE category_id=? AND is_selectable", Integer.class, category));
        });
    }

    @Test
    void reusesExistingSpecificItemIdsAndPreservesCustomCatalogEntries() throws Exception {
        inDatabase((url, jdbc) -> {
            UUID category = UUID.randomUUID();
            UUID parent = UUID.randomUUID();
            UUID existingChild = UUID.randomUUID();
            UUID custom = UUID.randomUUID();
            jdbc.update("INSERT INTO categories(id,name) VALUES (?, 'Custom kitchen')", category);
            jdbc.update("INSERT INTO items(id,category_id,name) VALUES (?,?,'Nồi cơm điện')", parent, category);
            jdbc.update("INSERT INTO items(id,category_id,name) VALUES (?,?,'Nồi cơm cơ')", existingChild, category);
            jdbc.update("INSERT INTO items(id,category_id,name) VALUES (?,?,'Thiết bị tự đặt tên')", custom, category);
            flyway(url, "72").migrate();
            assertEquals(existingChild, jdbc.queryForObject("SELECT id FROM items WHERE category_id=? AND name='Nồi cơm cơ'", UUID.class, category));
            assertTrue(jdbc.queryForObject("SELECT is_selectable FROM items WHERE id=?", Boolean.class, custom));
            assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM items WHERE category_id=?", Integer.class, category));
        });
    }

    private void inDatabase(DatabaseTest test) throws Exception {
        String name = "secondlife_ghn_test_gov_" + UUID.randomUUID().toString().replace("-", "");
        String originalUrl = database.getJdbcUrl();
        String url = originalUrl.substring(0, originalUrl.lastIndexOf('/') + 1) + name;
        try (var connection = DriverManager.getConnection(originalUrl, database.getUsername(), database.getPassword())) {
            var admin = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            admin.execute("CREATE DATABASE " + name);
            try {
                flyway(url, "69").migrate();
                try (var testConnection = DriverManager.getConnection(url, database.getUsername(), database.getPassword())) {
                    test.run(url, new JdbcTemplate(new SingleConnectionDataSource(testConnection, true)));
                }
            } finally {
                admin.execute("DROP DATABASE " + name);
            }
        }
    }

    private Flyway flyway(String url, String target) {
        return Flyway.configure().dataSource(url, database.getUsername(), database.getPassword()).target(target).load();
    }

    private interface DatabaseTest {
        void run(String url, JdbcTemplate jdbc) throws Exception;
    }
}
