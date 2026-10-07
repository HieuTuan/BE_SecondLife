package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("databaseAvailable")
class GhnMigrationUpgradeTest {
    private static final GhnTestDatabase database = new GhnTestDatabase("GHN_MIGRATION_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("GHN_MIGRATION_TEST_JDBC_URL"); }
    @AfterAll static void stopOwnContainer() { database.close(); }

    @Test void upgradeExpandsLegacyHibernateOrderChecksAndKeepsExistingOrders() {
        Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .target("23").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS negotiations (
                  id UUID PRIMARY KEY, post_id UUID NOT NULL REFERENCES posts(id), buyer_id UUID NOT NULL REFERENCES users(id),
                  offered_price NUMERIC(18,2) NOT NULL, status VARCHAR(20) NOT NULL,
                  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, expired_at TIMESTAMPTZ)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                  id UUID PRIMARY KEY, post_id UUID NOT NULL REFERENCES posts(id), buyer_id UUID NOT NULL REFERENCES users(id),
                  seller_id UUID NOT NULL REFERENCES users(id), negotiation_id UUID REFERENCES negotiations(id),
                  final_price NUMERIC(18,2) NOT NULL, status VARCHAR(20) NOT NULL, escrow_status VARCHAR(20) NOT NULL,
                  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL)
                """);
        jdbc.execute("""
                ALTER TABLE orders ADD CONSTRAINT orders_status_check
                CHECK (status IN ('PENDING_PAYMENT', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED'))
                """);
        jdbc.execute("""
                ALTER TABLE orders ADD CONSTRAINT orders_escrow_status_check
                CHECK (escrow_status IN ('HELD', 'RELEASED', 'REFUNDED'))
                """);
        UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), post = UUID.randomUUID(), order = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, password_hash) VALUES (?, ?, 'test-hash'), (?, ?, 'test-hash')",
                seller, "legacy-seller@example.test", buyer, "legacy-buyer@example.test");
        jdbc.update("INSERT INTO posts(id, user_id, title, status, price, created_at) VALUES (?, ?, 'Legacy order product', 'SOLD', 1000000, now())", post, seller);
        jdbc.update("""
                INSERT INTO orders(id, post_id, buyer_id, seller_id, final_price, status, escrow_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1000000, 'PROCESSING', 'HELD', now(), now())
                """, order, post, buyer, seller);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE orders SET status = 'DELIVERED' WHERE id = ?", order));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE orders SET escrow_status = 'FROZEN' WHERE id = ?", order));

        var upgraded = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()).target("24").load();
        assertEquals(1, upgraded.migrate().migrationsExecuted);
        assertTrue(upgraded.validateWithResult().validationSuccessful);
        assertEquals(1, jdbc.update("UPDATE orders SET status = 'DELIVERED', escrow_status = 'FROZEN' WHERE id = ?", order));
        assertEquals("DELIVERED", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order));
        assertEquals("FROZEN", jdbc.queryForObject("SELECT escrow_status FROM orders WHERE id = ?", String.class, order));
        assertEquals(post, jdbc.queryForObject("SELECT post_id FROM orders WHERE id = ?", UUID.class, order));
        assertEquals(0, new BigDecimal("1000000").compareTo(jdbc.queryForObject("SELECT final_price FROM orders WHERE id = ?", BigDecimal.class, order)));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE orders SET status = 'INVALID' WHERE id = ?", order));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE orders SET escrow_status = 'INVALID' WHERE id = ?", order));
        assertEquals(0, upgraded.migrate().migrationsExecuted);
    }
}
