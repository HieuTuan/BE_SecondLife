package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseAvailable")
class InspectionConsentV69MigrationTest {
    private static final String DATABASE_ENV = "GHN_FLOW_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() {
        return GhnTestDatabase.available(DATABASE_ENV);
    }

    @AfterAll
    static void closeDatabase() {
        database.close();
    }

    @Test
    void normalizesOnlyMissingConsentAndPreservesExistingDecisionsAndOrderData() throws Exception {
        inDatabase((url, jdbc) -> {
            jdbc.execute("""
                    ALTER TABLE inspection_orders
                        ALTER COLUMN buyer_consent_zin DROP DEFAULT,
                        ALTER COLUMN buyer_consent_zin DROP NOT NULL,
                        ALTER COLUMN seller_consent_zin DROP DEFAULT,
                        ALTER COLUMN seller_consent_zin DROP NOT NULL
                    """);
            UUID seller = user(jdbc);
            Boolean[] states = {null, false, true};
            for (Boolean buyerConsent : states) {
                for (Boolean sellerConsent : states) {
                    UUID post = post(jdbc, seller);
                    jdbc.update("""
                            INSERT INTO inspection_orders(id,post_id,seller_id,status,inspection_scope,
                                inspection_fee,shipping_fee,buyer_consent_zin,seller_consent_zin,note,created_at)
                            VALUES (?,?,?,'PENDING','PRE_LISTING',123000,45000,?,?,'Keep historical details',CURRENT_TIMESTAMP)
                            """, UUID.randomUUID(), post, seller, buyerConsent, sellerConsent);
                }
            }
            List<Map<String, Object>> expected = jdbc.queryForList("SELECT * FROM inspection_orders ORDER BY id")
                    .stream().map(row -> {
                        Map<String, Object> normalized = new LinkedHashMap<>(row);
                        normalized.put("buyer_consent_zin", Boolean.TRUE.equals(row.get("buyer_consent_zin")));
                        normalized.put("seller_consent_zin", Boolean.TRUE.equals(row.get("seller_consent_zin")));
                        return normalized;
                    }).toList();

            Flyway upgrade = flyway(url, "69");
            assertEquals(1, upgrade.migrate().migrationsExecuted);
            assertEquals(9, expected.size());
            assertEquals(expected, jdbc.queryForList("SELECT * FROM inspection_orders ORDER BY id"));
            assertEquals(0, upgrade.migrate().migrationsExecuted);
            assertTrue(upgrade.validateWithResult().validationSuccessful);
        });
    }

    @Test
    void newRowsDefaultToNoConsentAndExplicitNullsAreRejectedByPostgresql() throws Exception {
        inDatabase((url, jdbc) -> {
            flyway(url, "69").migrate();
            UUID seller = user(jdbc);
            UUID order = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO inspection_orders(id,post_id,seller_id,status,inspection_scope,inspection_fee,shipping_fee,created_at)
                    VALUES (?,?,?,'PENDING','PRE_LISTING',0,0,CURRENT_TIMESTAMP)
                    """, order, post(jdbc, seller), seller);
            assertFalse(jdbc.queryForObject("SELECT buyer_consent_zin FROM inspection_orders WHERE id=?", Boolean.class, order));
            assertFalse(jdbc.queryForObject("SELECT seller_consent_zin FROM inspection_orders WHERE id=?", Boolean.class, order));

            for (String column : List.of("buyer_consent_zin", "seller_consent_zin")) {
                var error = assertThrows(DataIntegrityViolationException.class,
                        () -> jdbc.update("UPDATE inspection_orders SET " + column + "=NULL WHERE id=?", order));
                assertEquals("23502", ((SQLException) error.getMostSpecificCause()).getSQLState());
                assertEquals("NO", jdbc.queryForObject("""
                        SELECT is_nullable FROM information_schema.columns
                        WHERE table_schema='public' AND table_name='inspection_orders' AND column_name=?
                        """, String.class, column));
            }
        });
    }

    private void inDatabase(DatabaseTest test) throws Exception {
        String name = "secondlife_ghn_test_inspection_" + UUID.randomUUID().toString().replace("-", "");
        String originalUrl = database.getJdbcUrl();
        String url = originalUrl.substring(0, originalUrl.lastIndexOf('/') + 1) + name;
        try (var connection = DriverManager.getConnection(originalUrl, database.getUsername(), database.getPassword())) {
            var admin = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            admin.execute("CREATE DATABASE " + name);
            try {
                flyway(url, "68").migrate();
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

    private UUID user(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')", id, id + "@example.test");
        return id;
    }

    private UUID post(JdbcTemplate jdbc, UUID seller) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO posts(id,user_id,title,status,price,created_at) VALUES (?,?,'Historical listing','PENDING_INSPECTION',1000000,CURRENT_TIMESTAMP)", id, seller);
        return id;
    }

    private interface DatabaseTest {
        void run(String url, JdbcTemplate jdbc) throws Exception;
    }
}
