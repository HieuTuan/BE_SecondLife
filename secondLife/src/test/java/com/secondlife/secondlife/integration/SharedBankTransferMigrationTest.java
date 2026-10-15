package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("databaseAvailable")
class SharedBankTransferMigrationTest {
    private static final String ENV="SHARED_MIGRATION_HISTORY_TEST_JDBC_URL";
    private static final GhnTestDatabase database=new GhnTestDatabase(ENV);
    static boolean databaseAvailable() { return GhnTestDatabase.available(ENV); }
    @AfterAll static void close() { database.close(); }
    private String isolatedDatabase() {
        String name="secondlife_ghn_test_bank_history_"+UUID.randomUUID().toString().replace("-","");
        jdbc(database.getJdbcUrl()).execute("CREATE DATABASE "+name);
        return database.getJdbcUrl().substring(0,database.getJdbcUrl().lastIndexOf('/')+1)+name;
    }
    private JdbcTemplate jdbc(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url,database.getUsername(),database.getPassword()));
    }
    private org.flywaydb.core.api.configuration.FluentConfiguration configuration(String url) {
        return Flyway.configure().dataSource(url,database.getUsername(),database.getPassword());
    }
    @Test void freshDatabaseInstallsOriginalDeviceMigrationAndSeparateCheckoutMigration() {
        String url=isolatedDatabase();var flyway=configuration(url).load();flyway.migrate();
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals("49.1",flyway.info().current().getVersion().toString());
        var jdbc=jdbc(url);
        assertEquals(-193514176,jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='49'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='orders' AND column_name='payment_method'",Integer.class));
    }
    @Test void sharedV52HistoryUpgradesWithoutRepairOrLosingExistingDeposit() {
        String url=isolatedDatabase();configuration(url).target("49").load().migrate();var jdbc=jdbc(url);
        // The shared deployment already has these later dev migrations. Preserve their metadata verbatim.
        String[] versions={"50","51","52"};
        String[] descriptions={"add video proof cleanup lifecycle","create wallet withdrawals","seal logs and geocoding cache"};
        String[] scripts={"V50__add_video_proof_cleanup_lifecycle.sql","V51__create_wallet_withdrawals.sql","V52__seal_logs_and_geocoding_cache.sql"};
        int[] checksums={-19940578,1459338785,434418008};
        for(int i=0;i<3;i++) jdbc.update("""
            INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success)
            SELECT max(installed_rank)+1,?,?,'SQL',?,?,'shared-dev',0,true FROM flyway_schema_history
            """,versions[i],descriptions[i],scripts[i],checksums[i]);
        jdbc.execute("""
            CREATE TABLE deposit_requests(id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),
                amount NUMERIC(18,2) NOT NULL,code VARCHAR(255) UNIQUE NOT NULL,status VARCHAR(255) NOT NULL,
                sepay_transaction_id BIGINT,created_at TIMESTAMPTZ NOT NULL)
            """);
        UUID user=UUID.randomUUID(),deposit=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')",user,user+"@example.test");
        jdbc.update("INSERT INTO deposit_requests(id,user_id,amount,code,status,sepay_transaction_id,created_at) VALUES (?,?,25000,'SCL737373','COMPLETED',737373,now())",deposit,user);
        assertFalse(configuration(url).load().validateWithResult().validationSuccessful);
        assertEquals(1,configuration(url).outOfOrder(true).target("49.1").load().migrate().migrationsExecuted);
        assertTrue(configuration(url).load().validateWithResult().validationSuccessful);
        assertEquals(-193514176,jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='49'",Integer.class));
        for(int i=0;i<3;i++) assertEquals(checksums[i],jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version=?",Integer.class,versions[i]));
        assertEquals(new BigDecimal("25000.00"),jdbc.queryForObject("SELECT amount FROM deposit_requests WHERE id=?",BigDecimal.class,deposit));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM sepay_transfer_receipts WHERE transaction_id=737373",Integer.class));
        assertEquals(0,configuration(url).load().migrate().migrationsExecuted);
    }
}
