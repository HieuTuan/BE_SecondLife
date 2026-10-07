package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class MainFlowV19MigrationTest {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test void upgradesLegacyPostTableAndMarksAcceptedPostsWithoutChargingDrafts() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("18").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            // Bridge an existing Hibernate-created table instead of assuming a fresh install.
            statement.execute("CREATE TABLE posts(id UUID PRIMARY KEY, user_id UUID, status VARCHAR(255), created_at TIMESTAMPTZ)");
            statement.execute("ALTER TABLE permissions ALTER COLUMN id DROP DEFAULT");
            statement.execute("INSERT INTO posts(id, status, created_at) VALUES ('" + UUID.randomUUID() + "', 'ACTIVE', now()), ('"
                    + UUID.randomUUID() + "', 'PENDING_INSPECTION', now()), ('" + UUID.randomUUID() + "', 'DRAFT', now())");
        }
        var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).target("19").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals("19", flyway.info().current().getVersion().toString());
        assertTrue(flyway.validateWithResult().validationSuccessful);
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT status, listing_credit_charged, published_at FROM posts")) {
                int count = 0;
                while (rows.next()) {
                    boolean accepted = !"DRAFT".equals(rows.getString(1));
                    assertEquals(accepted, rows.getBoolean(2));
                    assertEquals(accepted, rows.getTimestamp(3) != null); count++;
                }
                assertEquals(3, count);
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM credit_ledger")) {
                assertTrue(rows.next()); assertEquals(0, rows.getLong(1));
            }
            try (var rows = statement.executeQuery("""
                    SELECT r.code FROM roles r JOIN role_permissions rp ON r.id = rp.role_id
                    JOIN permissions p ON p.id = rp.permission_id WHERE p.code = 'LISTING_VALUATION_SELF' ORDER BY r.code
                    """)) {
                assertTrue(rows.next()); assertEquals("ADMIN", rows.getString(1));
                assertTrue(rows.next()); assertEquals("SELLER", rows.getString(1)); assertFalse(rows.next());
            }
        }
        assertEquals(0, flyway.migrate().migrationsExecuted);
        var latest = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).target("20").load();
        assertEquals(1, latest.migrate().migrationsExecuted);
        assertTrue(latest.validateWithResult().validationSuccessful, "Previously applied V19 must still validate");
        assertEquals("20", latest.info().current().getVersion().toString());
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement(); var rows = statement.executeQuery("""
                     SELECT (SELECT checksum FROM flyway_schema_history WHERE version = '19') = 1319288901
                        AND EXISTS(SELECT 1 FROM pg_constraint WHERE conname = 'ck_ai_estimate_valid_request')
                        AND EXISTS(SELECT 1 FROM pg_trigger WHERE tgname = 'trg_ai_estimate_immutable')
                        AND EXISTS(SELECT 1 FROM permission_assignable_roles a JOIN permissions p ON p.id = a.permission_id
                            WHERE p.code = 'LISTING_VALUATION_SELF' AND a.role_code = 'SELLER')
                     """)) {
            assertTrue(rows.next() && rows.getBoolean(1));
        }
        assertEquals(0, latest.migrate().migrationsExecuted);
    }
}
