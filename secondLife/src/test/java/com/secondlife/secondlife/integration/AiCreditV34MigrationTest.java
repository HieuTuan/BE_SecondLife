package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AiCreditV34MigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @ParameterizedTest
    @ValueSource(strings = {"no_topup_table", "missing_columns", "existing_columns", "partial_columns"})
    void upgradesLegacyAndFreshSchemasWithoutChangingExistingCreditValues(String scenario) {
        String database = "v34_" + scenario;
        var admin = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        admin.execute("CREATE DATABASE " + database);
        String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + database);
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
        Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                .target("33").load().migrate();

        boolean existing = scenario.equals("existing_columns");
        boolean partial = scenario.equals("partial_columns");
        boolean topupExists = !scenario.equals("no_topup_table");
        if (topupExists) {
            jdbc.execute("""
                    CREATE TABLE topup_packages (
                        id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL, description VARCHAR(255),
                        post_credits INT NOT NULL, chat_credits INT NOT NULL,
                        price NUMERIC(38,2) NOT NULL, discount_percentage DOUBLE PRECISION)
                    """);
        }
        if (existing || partial) {
            jdbc.execute("ALTER TABLE credit_purchases ADD COLUMN ai_chat_quantity INT NOT NULL DEFAULT 0");
        }
        if (existing) {
            jdbc.execute("ALTER TABLE credit_purchases ADD COLUMN ai_chat_unit_price NUMERIC(19,2) NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE topup_packages ADD COLUMN valuation_credits INT NOT NULL DEFAULT 0");
        }

        UUID user = UUID.randomUUID(), purchase = UUID.randomUUID(), topup = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, password_hash) VALUES (?, ?, 'test-hash')", user, user + "@example.test");
        jdbc.update("""
                INSERT INTO credit_purchases(id, user_id, listing_quantity, valuation_quantity,
                    listing_unit_price, valuation_unit_price, discount_min_quantity,
                    discount_rate, subtotal, discount_amount, final_fee)
                VALUES (?, ?, 1, 0, 10000, 0, 0, 0, 10000, 0, 10000)
                """, purchase, user);
        if (existing || partial) jdbc.update("UPDATE credit_purchases SET ai_chat_quantity = 7 WHERE id = ?", purchase);
        if (existing) jdbc.update("UPDATE credit_purchases SET ai_chat_unit_price = 1500.50 WHERE id = ?", purchase);
        if (topupExists) {
            jdbc.update("INSERT INTO topup_packages(id, name, post_credits, chat_credits, price) VALUES (?, 'Existing package', 2, 10, 69000)", topup);
            if (existing) jdbc.update("UPDATE topup_packages SET valuation_credits = 3 WHERE id = ?", topup);
        }

        var upgrade = Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword()).load();
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        assertEquals(existing || partial ? 7 : 0,
                jdbc.queryForObject("SELECT ai_chat_quantity FROM credit_purchases WHERE id = ?", Integer.class, purchase));
        assertEquals(existing ? new BigDecimal("1500.50") : new BigDecimal("0.00"),
                jdbc.queryForObject("SELECT ai_chat_unit_price FROM credit_purchases WHERE id = ?", BigDecimal.class, purchase));
        assertEquals(new BigDecimal("10000.00"),
                jdbc.queryForObject("SELECT final_fee FROM credit_purchases WHERE id = ?", BigDecimal.class, purchase));
        if (topupExists) {
            assertEquals(existing ? 3 : 0,
                    jdbc.queryForObject("SELECT valuation_credits FROM topup_packages WHERE id = ?", Integer.class, topup));
        } else {
            jdbc.update("INSERT INTO topup_packages(id, name, post_credits, chat_credits, price) VALUES (?, 'New package', 1, 1, 10000)", topup);
            assertEquals(0, jdbc.queryForObject("SELECT valuation_credits FROM topup_packages WHERE id = ?", Integer.class, topup));
        }
        assertTrue(upgrade.validateWithResult().validationSuccessful);
        assertEquals(0, upgrade.migrate().migrationsExecuted);
    }
}
