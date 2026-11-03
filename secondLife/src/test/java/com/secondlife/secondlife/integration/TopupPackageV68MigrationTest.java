package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class TopupPackageV68MigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void upgradePreservesOldPackagesWalletTransactionsAndCreditsWithoutInventingReceipts() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("65").load().migrate();
        UUID user = UUID.randomUUID(), wallet = UUID.randomUUID(), pkg = UUID.randomUUID(), tx = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')", user, user + "@test.invalid");
        jdbc.update("""
                INSERT INTO topup_packages(id,name,description,post_credits,chat_credits,valuation_credits,price,discount_percentage)
                VALUES (?,'Legacy Basic','Original description',2,10,2,59000,12.5)
                """, pkg);
        jdbc.update("""
                INSERT INTO user_wallets(id,user_id,balance,created_at,updated_at)
                VALUES (?,?,41000,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, wallet, user);
        jdbc.update("""
                INSERT INTO wallet_transactions(id,wallet_id,amount,type,reference_id,reference_type,balance_before,balance_after,created_at)
                VALUES (?,?,-59000,'PAYMENT',?,'TOPUP_PACKAGE',100000,41000,CURRENT_TIMESTAMP)
                """, tx, wallet, pkg);
        jdbc.update("""
                INSERT INTO credit_balances(id,user_id,credit_type,quantity,version,updated_at)
                VALUES (?,?,'LISTING',2,0,CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), user);
        jdbc.update("""
                INSERT INTO credit_ledger(id,user_id,credit_type,entry_type,quantity_delta,balance_after,idempotency_key,created_at)
                VALUES (?,?,'LISTING','GRANT',2,2,'old-topup-grant',CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), user);
        var before = jdbc.queryForMap("""
                SELECT name,description,post_credits,chat_credits,valuation_credits,price,discount_percentage
                FROM topup_packages WHERE id = ?
                """, pkg);
        var upgrade = Flyway.configure().dataSource(source).target("68").load();
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        assertEquals(before, jdbc.queryForMap("""
                SELECT name,description,post_credits,chat_credits,valuation_credits,price,discount_percentage
                FROM topup_packages WHERE id = ?
                """, pkg));
        assertTrue(jdbc.queryForObject("SELECT active FROM topup_packages WHERE id = ?", Boolean.class, pkg));
        assertEquals(0L, jdbc.queryForObject("SELECT version FROM topup_packages WHERE id = ?", Long.class, pkg));
        assertEquals(new BigDecimal("41000.00"),
                jdbc.queryForObject("SELECT balance FROM user_wallets WHERE id = ?", BigDecimal.class, wallet));
        assertEquals(new BigDecimal("-59000.00"),
                jdbc.queryForObject("SELECT amount FROM wallet_transactions WHERE id = ?", BigDecimal.class, tx));
        assertEquals(2L, jdbc.queryForObject("SELECT quantity FROM credit_balances WHERE user_id = ?", Long.class, user));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE user_id = ?", Integer.class, user));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM topup_package_purchases", Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint WHERE conrelid = 'topup_package_purchases'::regclass
                AND conname = 'uq_topup_purchase_request' AND contype = 'u'
                """, Integer.class));
        assertTrue(upgrade.validateWithResult().validationSuccessful);
        assertEquals(0, upgrade.migrate().migrationsExecuted);
    }
}
