package com.secondlife.secondlife.integration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("databaseAvailable")
class SellerReputationMigrationTest {
    private static final GhnTestDatabase database=new GhnTestDatabase("GHN_FLOW_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("GHN_FLOW_TEST_JDBC_URL"); }
    @AfterAll static void closeDatabase() { database.close(); }
    @Test void importsOnlyCompletedReleasedSalesWithoutChangingMoneyAndIsReproducible() throws Exception {
        String original=database.getJdbcUrl(); String name="secondlife_ghn_test_reputation_upgrade_"+UUID.randomUUID().toString().replace("-","");
        String url=original.substring(0,original.lastIndexOf('/')+1)+name;
        try (var adminConnection=DriverManager.getConnection(original,database.getUsername(),database.getPassword())) {
            var admin=new JdbcTemplate(new SingleConnectionDataSource(adminConnection,true));
            admin.execute("CREATE DATABASE "+name);
            try {
                flyway(url,"39").migrate();
                try (var connection=DriverManager.getConnection(url,database.getUsername(),database.getPassword())) {
                    var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                    UUID seller=user(jdbc),buyer=user(jdbc);
                    sale(jdbc,seller,buyer,"COMPLETED","RELEASED"); sale(jdbc,seller,buyer,"COMPLETED","RELEASED");
                    sale(jdbc,seller,buyer,"DELIVERED","HELD"); sale(jdbc,seller,buyer,"CANCELLED","REFUNDED");
                    var balanceBefore=jdbc.queryForMap("SELECT held_balance,shipping_balance,revenue_balance FROM system_wallets");
                    var upgrade=flyway(url,"40"); assertEquals(1,upgrade.migrate().migrationsExecuted);
                    assertEquals(2L,jdbc.queryForObject("SELECT good_transactions FROM seller_reputations WHERE seller_id=?",Long.class,seller));
                    assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM reputation_order_outcomes",Integer.class));
                    assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM seller_reputation_events WHERE event_type='HISTORICAL_ORDER_IMPORTED' AND score_after IS NULL",Integer.class));
                    assertEquals(balanceBefore,jdbc.queryForMap("SELECT held_balance,shipping_balance,revenue_balance FROM system_wallets"));
                    assertEquals(0,upgrade.migrate().migrationsExecuted); assertTrue(upgrade.validateWithResult().validationSuccessful);
                    UUID pending=UUID.randomUUID(),post=UUID.randomUUID(),reviewer=user(jdbc),report=UUID.randomUUID();
                    jdbc.update("INSERT INTO posts(id,user_id,title,status,created_at) VALUES (?,?,'Pending','SOLD',now())",post,seller);
                    jdbc.update("INSERT INTO orders(id,post_id,seller_id,buyer_id,final_price,status,escrow_status,created_at,updated_at) VALUES (?,?,?,?,1000000,'PROCESSING','HELD',now(),now())",pending,post,seller,buyer);
                    jdbc.update("""
                        INSERT INTO user_reports(id,reporter_id,request_id,seller_id,post_id,order_id,reason_code,description,evidence_urls,status,
                            reviewer_id,responsible_party,decision_reason,created_at,updated_at,resolved_at)
                        VALUES (?,?,?,?,?,?,'OTHER','Confirmed issue','[]','CONFIRMED',?,'SELLER','Seller at fault',now(),now(),now())
                        """,report,buyer,UUID.randomUUID(),seller,post,pending,reviewer);
                    jdbc.update("INSERT INTO reputation_order_outcomes(order_id,seller_id,outcome,source_report_id,updated_at) VALUES (?,?,'BAD',?,now())",pending,seller,report);
                    jdbc.update("UPDATE seller_reputations SET bad_transactions=1 WHERE seller_id=?",seller);
                    var v2=flyway(url,"43"); assertEquals(3,v2.migrate().migrationsExecuted);
                    assertEquals(0L,jdbc.queryForObject("SELECT bad_transactions FROM seller_reputations WHERE seller_id=?",Long.class,seller));
                    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reputation_order_faults WHERE order_id=?",Integer.class,pending));
                    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM reputation_order_outcomes WHERE order_id=?",Integer.class,pending));
                    assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM seller_reputation_events WHERE event_type='HISTORICAL_ORDER_IMPORTED' AND formula_version='SMOOTHED_SUCCESS_RATE_V1'",Integer.class));
                    assertEquals(new java.math.BigDecimal("100.00"),jdbc.queryForObject("SELECT score_after FROM seller_reputation_events WHERE seller_id=? AND formula_version='TRANSACTION_SUCCESS_RATE_V2'",java.math.BigDecimal.class,seller));
                    assertEquals(balanceBefore,jdbc.queryForMap("SELECT held_balance,shipping_balance,revenue_balance FROM system_wallets"));
                    assertEquals(0,v2.migrate().migrationsExecuted); assertTrue(v2.validateWithResult().validationSuccessful);

                }
            } finally { admin.execute("DROP DATABASE "+name); }
        }
    }
    private Flyway flyway(String url,String target) {
        return Flyway.configure().dataSource(url,database.getUsername(),database.getPassword()).target(target).load();
    }
    private UUID user(JdbcTemplate jdbc) {
        UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')",id,id+"@example.test"); return id;
    }
    private void sale(JdbcTemplate jdbc,UUID seller,UUID buyer,String status,String escrow) {
        UUID post=UUID.randomUUID(); jdbc.update("INSERT INTO posts(id,user_id,title,status,created_at) VALUES (?,?,'Product','SOLD',now())",post,seller);
        jdbc.update("INSERT INTO orders(id,post_id,seller_id,buyer_id,final_price,status,escrow_status,created_at,updated_at) VALUES (?,?,?,?,1000000,?,?,now(),now())",UUID.randomUUID(),post,seller,buyer,status,escrow);
    }
}
