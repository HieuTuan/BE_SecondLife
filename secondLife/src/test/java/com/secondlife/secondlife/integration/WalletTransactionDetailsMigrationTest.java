package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseAvailable")
class WalletTransactionDetailsMigrationTest {
    private static final String DATABASE_ENV = "WALLET_HISTORY_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);
    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }
    @AfterAll static void closeDatabase() { database.close(); }

    @Test
    void migrationKeepsOldFinancialDataAndRejectsInconsistentNewSnapshots() throws Exception {
        String name = "secondlife_ghn_test_wallet_v60_" + UUID.randomUUID().toString().replace("-", "");
        String base = database.getJdbcUrl();
        String url = base.substring(0, base.lastIndexOf('/') + 1) + name;
        try (var adminConnection = DriverManager.getConnection(base, database.getUsername(), database.getPassword())) {
            JdbcTemplate admin = new JdbcTemplate(new SingleConnectionDataSource(adminConnection, true));
            admin.execute("CREATE DATABASE " + name);
            try {
                Flyway.configure().dataSource(url, database.getUsername(), database.getPassword()).target("59").load().migrate();
                try (var connection = DriverManager.getConnection(url, database.getUsername(), database.getPassword())) {
                    JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                    UUID user = UUID.randomUUID(), wallet = UUID.randomUUID(), old = UUID.randomUUID();
                    jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')", user, user + "@example.test");
                    jdbc.update("INSERT INTO user_wallets(id,user_id,balance,created_at,updated_at) VALUES (?,?,75,now(),now())", wallet, user);
                    jdbc.update("INSERT INTO wallet_transactions(id,wallet_id,type,amount,created_at) VALUES (?,?,'PAYMENT',-25,now())", old, wallet);
                    var before = jdbc.queryForMap("SELECT id,wallet_id,type,amount,created_at,reference_id FROM wallet_transactions WHERE id=?", old);
                    Flyway migration = Flyway.configure().dataSource(url, database.getUsername(), database.getPassword()).load();
                    assertEquals(1, migration.migrate().migrationsExecuted);
                    assertEquals(before, jdbc.queryForMap("SELECT id,wallet_id,type,amount,created_at,reference_id FROM wallet_transactions WHERE id=?", old));
                    var legacy = jdbc.queryForMap("SELECT balance_before,balance_after,reference_type FROM wallet_transactions WHERE id=?", old);
                    assertNull(legacy.get("balance_before"));
                    assertNull(legacy.get("balance_after"));
                    assertEquals("UNKNOWN", legacy.get("reference_type"));
                    assertEquals(new BigDecimal("75.00"), jdbc.queryForObject("SELECT balance FROM user_wallets WHERE id=?", BigDecimal.class, wallet));
                    insert(jdbc, wallet, "100", "75", "ORDER");
                    assertThrows(DataIntegrityViolationException.class, () -> insert(jdbc, wallet, "100", "70", "ORDER"));
                    assertThrows(DataIntegrityViolationException.class, () -> insert(jdbc, wallet, "10", "-15", "ORDER"));
                    assertThrows(DataIntegrityViolationException.class, () -> insert(jdbc, wallet, null, "75", "ORDER"));
                    assertThrows(DataIntegrityViolationException.class, () -> insert(jdbc, wallet, "100", "75", "NOT_A_REFERENCE"));
                    assertEquals(0, migration.migrate().migrationsExecuted);
                    assertTrue(migration.validateWithResult().validationSuccessful);
                }
            } finally {
                admin.execute("DROP DATABASE " + name);
            }
        }
    }

    private void insert(JdbcTemplate jdbc, UUID wallet, String before, String after, String referenceType) {
        jdbc.update("""
                INSERT INTO wallet_transactions(id,wallet_id,type,amount,created_at,balance_before,balance_after,reference_type)
                VALUES (?,?,'PAYMENT',-25,now(),?,?,?)
                """, UUID.randomUUID(), wallet, before != null ? new BigDecimal(before) : null,
                after != null ? new BigDecimal(after) : null, referenceType);
    }
}
