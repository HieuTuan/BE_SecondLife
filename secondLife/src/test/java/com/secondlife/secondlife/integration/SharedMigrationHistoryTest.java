package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseAvailable")
class SharedMigrationHistoryTest {
    private static final String DATABASE_ENV = "SHARED_MIGRATION_HISTORY_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }
    @AfterAll static void stopOwnContainer() { database.close(); }

    @Test void validatesChatMigrationsAlreadyAppliedByTheDevBranch() {
        Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .target("27").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
        // Reproduce the applied metadata in the shared database, including its old V28 script label.
        jdbc.update("""
                INSERT INTO flyway_schema_history(installed_rank, version, description, type, script, checksum,
                    installed_by, execution_time, success)
                VALUES (28, '28', 'add chat room and message tables', 'SQL',
                            'V28__commission_rules_order_snapshots_and_settlements.sql', 1178460597, 'test', 0, true),
                       (29, '29', 'fix chat tables', 'SQL', 'V29__fix_chat_tables.sql', -379692215, 'test', 0, true),
                       (30, '30', 'add address to user profile', 'SQL', 'V30__add_address_to_user_profile.sql', -1490392398, 'test', 0, true),
                       (31, '31', 'add unique constraint to chat rooms', 'SQL', 'V31__add_unique_constraint_to_chat_rooms.sql', 1047659578, 'test', 0, true)
                """);
        var result = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .ignoreMigrationPatterns("*:pending").load().validateWithResult();
        assertTrue(result.validationSuccessful, () -> "Applied dev migrations must remain valid: " + result.invalidMigrations);
    }
}
