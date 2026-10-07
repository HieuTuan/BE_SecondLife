package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class RbacV18MigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4-alpine");

    @Test
    void upgradesExistingPermissionTableWithoutUuidDefaultAndPreservesExistingMappings() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("17").load().migrate();
        UUID originalPermissionId;
        long originalMappingCount;
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            // Existing application schemas may have been created/updated by Hibernate, without SQL UUID defaults.
            statement.executeUpdate("ALTER TABLE permissions ALTER COLUMN id DROP DEFAULT");
            try (var rows = statement.executeQuery("SELECT id FROM permissions WHERE code = 'ADMIN_RBAC_MANAGE'")) {
                assertTrue(rows.next());
                originalPermissionId = rows.getObject(1, UUID.class);
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM role_permissions")) {
                assertTrue(rows.next()); originalMappingCount = rows.getLong(1);
            }
        }

        Flyway flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).target("18").load();
        assertDoesNotThrow(() -> flyway.migrate(), "V18 must explicitly supply UUIDs when permissions.id has no default");
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals("18", flyway.info().current().getVersion().toString());

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM permissions WHERE code IN (
                        'INSPECTION_ORDER_READ_SELF', 'INSPECTION_ORDER_READ_ANY', 'INSPECTION_ORDER_ASSIGN',
                        'INSPECTION_STAFF_MANAGE', 'AI_CHAT_SELF', 'MEDIA_UPLOAD_SELF') AND id IS NOT NULL
                    """)) {
                assertTrue(rows.next()); assertEquals(6, rows.getLong(1));
            }
            try (var rows = statement.executeQuery("SELECT id FROM permissions WHERE code = 'ADMIN_RBAC_MANAGE'")) {
                assertTrue(rows.next()); assertEquals(originalPermissionId, rows.getObject(1, UUID.class));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM role_permissions")) {
                assertTrue(rows.next()); assertEquals(originalMappingCount + 20, rows.getLong(1));
            }
            try (var rows = statement.executeQuery("SELECT to_regclass('public.user_role_audit') IS NOT NULL")) {
                assertTrue(rows.next()); assertTrue(rows.getBoolean(1));
            }
        }
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }
}
