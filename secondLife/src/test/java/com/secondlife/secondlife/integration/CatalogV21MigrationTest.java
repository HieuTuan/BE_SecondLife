package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.service.CatalogSeedService;
import com.secondlife.secondlife.service.CatalogWriteLock;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class CatalogV21MigrationTest {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test void upgradeKeepsLegacyReferencesAndAdoptsCategoryAndItemIds() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("20").load().migrate();
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(source);
        UUID category = UUID.randomUUID(), item = UUID.randomUUID(), orphan = UUID.randomUUID();
        jdbc.update("INSERT INTO categories(id, name, description) VALUES(?, ?, ?)", category, "living room", "Existing description");
        jdbc.update("INSERT INTO items(id, category_id, name) VALUES(?, ?, ?)", item, category, "sofa");
        jdbc.update("INSERT INTO category_question_templates(id, category_id, item_id, template_text) VALUES(?, ?, ?, ?)",
                UUID.randomUUID(), category, item, "Existing template");
        jdbc.update("INSERT INTO category_question_templates(id, category_id, template_text) VALUES(?, ?, ?)",
                UUID.randomUUID(), orphan, "Legacy orphan kept during upgrade");

        var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).target("21").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM category_question_templates", Integer.class));

        var seeder = new CatalogSeedService(jdbc, new CatalogWriteLock(jdbc));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> seeder.seedDefaults());
        transaction.executeWithoutResult(status -> seeder.seedDefaults());
        assertEquals(category, jdbc.queryForObject("SELECT id FROM categories WHERE seed_key = 'living-room'", UUID.class));
        assertEquals(item, jdbc.queryForObject("SELECT id FROM items WHERE seed_key = 'living-room/sofa'", UUID.class));
        assertEquals("Existing description", jdbc.queryForObject("SELECT description FROM categories WHERE id = ?", String.class, category));
        assertEquals("living room", jdbc.queryForObject("SELECT name FROM categories WHERE id = ?", String.class, category));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class));
        assertEquals(36, jdbc.queryForObject("SELECT COUNT(*) FROM items", Integer.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM items WHERE id = ?", item));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO category_question_templates(id, category_id, template_text) VALUES(?, ?, ?)",
                UUID.randomUUID(), orphan, "New orphan must be rejected"));
    }
}
