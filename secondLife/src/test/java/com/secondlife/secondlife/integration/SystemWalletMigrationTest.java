package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("databaseAvailable")
class SystemWalletMigrationTest {
    private static final String DATABASE_ENV = "GHN_FLOW_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);
    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }
    @AfterAll static void closeDatabase() { database.close(); }

    @Test void reconcilesPaidHeldCompletedAndRefundedOrdersWithoutInventingFundsOrChangingUserBalances() throws Exception {
        inDatabase((jdbcUrl, jdbc) -> {
            UUID buyer = user(jdbc), seller = user(jdbc), buyerWallet = wallet(jdbc, buyer), sellerWallet = wallet(jdbc, seller);
            UUID held = order(jdbc, buyer, seller, "DELIVERED", "HELD");
            UUID completed = order(jdbc, buyer, seller, "COMPLETED", "RELEASED");
            UUID cancelled = order(jdbc, buyer, seller, "CANCELLED", "REFUNDED");
            UUID returned = order(jdbc, buyer, seller, "CANCELLED", "REFUNDED");
            UUID unpaid = order(jdbc, buyer, seller, "PROCESSING", "HELD");
            for (UUID id : new UUID[]{held, completed, cancelled, returned}) transaction(jdbc, buyerWallet, id, "PAYMENT", "-1027000");
            transaction(jdbc, sellerWallet, completed, "EARNING", "950000");
            transaction(jdbc, buyerWallet, cancelled, "REFUND", "1027000");
            transaction(jdbc, buyerWallet, returned, "REFUND", "1000000");
            var upgrade = flyway(jdbcUrl, "39");
            assertEquals(1, upgrade.migrate().migrationsExecuted);
            money("1000000", jdbc.queryForObject("SELECT held_balance FROM system_wallets", BigDecimal.class));
            money("81000", jdbc.queryForObject("SELECT shipping_balance FROM system_wallets", BigDecimal.class));
            money("50000", jdbc.queryForObject("SELECT revenue_balance FROM system_wallets", BigDecimal.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM system_order_funds WHERE order_id = ?", Integer.class, unpaid));
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM system_wallet_transactions WHERE type = 'OPENING_BALANCE'", Integer.class));
            money("0", jdbc.queryForObject("SELECT SUM(balance) FROM user_wallets", BigDecimal.class));
            assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE system_wallet_transactions SET held_delta = 0 WHERE order_id = ?", held));
            assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM system_wallet_transactions WHERE order_id = ?", held));
            assertEquals(0, upgrade.migrate().migrationsExecuted);
            assertTrue(upgrade.validateWithResult().validationSuccessful);
        });
    }

    @Test void inconsistentHistoricalDebitsFailMigrationRatherThanCreatingUnfundedEscrow() throws Exception {
        inDatabase((jdbcUrl, jdbc) -> {
            UUID buyer = user(jdbc), seller = user(jdbc), wallet = wallet(jdbc, buyer);
            UUID id = order(jdbc, buyer, seller, "DELIVERED", "HELD");
            transaction(jdbc, wallet, id, "PAYMENT", "-1");
            assertThrows(FlywayException.class, () -> flyway(jdbcUrl, "39").migrate());
            assertNull(jdbc.queryForObject("SELECT to_regclass('system_wallets')", String.class));
            money("0", jdbc.queryForObject("SELECT balance FROM user_wallets WHERE id = ?", BigDecimal.class, wallet));
        });
    }

    private interface DatabaseTest { void run(String jdbcUrl, JdbcTemplate jdbc) throws Exception; }
    private void inDatabase(DatabaseTest test) throws Exception {
        // Legacy migrations drop unqualified tables; another schema's public fallback is not safe isolation.
        String name = "secondlife_ghn_test_wallet_" + UUID.randomUUID().toString().replace("-", "");
        String originalUrl = database.getJdbcUrl();
        String testUrl = originalUrl.substring(0, originalUrl.lastIndexOf('/') + 1) + name;
        try (var adminConnection = DriverManager.getConnection(originalUrl, database.getUsername(), database.getPassword())) {
            var admin = new JdbcTemplate(new SingleConnectionDataSource(adminConnection, true));
            admin.execute("CREATE DATABASE " + name);
            try {
                flyway(testUrl, "38").migrate();
                try (var connection = DriverManager.getConnection(testUrl, database.getUsername(), database.getPassword())) {
                    test.run(testUrl, new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
                }
            } finally { admin.execute("DROP DATABASE " + name); }
        }
    }

    private Flyway flyway(String jdbcUrl, String version) {
        return Flyway.configure().dataSource(jdbcUrl, database.getUsername(), database.getPassword()).target(version).load();
    }
    private UUID user(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO users(id, email, password_hash) VALUES (?, ?, 'test-hash')", id, id + "@example.test"); return id;
    }
    private UUID wallet(JdbcTemplate jdbc, UUID user) {
        UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO user_wallets(id, user_id, balance, created_at, updated_at) VALUES (?, ?, 0, now(), now())", id, user); return id;
    }
    private UUID order(JdbcTemplate jdbc, UUID buyer, UUID seller, String status, String escrow) {
        UUID post = UUID.randomUUID(), id = UUID.randomUUID();
        jdbc.update("INSERT INTO posts(id, user_id, title, status, created_at) VALUES (?, ?, 'Product', 'SOLD', now())", post, seller);
        jdbc.update("""
            INSERT INTO orders(id, post_id, buyer_id, seller_id, final_price, shipping_fee, status, escrow_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, 1000000, 27000, ?, ?, now(), now())
            """, id, post, buyer, seller, status, escrow);
        return id;
    }
    private void transaction(JdbcTemplate jdbc, UUID wallet, UUID order, String type, String amount) {
        jdbc.update("INSERT INTO wallet_transactions(id, wallet_id, reference_id, type, amount, created_at) VALUES (?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), wallet, order, type, new BigDecimal(amount));
    }
    private void money(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
}
