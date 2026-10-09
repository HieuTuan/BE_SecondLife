package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("databaseAvailable")
class DefaultCommissionMigrationTest {
    private static final String DATABASE_ENV = "COMMISSION_MIGRATION_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }
    @AfterAll static void stopOwnContainer() { database.close(); }

    @Test void adoptsExistingCommissionTablesAndRetiresScopedPoliciesWithoutChangingHistoricalSnapshots() throws Exception {
        Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .target("31").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
        // Existing deployments can already have these tables outside the canonical migration history.
        jdbc.execute(new org.springframework.core.io.ClassPathResource("db/migration/V32__commission_rules_order_snapshots_and_settlements.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        UUID actor = UUID.randomUUID(), category = UUID.randomUUID(), post = UUID.randomUUID(), order = UUID.randomUUID();
        UUID defaultRule = UUID.randomUUID(), categoryRule = UUID.randomUUID(), valueRule = UUID.randomUUID(), snapshot = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, password_hash) VALUES (?, ?, 'test-hash')", actor, actor + "@example.test");
        jdbc.update("INSERT INTO categories(id, name) VALUES (?, 'Historical commission category')", category);
        jdbc.update("INSERT INTO posts(id, user_id, title, status, created_at) VALUES (?, ?, 'Existing product', 'SOLD', now())", post, actor);
        jdbc.update("""
                INSERT INTO orders(id, post_id, buyer_id, seller_id, final_price, status, escrow_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1000000, 'DELIVERED', 'HELD', now(), now())
                """, order, post, actor, actor);
        jdbc.update("""
                INSERT INTO commission_rules(id, name, type, category_id, transaction_value_from, rate,
                    min_commission, max_commission, active, revision, updated_by, created_at, updated_at)
                VALUES (?, 'Default policy', 'DEFAULT', NULL, NULL, 0.05, 10000, 100000, true, 1, ?, now(), now()),
                       (?, 'Category policy', 'CATEGORY', ?, NULL, 0.02, 0, NULL, true, 1, ?, now(), now()),
                       (?, 'Value policy', 'TRANSACTION_VALUE', NULL, 0, 0.03, 0, NULL, true, 1, ?, now(), now())
                """, defaultRule, actor, categoryRule, category, actor, valueRule, actor);
        jdbc.update("""
                INSERT INTO order_commission_snapshots(id, order_id, rule_id, rule_revision, rule_name, rule_type,
                    category_id, rate, min_commission, base_type, commission_base, currency, snapshotted_at)
                VALUES (?, ?, ?, 1, 'Category policy', 'CATEGORY', ?, 0.02, 0, 'PRODUCT_PRICE', 1000000, 'VND', now())
                """, snapshot, order, categoryRule, category);
        jdbc.update("""
                INSERT INTO commission_rule_audits(id, rule_id, actor_id, action, new_value, reason, changed_at)
                VALUES (?, ?, ?, 'CREATE', '{"type":"CATEGORY","rate":0.02}', 'Historical audit', now())
                """, UUID.randomUUID(), categoryRule, actor);

        var upgrade = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()).load();
        assertEquals(2, upgrade.migrate().migrationsExecuted);
        assertTrue(jdbc.queryForObject("SELECT active FROM commission_rules WHERE id = ?", Boolean.class, defaultRule));
        assertEquals(new BigDecimal("0.050000"), jdbc.queryForObject("SELECT rate FROM commission_rules WHERE id = ?", BigDecimal.class, defaultRule));
        assertEquals(new BigDecimal("10000.00"), jdbc.queryForObject("SELECT min_commission FROM commission_rules WHERE id = ?", BigDecimal.class, defaultRule));
        assertEquals(new BigDecimal("100000.00"), jdbc.queryForObject("SELECT max_commission FROM commission_rules WHERE id = ?", BigDecimal.class, defaultRule));
        for (UUID retired : new UUID[]{categoryRule, valueRule}) {
            assertFalse(jdbc.queryForObject("SELECT active FROM commission_rules WHERE id = ?", Boolean.class, retired));
            assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE commission_rules SET active = true WHERE id = ?", retired));
        }
        assertEquals("CATEGORY", jdbc.queryForObject("SELECT rule_type FROM order_commission_snapshots WHERE id = ?", String.class, snapshot));
        assertEquals(new BigDecimal("0.020000"), jdbc.queryForObject("SELECT rate FROM order_commission_snapshots WHERE id = ?", BigDecimal.class, snapshot));
        assertEquals(1, jdbc.queryForObject("SELECT rule_revision FROM order_commission_snapshots WHERE id = ?", Integer.class, snapshot));
        assertEquals("Historical audit", jdbc.queryForObject("SELECT reason FROM commission_rule_audits WHERE rule_id = ?", String.class, categoryRule));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE order_commission_snapshots SET rate = 0.05 WHERE id = ?", snapshot));
        assertEquals(0, upgrade.migrate().migrationsExecuted);
        assertTrue(upgrade.validateWithResult().validationSuccessful);
    }
}
