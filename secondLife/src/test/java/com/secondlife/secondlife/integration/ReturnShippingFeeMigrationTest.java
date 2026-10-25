package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseAvailable")
class ReturnShippingFeeMigrationTest {
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
    void preservesHistoricalClosedRefundsAndFinancialRowsWithoutBackfillingFees() throws Exception {
        inDatabase((jdbcUrl, jdbc) -> {
            UUID seller = user(jdbc), buyer = user(jdbc), reviewer = user(jdbc);
            var returned = returnedOrder(jdbc, seller, buyer, reviewer);
            UUID wallet = wallet(jdbc, buyer);
            transaction(jdbc, wallet, returned.orderId(), "REFUND", 1000000);
            jdbc.update("UPDATE system_wallets SET shipping_balance=27000,revenue_balance=50000 WHERE id='MAIN'");
            jdbc.update("""
                    INSERT INTO system_wallet_transactions(order_id,type,held_delta,shipping_delta,revenue_delta,
                        held_balance_after,shipping_balance_after,revenue_balance_after,created_at)
                    VALUES (?,'REFUND',-1000000,0,0,0,27000,50000,now())
                    """, returned.orderId());
            Map<String, List<Map<String, Object>>> historical = new LinkedHashMap<>();
            for (String table : List.of("users", "user_wallets", "wallet_transactions", "system_wallets",
                    "system_wallet_transactions", "orders", "shipments", "user_reports")) {
                historical.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
            }

            var upgrade = latest(jdbcUrl);
            assertEquals(1, upgrade.migrate().migrationsExecuted);

            historical.forEach((table, rows) ->
                    assertEquals(rows, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"), table));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM return_shipping_fees", Integer.class));
            assertEquals(0, upgrade.migrate().migrationsExecuted);
            assertTrue(upgrade.validateWithResult().validationSuccessful);
        });
    }

    @Test
    void acceptsCoherentAllocationsAndRejectsInvalidAmountsStatusesAndAudit() throws Exception {
        inDatabase((jdbcUrl, jdbc) -> {
            latest(jdbcUrl).migrate();
            UUID seller = user(jdbc), buyer = user(jdbc), reviewer = user(jdbc);
            var pending = returnedOrder(jdbc, seller, buyer, reviewer);
            var paid = returnedOrder(jdbc, seller, buyer, reviewer);
            var outstanding = returnedOrder(jdbc, seller, buyer, reviewer);
            fee(jdbc, pending, seller, "PENDING_FEE", null, 0, 0, false, null);
            fee(jdbc, paid, seller, "PAID", 26000, 26000, 0, true, reviewer);
            fee(jdbc, outstanding, seller, "OUTSTANDING", 26000, 10000, 16000, true, reviewer);
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM return_shipping_fees", Integer.class));
            assertEquals(new BigDecimal("16000.00"), jdbc.queryForObject(
                    "SELECT debt_amount FROM return_shipping_fees WHERE id=?", BigDecimal.class, outstanding.shipmentId()));

            var invalid = returnedOrder(jdbc, seller, buyer, reviewer);
            invalidFee(jdbc, invalid, seller, "PAID", 26000, 25000, 0, true, reviewer);
            invalidFee(jdbc, invalid, seller, "UNKNOWN", 26000, 26000, 0, true, reviewer);
            invalidFee(jdbc, invalid, seller, "PAID", -1, -1, 0, true, reviewer);
            invalidFee(jdbc, invalid, seller, "OUTSTANDING", 26000, -1, 26001, true, reviewer);
            invalidFee(jdbc, invalid, seller, "PAID", 26000, 26001, -1, true, reviewer);
            invalidFee(jdbc, invalid, seller, "PAID", 1000000001, 1000000001, 0, true, reviewer);
            invalidFee(jdbc, invalid, seller, "PAID", null, 0, 0, true, reviewer);
            invalidFee(jdbc, invalid, seller, "PAID", 26000, 26000, 0, false, reviewer);
            invalidFee(jdbc, invalid, seller, "PAID", 26000, 26000, 0, true, null);
            invalidFee(jdbc, invalid, seller, "PENDING_FEE", null, 0, 0, true, reviewer);
            invalidFee(jdbc, invalid, seller, "PENDING_FEE", 0, 0, 0, false, null);
            invalidFee(jdbc, invalid, seller, "PAID", 26000, 25000, 1000, true, reviewer);
            invalidFee(jdbc, invalid, seller, "OUTSTANDING", 26000, 26000, 0, true, reviewer);
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM return_shipping_fees", Integer.class));
        });
    }

    @Test
    void permitsOnlyOnePendingAssessmentAndPreservesFeeIdentityAndAssessedRows() throws Exception {
        inDatabase((jdbcUrl, jdbc) -> {
            latest(jdbcUrl).migrate();
            UUID seller = user(jdbc), buyer = user(jdbc), reviewer = user(jdbc);
            var paid = returnedOrder(jdbc, seller, buyer, reviewer);
            var outstanding = returnedOrder(jdbc, seller, buyer, reviewer);
            var other = returnedOrder(jdbc, seller, buyer, reviewer);
            fee(jdbc, paid, seller, "PENDING_FEE", null, 0, 0, false, null);
            fee(jdbc, outstanding, seller, "PENDING_FEE", null, 0, 0, false, null);
            var pendingBefore = jdbc.queryForMap("SELECT * FROM return_shipping_fees WHERE id=?", paid.shipmentId());
            Map<String, Object> replacements = Map.of(
                    "id", other.shipmentId(), "order_id", other.orderId(), "report_id", other.reportId(),
                    "seller_id", buyer, "created_at", Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")));
            replacements.forEach((column, value) -> assertThrows(DataAccessException.class, () -> jdbc.update("""
                    UPDATE return_shipping_fees SET status='PAID',fee_amount=26000,collected_amount=26000,
                        assessed_at=now(),assessed_by=?, """ + column + "=? WHERE id=?",
                    reviewer, value, paid.shipmentId()), column));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE return_shipping_fees SET collected_amount=0 WHERE id=?", paid.shipmentId()));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "DELETE FROM return_shipping_fees WHERE id=?", paid.shipmentId()));
            assertEquals(pendingBefore, jdbc.queryForMap("SELECT * FROM return_shipping_fees WHERE id=?", paid.shipmentId()));

            assertEquals(1, jdbc.update("""
                    UPDATE return_shipping_fees SET status='PAID',fee_amount=26000,collected_amount=26000,
                        assessed_at=now(),assessed_by=? WHERE id=?
                    """, reviewer, paid.shipmentId()));
            assertEquals(1, jdbc.update("""
                    UPDATE return_shipping_fees SET status='OUTSTANDING',fee_amount=26000,collected_amount=10000,
                        debt_amount=16000,assessed_at=now(),assessed_by=? WHERE id=?
                    """, reviewer, outstanding.shipmentId()));
            assertEquals("PAID", jdbc.queryForObject(
                    "SELECT status FROM return_shipping_fees WHERE id=?", String.class, paid.shipmentId()));
            assertEquals("OUTSTANDING", jdbc.queryForObject(
                    "SELECT status FROM return_shipping_fees WHERE id=?", String.class, outstanding.shipmentId()));
            for (UUID id : List.of(paid.shipmentId(), outstanding.shipmentId())) {
                var assessed = jdbc.queryForMap("SELECT * FROM return_shipping_fees WHERE id=?", id);
                assertThrows(DataAccessException.class, () -> jdbc.update(
                        "UPDATE return_shipping_fees SET collected_amount=collected_amount WHERE id=?", id));
                assertThrows(DataAccessException.class, () -> jdbc.update(
                        "UPDATE return_shipping_fees SET status='PAID',collected_amount=26000,debt_amount=0 WHERE id=?", id));
                assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM return_shipping_fees WHERE id=?", id));
                assertEquals(assessed, jdbc.queryForMap("SELECT * FROM return_shipping_fees WHERE id=?", id));
            }
        });
    }

    @Test
    void acceptsSystemReturnFeeEntriesAndUniquelyDeduplicatesOnlyWalletReturnFees() throws Exception {
        inDatabase((jdbcUrl, jdbc) -> {
            latest(jdbcUrl).migrate();
            UUID seller = user(jdbc), buyer = user(jdbc), reviewer = user(jdbc);
            var returned = returnedOrder(jdbc, seller, buyer, reviewer);
            UUID sellerWallet = wallet(jdbc, seller), buyerWallet = wallet(jdbc, buyer);
            jdbc.update("""
                    INSERT INTO system_wallet_transactions(order_id,type,held_delta,shipping_delta,revenue_delta,
                        held_balance_after,shipping_balance_after,revenue_balance_after,created_at)
                    VALUES (?,'RETURN_SHIPPING_FEE',0,26000,0,0,26000,0,now())
                    """, returned.orderId());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM system_wallet_transactions WHERE type='RETURN_SHIPPING_FEE'", Integer.class));

            transaction(jdbc, sellerWallet, returned.shipmentId(), "RETURN_SHIPPING_FEE", -26000);
            assertThrows(DataIntegrityViolationException.class, () ->
                    transaction(jdbc, sellerWallet, returned.shipmentId(), "RETURN_SHIPPING_FEE", -26000));
            assertThrows(DataIntegrityViolationException.class, () ->
                    transaction(jdbc, buyerWallet, returned.shipmentId(), "RETURN_SHIPPING_FEE", -26000));
            transaction(jdbc, buyerWallet, returned.shipmentId(), "REFUND", 26000);
            transaction(jdbc, buyerWallet, returned.shipmentId(), "REFUND", 26000);
            transaction(jdbc, sellerWallet, UUID.randomUUID(), "RETURN_SHIPPING_FEE", -10000);
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM wallet_transactions WHERE type='RETURN_SHIPPING_FEE'", Integer.class));
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM wallet_transactions WHERE type='REFUND'", Integer.class));
        });
    }

    private interface DatabaseTest {
        void run(String jdbcUrl, JdbcTemplate jdbc) throws Exception;
    }

    private void inDatabase(DatabaseTest test) throws Exception {
        String name = "secondlife_ghn_test_return_fee_" + UUID.randomUUID().toString().replace("-", "");
        String originalUrl = database.getJdbcUrl();
        String testUrl = originalUrl.substring(0, originalUrl.lastIndexOf('/') + 1) + name;
        try (var adminConnection = DriverManager.getConnection(originalUrl, database.getUsername(), database.getPassword())) {
            var admin = new JdbcTemplate(new SingleConnectionDataSource(adminConnection, true));
            admin.execute("CREATE DATABASE " + name);
            try {
                Flyway.configure().dataSource(testUrl, database.getUsername(), database.getPassword()).target("58").load().migrate();
                try (var connection = DriverManager.getConnection(testUrl, database.getUsername(), database.getPassword())) {
                    test.run(testUrl, new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
                }
            } finally {
                admin.execute("DROP DATABASE " + name);
            }
        }
    }

    private Flyway latest(String jdbcUrl) {
        return Flyway.configure().dataSource(jdbcUrl, database.getUsername(), database.getPassword()).load();
    }

    private UUID user(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')", id, id + "@example.test");
        return id;
    }

    private UUID wallet(JdbcTemplate jdbc, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_wallets(id,user_id,balance,created_at,updated_at) VALUES (?,?,1000000,now(),now())
                """, id, userId);
        return id;
    }

    private void transaction(JdbcTemplate jdbc, UUID walletId, UUID referenceId, String type, int amount) {
        jdbc.update("""
                INSERT INTO wallet_transactions(id,wallet_id,reference_id,type,amount,created_at) VALUES (?,?,?,?,?,now())
                """, UUID.randomUUID(), walletId, referenceId, type, amount);
    }

    private record ReturnedOrder(UUID orderId, UUID reportId, UUID shipmentId) {
    }

    private ReturnedOrder returnedOrder(JdbcTemplate jdbc, UUID seller, UUID buyer, UUID reviewer) {
        UUID postId = UUID.randomUUID(), orderId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID(), shipmentId = UUID.randomUUID();
        jdbc.update("INSERT INTO posts(id,user_id,title,status,created_at) VALUES (?,?,'Returned product','SOLD',now())", postId, seller);
        jdbc.update("""
                INSERT INTO orders(id,post_id,seller_id,buyer_id,final_price,shipping_fee,status,escrow_status,
                    delivery_address,created_at,updated_at)
                VALUES (?,?,?,?,1000000,27000,'CANCELLED','REFUNDED','Historical address',now(),now())
                """, orderId, postId, seller, buyer);
        jdbc.update("""
                INSERT INTO user_reports(id,reporter_id,request_id,seller_id,post_id,order_id,reason_code,
                    description,evidence_urls,status,reviewer_id,responsible_party,decision_reason,
                    resolution_action,return_approved_at,created_at,updated_at,resolved_at)
                VALUES (?,?,?,?,?,?,'RETURN_NOT_AS_DESCRIBED','Historical dispute','["private/legacy.jpg"]',
                    'CONFIRMED',?,'SELLER','Historical decision','RETURN_AND_REFUND',now(),now(),now(),now())
                """, reportId, buyer, UUID.randomUUID(), seller, postId, orderId, reviewer);
        jdbc.update("""
                INSERT INTO shipments(id,order_id,seller_id,buyer_id,leg,request_id,client_order_code,order_code,
                    status,payload,reason,quoted_fee,actual_fee,report_id,delivered_at,created_at,updated_at)
                VALUES (?,?,?,?,'BUYER_TO_SELLER',?, ?,?,'DELIVERED','{"legacy":true}','Historical return',
                    27000,26000,?,now(),now(),now())
                """, shipmentId, orderId, seller, buyer, UUID.randomUUID(), "legacy-" + shipmentId,
                "carrier-" + shipmentId, reportId);
        jdbc.update("""
                UPDATE user_reports SET return_shipment_id=?,refunded_at=now(),refunded_by=?,refund_amount=1000000
                WHERE id=?
                """, shipmentId, reviewer, reportId);
        return new ReturnedOrder(orderId, reportId, shipmentId);
    }

    private void fee(JdbcTemplate jdbc, ReturnedOrder returned, UUID seller, String status, Integer amount,
                     int collected, int debt, boolean assessedAt, UUID assessedBy) {
        jdbc.update("""
                INSERT INTO return_shipping_fees(id,order_id,report_id,seller_id,status,fee_amount,
                    collected_amount,debt_amount,assessed_at,assessed_by)
                VALUES (?,?,?,?,?,?,?,?,CASE WHEN ? THEN now() ELSE NULL END,?)
                """, returned.shipmentId(), returned.orderId(), returned.reportId(), seller, status,
                amount, collected, debt, assessedAt, assessedBy);
    }

    private void invalidFee(JdbcTemplate jdbc, ReturnedOrder returned, UUID seller, String status, Integer amount,
                            int collected, int debt, boolean assessedAt, UUID assessedBy) {
        assertThrows(DataIntegrityViolationException.class,
                () -> fee(jdbc, returned, seller, status, amount, collected, debt, assessedAt, assessedBy),
                status + ": fee=" + amount + ", collected=" + collected + ", debt=" + debt
                        + ", assessedAt=" + assessedAt + ", assessedBy=" + assessedBy);
    }
}
