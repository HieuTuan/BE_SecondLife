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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseAvailable")
class OrderDisputeMigrationTest {
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
    void preservesHistoricalRowsAndEnforcesRefundAuditOnUpgrade() throws Exception {
        String databaseName = "secondlife_ghn_test_dispute_" + UUID.randomUUID().toString().replace("-", "");
        String originalUrl = database.getJdbcUrl();
        String testUrl = originalUrl.substring(0, originalUrl.lastIndexOf('/') + 1) + databaseName;
        try (var adminConnection = DriverManager.getConnection(originalUrl, database.getUsername(), database.getPassword())) {
            var admin = new JdbcTemplate(new SingleConnectionDataSource(adminConnection, true));
            admin.execute("CREATE DATABASE " + databaseName);
            try {
                flyway(testUrl, "54").migrate();
                try (var connection = DriverManager.getConnection(testUrl, database.getUsername(), database.getPassword())) {
                    var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                    UUID sellerId = user(jdbc);
                    UUID buyerId = user(jdbc);
                    UUID reviewerId = user(jdbc);
                    UUID heldOrderId = order(jdbc, sellerId, buyerId, "HELD");
                    UUID frozenOrderId = order(jdbc, sellerId, buyerId, "FROZEN");
                    UUID postId = jdbc.queryForObject("SELECT post_id FROM orders WHERE id=?", UUID.class, frozenOrderId);
                    UUID reportId = UUID.randomUUID();
                    UUID shipmentId = UUID.randomUUID();
                    jdbc.update("""
                            INSERT INTO user_reports(id,reporter_id,request_id,seller_id,post_id,order_id,reason_code,
                                description,evidence_urls,status,reviewer_id,responsible_party,decision_reason,
                                created_at,updated_at,resolved_at)
                            VALUES (?,?,?,?,?,?,'RETURN_NOT_AS_DESCRIBED','Historical dispute',
                                '["private/legacy-evidence.jpg"]','CONFIRMED',?,'SELLER','Historical decision',
                                now(),now(),now())
                            """, reportId, buyerId, UUID.randomUUID(), sellerId, postId, frozenOrderId, reviewerId);
                    jdbc.update("""
                            INSERT INTO shipments(id,order_id,seller_id,buyer_id,leg,request_id,client_order_code,
                                order_code,status,payload,reason,quoted_fee,actual_fee,delivered_at,created_at,updated_at)
                            VALUES (?,?,?,?,'BUYER_TO_SELLER',?, ?,?,'DELIVERED','{"legacy":true}',
                                'Historical return',27000,26000,now(),now(),now())
                            """, shipmentId, frozenOrderId, sellerId, buyerId, UUID.randomUUID(),
                            "legacy-" + shipmentId, "carrier-" + shipmentId);
                    Map<String, Object> heldBefore = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", heldOrderId);
                    Map<String, Object> frozenBefore = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", frozenOrderId);
                    Map<String, Object> reportBefore = jdbc.queryForMap("SELECT * FROM user_reports WHERE id=?", reportId);
                    Map<String, Object> shipmentBefore = jdbc.queryForMap("SELECT * FROM shipments WHERE id=?", shipmentId);
                    Map<String, Object> walletBefore = jdbc.queryForMap("SELECT * FROM system_wallets WHERE id='MAIN'");

                    var upgrade = flyway(testUrl, "55");
                    assertEquals(1, upgrade.migrate().migrationsExecuted);

                    var heldAfter = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", heldOrderId);
                    var frozenAfter = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", frozenOrderId);
                    var reportAfter = jdbc.queryForMap("SELECT * FROM user_reports WHERE id=?", reportId);
                    var shipmentAfter = jdbc.queryForMap("SELECT * FROM shipments WHERE id=?", shipmentId);
                    assertHistoricalColumnsPreserved(heldBefore, heldAfter);
                    assertHistoricalColumnsPreserved(frozenBefore, frozenAfter);
                    assertHistoricalColumnsPreserved(reportBefore, reportAfter);
                    assertHistoricalColumnsPreserved(shipmentBefore, shipmentAfter);
                    assertEquals(walletBefore, jdbc.queryForMap("SELECT * FROM system_wallets WHERE id='MAIN'"));
                    assertEquals(Boolean.FALSE, heldAfter.get("shipping_hold"));
                    assertEquals(Boolean.TRUE, frozenAfter.get("shipping_hold"));
                    assertEquals("NONE", reportAfter.get("resolution_action"));
                    for (String field : List.of("return_shipment_id", "return_approved_at", "refunded_at", "refunded_by", "refund_amount")) {
                        assertNull(reportAfter.get(field), field);
                    }
                    assertNull(shipmentAfter.get("report_id"));

                    jdbc.update("""
                            UPDATE user_reports SET resolution_action='RETURN_AND_REFUND',return_approved_at=now(),
                                return_shipment_id=? WHERE id=?
                            """, shipmentId, reportId);
                    jdbc.update("UPDATE shipments SET report_id=? WHERE id=?", reportId, shipmentId);
                    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                            "UPDATE user_reports SET refunded_at=now() WHERE id=?", reportId));
                    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                            UPDATE user_reports SET refunded_at=now(),refund_amount=1000000 WHERE id=?
                            """, reportId));
                    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                            UPDATE user_reports SET refunded_at=now(),refunded_by=?,refund_amount=0 WHERE id=?
                            """, reviewerId, reportId));
                    jdbc.update("""
                            UPDATE user_reports SET refunded_at=now(),refunded_by=?,refund_amount=1000000 WHERE id=?
                            """, reviewerId, reportId);
                    assertEquals(new BigDecimal("1000000.00"), jdbc.queryForObject(
                            "SELECT refund_amount FROM user_reports WHERE id=?", BigDecimal.class, reportId));
                    assertTrue(jdbc.queryForObject("SELECT refunded_at IS NOT NULL FROM user_reports WHERE id=?", Boolean.class, reportId));
                    assertEquals(reviewerId, jdbc.queryForObject("SELECT refunded_by FROM user_reports WHERE id=?", UUID.class, reportId));
                    assertFalse(jdbc.queryForObject("SELECT shipping_hold FROM orders WHERE id=?", Boolean.class, heldOrderId));
                    assertTrue(jdbc.queryForObject("SELECT shipping_hold FROM orders WHERE id=?", Boolean.class, frozenOrderId));
                    assertEquals(0, upgrade.migrate().migrationsExecuted);
                    assertTrue(upgrade.validateWithResult().validationSuccessful);
                }
            } finally {
                admin.execute("DROP DATABASE " + databaseName);
            }
        }
    }

    private Flyway flyway(String jdbcUrl, String version) {
        return Flyway.configure().dataSource(jdbcUrl, database.getUsername(), database.getPassword()).target(version).load();
    }

    private UUID user(JdbcTemplate jdbc) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')", userId, userId + "@example.test");
        return userId;
    }

    private UUID order(JdbcTemplate jdbc, UUID sellerId, UUID buyerId, String escrowStatus) {
        UUID postId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        jdbc.update("INSERT INTO posts(id,user_id,title,status,created_at) VALUES (?,?,'Historical product','SOLD',now())", postId, sellerId);
        jdbc.update("""
                INSERT INTO orders(id,post_id,seller_id,buyer_id,final_price,shipping_fee,status,escrow_status,
                    delivery_address,created_at,updated_at)
                VALUES (?,?,?,?,1000000,27000,'DELIVERED',?,'Historical delivery address',now(),now())
                """, orderId, postId, sellerId, buyerId, escrowStatus);
        return orderId;
    }

    private void assertHistoricalColumnsPreserved(Map<String, Object> before, Map<String, Object> after) {
        before.forEach((column, value) -> assertEquals(value, after.get(column), column));
    }
}
