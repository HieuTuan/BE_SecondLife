package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PermissionCatalogMigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void upgradesAppliedV16WithoutChangingChecksumOrLosingPermissionPolicies() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("16").load().migrate();

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword()); var statement = connection.createStatement()) {
            try (var history = statement.executeQuery(
                    "SELECT checksum FROM flyway_schema_history WHERE version = '16'")) {
                assertTrue(history.next());
                assertEquals(-771329655, history.getInt(1));
            }
            statement.executeUpdate("""
                    INSERT INTO permissions (code, name) VALUES ('REPORT_EXPORT', 'Reports');
                    INSERT INTO permission_assignable_roles (permission_id, role_code)
                    SELECT id, 'STAFF' FROM permissions WHERE code = 'REPORT_EXPORT';
                    """);
            // Model an existing development schema whose CHECK was not created by Hibernate.
            statement.executeUpdate("ALTER TABLE permission_assignable_roles "
                    + "DROP CONSTRAINT ck_permission_assignable_roles_no_admin");
        }

        Flyway flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword()).target("18").load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword()); var statement = connection.createStatement()) {
            try (var policies = statement.executeQuery("""
                    SELECT p.code, pr.role_code FROM permissions p
                    JOIN permission_assignable_roles pr ON pr.permission_id = p.id
                    WHERE p.code = 'REPORT_EXPORT'
                    """)) {
                assertTrue(policies.next());
                assertEquals("STAFF", policies.getString("role_code"));
            }
            try (var constraints = statement.executeQuery("""
                    SELECT
                      NOT EXISTS (SELECT 1 FROM pg_constraint
                        WHERE conrelid = 'permission_assignable_roles'::regclass
                          AND conname = 'permission_assignable_roles_role_code_fkey')
                      AND EXISTS (SELECT 1 FROM pg_constraint
                        WHERE conrelid = 'permission_assignable_roles'::regclass
                          AND conname = 'ck_permission_assignable_roles_no_admin')
                    """)) {
                assertTrue(constraints.next() && constraints.getBoolean(1));
            }
        }
    }
}
