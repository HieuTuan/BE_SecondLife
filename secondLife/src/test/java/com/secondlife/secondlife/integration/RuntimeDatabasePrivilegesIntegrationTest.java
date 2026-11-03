package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in: only a disposable local PostgreSQL database with an administrative test account. */
@EnabledIfEnvironmentVariable(named = "RUNTIME_DB_TEST_URL", matches = ".+")
class RuntimeDatabasePrivilegesIntegrationTest {
    private final String suffix = UUID.randomUUID().toString().replace("-", "");
    private final String schema = "runtime_test_" + suffix;
    private final String runtimeRole = "runtime_test_" + suffix;
    private Connection admin;
    private String owner;

    @BeforeEach
    void migrateAsOwner() throws Exception {
        URI address = URI.create(url().substring("jdbc:".length()));
        assertTrue("localhost".equals(address.getHost()) || "127.0.0.1".equals(address.getHost())
                || "[::1]".equals(address.getHost()), "Privilege tests require a disposable LOCAL database");
        assertEquals(null, address.getQuery(), "Connection options are not accepted by the local safety check");
        admin = DriverManager.getConnection(url(), username(), password());
        owner = scalar("SELECT current_user");
        Flyway.configure().dataSource(url(), username(), password()).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();
    }

    @AfterEach
    void removeOnlyTestObjects() throws SQLException {
        if (admin != null) {
            try (Connection connection = admin) {
                execute("RESET ROLE");
                execute("DROP SCHEMA IF EXISTS " + identifier(schema) + " CASCADE");
                if ("1".equals(scalar("SELECT count(*) FROM pg_roles WHERE rolname = '" + runtimeRole + "'"))) {
                    execute("DROP OWNED BY " + identifier(runtimeRole));
                    execute("DROP ROLE " + identifier(runtimeRole));
                }
            }
        }
    }

    @Test
    void runtimePreservesAccountAndFinancialWritesButCannotDeleteUsersOrManageSchema() throws Exception {
        provisionSuccessfully();
        provisionSuccessfully(); // Re-provisioning must preserve the same restrictions.
        execute("SET ROLE " + identifier(runtimeRole));
        execute("SET search_path TO " + identifier(schema) + ", pg_catalog");

        String userId = UUID.randomUUID().toString();
        execute("INSERT INTO users(id,email,password_hash) VALUES ('" + userId + "','runtime@example.test','test-hash')");
        execute("UPDATE users SET account_status = 'LOCKED' WHERE id = '" + userId + "'");
        assertEquals("LOCKED", scalar("SELECT account_status FROM users WHERE id = '" + userId + "'"));
        execute("UPDATE users SET account_status = 'DISABLED' WHERE id = '" + userId + "'");
        assertEquals("DISABLED", scalar("SELECT account_status FROM users WHERE id = '" + userId + "'"));
        assertDenied("DELETE FROM users WHERE id = '" + userId + "'"); // No dependent rows/FKs can mask the denial.
        assertDenied("TRUNCATE TABLE users CASCADE");
        assertEquals("1", scalar("SELECT count(*) FROM users WHERE id = '" + userId + "'"));

        String walletId = UUID.randomUUID().toString();
        execute("INSERT INTO user_wallets(id,user_id,balance,created_at,updated_at) VALUES ('" + walletId
                + "','" + userId + "',100,now(),now())");
        execute("UPDATE user_wallets SET balance = balance - 20 WHERE id = '" + walletId + "'");
        execute("INSERT INTO wallet_transactions(id,wallet_id,amount,type,created_at) VALUES (gen_random_uuid(),'"
                + walletId + "',-20,'PAYMENT',now())");
        assertEquals("80.00", scalar("SELECT balance FROM user_wallets WHERE id = '" + walletId + "'"));
        execute("INSERT INTO credit_balances(user_id,credit_type,quantity) VALUES ('" + userId + "','VALUATION',3)");
        execute("UPDATE credit_balances SET quantity = quantity - 1 WHERE user_id = '" + userId + "'");
        execute("INSERT INTO credit_ledger(user_id,credit_type,entry_type,quantity_delta,balance_after,idempotency_key)"
                + " VALUES ('" + userId + "','VALUATION','CONSUME',-1,2,'runtime-test')");
        assertEquals("2", scalar("SELECT quantity FROM credit_balances WHERE user_id = '" + userId + "'"));
        assertEquals("1", scalar("SELECT count(*) FROM credit_ledger WHERE user_id = '" + userId + "'"));
        assertTrue(Long.parseLong(scalar("SELECT nextval('seller_reputation_event_sequence')")) > 0);

        execute("INSERT INTO categories(id,name) VALUES (gen_random_uuid(),'Temporary runtime category')");
        execute("DELETE FROM categories WHERE name = 'Temporary runtime category'");
        assertEquals("0", scalar("SELECT count(*) FROM categories WHERE name = 'Temporary runtime category'"));
        assertDenied("CREATE TABLE " + identifier(schema) + ".runtime_ddl_attempt(id integer)");
        assertDenied("ALTER TABLE users ADD COLUMN runtime_ddl_attempt integer");
        assertDenied("DROP TABLE users CASCADE");
        assertDenied("ALTER TABLE users DISABLE TRIGGER ALL");
        assertDenied("INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,installed_by,success)"
                + " VALUES (9999,'9999','tamper','SQL','tamper.sql',current_user,true)");
        assertDenied("DELETE FROM flyway_schema_history");
        assertDenied("TRUNCATE flyway_schema_history");
        assertDenied("SELECT * FROM flyway_schema_history");
        execute("RESET ROLE");
        assertEquals(owner, scalar("SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid = '"
                + schema + ".users'::regclass"));
    }

    @Test
    void newTablesRemainUnprivilegedUntilReviewedAndExplicitlyGranted() throws Exception {
        provisionSuccessfully();
        execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + identifier(owner) + " IN SCHEMA " + identifier(schema)
                + " GRANT ALL ON TABLES TO " + identifier(runtimeRole));
        provisionSuccessfully();
        execute("CREATE TABLE " + identifier(schema) + ".unreviewed_future_table(id bigint GENERATED ALWAYS AS IDENTITY)");
        execute("SET ROLE " + identifier(runtimeRole));
        assertDenied("SELECT * FROM " + identifier(schema) + ".unreviewed_future_table");
        assertDenied("INSERT INTO " + identifier(schema) + ".unreviewed_future_table DEFAULT VALUES");
        execute("RESET ROLE");
        provisionSuccessfully();
        execute("SET ROLE " + identifier(runtimeRole));
        assertDenied("SELECT * FROM " + identifier(schema) + ".unreviewed_future_table");
        execute("RESET ROLE");
    }

    @Test
    void provisioningRollsBackWhenPublicDeleteMakesRevocationIneffective() throws Exception {
        execute("GRANT DELETE ON " + identifier(schema) + ".users TO PUBLIC");
        assertTrue(provision() != 0);
        assertEquals("0", scalar("SELECT count(*) FROM pg_roles WHERE rolname = '" + runtimeRole + "'"));
    }

    @Test
    void provisioningRejectsRuntimeOwnershipAndRoleMemberships() throws Exception {
        provisionSuccessfully();
        execute("ALTER TABLE " + identifier(schema) + ".categories OWNER TO " + identifier(runtimeRole));
        assertTrue(provision() != 0);
        execute("ALTER TABLE " + identifier(schema) + ".categories OWNER TO " + identifier(owner));
        execute("GRANT " + identifier(owner) + " TO " + identifier(runtimeRole));
        try {
            assertTrue(provision() != 0);
        } finally {
            execute("REVOKE " + identifier(owner) + " FROM " + identifier(runtimeRole));
        }
    }

    private void provisionSuccessfully() throws Exception {
        assertEquals(0, provision(), "Provisioning must succeed for a separate restricted runtime role");
        assertEquals("false", scalar("SELECT rolcanlogin::text FROM pg_roles WHERE rolname = '" + runtimeRole + "'"));
        assertFalse(Boolean.parseBoolean(scalar("SELECT has_schema_privilege('" + runtimeRole + "','" + schema + "','CREATE')")));
    }

    private int provision() throws Exception {
        URI address = URI.create(url().substring("jdbc:".length()));
        ProcessBuilder command = new ProcessBuilder(System.getenv().getOrDefault("RUNTIME_DB_TEST_PSQL", "psql"),
                "-X", "--host=" + address.getHost(), "--port=" + address.getPort(),
                "--dbname=" + address.getPath().substring(1), "--username=" + username(),
                "--set=ON_ERROR_STOP=1", "--set=runtime_role=" + runtimeRole, "--set=app_schema=" + schema,
                "--set=migration_owner=" + owner, "--file=" + Path.of("scripts/database/provision_runtime_role.sql").toAbsolutePath());
        command.environment().put("PGPASSWORD", password());
        command.environment().put("PGCONNECT_TIMEOUT", "5");
        command.redirectErrorStream(true);
        Path output = Files.createTempFile("runtime-role-", ".log");
        try {
            command.redirectOutput(output.toFile());
            Process process = command.start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("Local provisioning exceeded 30 seconds");
            }
            String log = Files.readString(output);
            System.out.println(log);
            return process.exitValue();
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private void assertDenied(String sql) {
        SQLException exception = assertThrows(SQLException.class, () -> execute(sql), sql);
        assertEquals("42501", exception.getSQLState(), "Must fail for insufficient privilege, not a foreign key or syntax error");
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = admin.createStatement()) {
            statement.execute(sql);
        }
    }

    private String scalar(String sql) throws SQLException {
        try (Statement statement = admin.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static String identifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String url() {
        return System.getenv("RUNTIME_DB_TEST_URL");
    }

    private static String username() {
        return System.getenv("RUNTIME_DB_TEST_USERNAME");
    }

    private static String password() {
        return System.getenv().getOrDefault("RUNTIME_DB_TEST_PASSWORD", "");
    }
}
