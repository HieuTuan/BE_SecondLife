package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("databaseAvailable")
class HighValueInspectionMigrationTest {
    private static final String DATABASE_ENV = "HIGH_VALUE_INSPECTION_TEST_JDBC_URL";
    private static final GhnTestDatabase DATABASE = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }
    @AfterAll static void stopDatabase() { DATABASE.close(); }

    @Test
    void upgradesFromV87AndKeepsAlreadyPendingListingsSubjectToInspection() throws Exception {
        var config = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        config.target("87").load().migrate();
        try (var connection = DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            UUID seller = UUID.randomUUID();
            UUID pending = UUID.randomUUID();
            UUID active = UUID.randomUUID();
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'unused')", seller, seller + "@example.test");
            jdbc.update("""
                    INSERT INTO posts(id,user_id,title,status,price,created_at)
                    VALUES (?,?,'Pending inspection','PENDING_INSPECTION',11000000,CURRENT_TIMESTAMP),
                           (?,?,'Already published','ACTIVE',15000000,CURRENT_TIMESTAMP)
                    """, pending, seller, active, seller);

            var upgrade = config.target("88").load();
            assertEquals(1, upgrade.migrate().migrationsExecuted);
            assertEquals(new BigDecimal("10000000.00"),
                    jdbc.queryForObject("SELECT threshold_price FROM high_value_inspection_policy WHERE id=1", BigDecimal.class));
            assertTrue(jdbc.queryForObject("SELECT mandatory_inspection FROM posts WHERE id=?", Boolean.class, pending));
            assertNull(jdbc.queryForObject("SELECT mandatory_inspection FROM posts WHERE id=?", Boolean.class, active));
            assertEquals(0, upgrade.migrate().migrationsExecuted);
            assertTrue(upgrade.validateWithResult().validationSuccessful);
        }
    }
}
