package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("databaseAvailable")
class ListingDescriptionMigrationTest {
    private static final String DATABASE_ENV = "LISTING_DESCRIPTION_MIGRATION_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }
    @AfterAll static void stopOwnContainer() { database.close(); }

    @Test
    void repairsMissingDescriptionAcceptanceColumnWithoutLosingPostsOrChangingExistingChoices() throws Exception {
        var flyway = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .target("26").load();
        flyway.migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
        UUID seller = UUID.randomUUID(), describedPost = UUID.randomUUID(), blankPost = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, password_hash) VALUES (?, 'description-migration@example.test', 'test-hash')", seller);
        jdbc.update("""
                INSERT INTO posts(id, user_id, title, description, status, created_at)
                VALUES (?, ?, 'Existing product', 'Seller description', 'ACTIVE', now()),
                       (?, ?, 'Empty draft', '   ', 'DRAFT', now())
                """, describedPost, seller, blankPost, seller);
        jdbc.execute("ALTER TABLE posts DROP COLUMN description_accepted");

        var upgrade = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()).target("27").load();
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        assertTrue(jdbc.queryForObject("SELECT description_accepted FROM posts WHERE id = ?", Boolean.class, describedPost));
        assertFalse(jdbc.queryForObject("SELECT description_accepted FROM posts WHERE id = ?", Boolean.class, blankPost));
        assertEquals("Seller description", jdbc.queryForObject("SELECT description FROM posts WHERE id = ?", String.class, describedPost));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM posts WHERE id = ?", String.class, describedPost));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
        assertEquals("NO", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'posts' AND column_name = 'description_accepted'
                """, String.class));
        assertEquals(0, upgrade.migrate().migrationsExecuted);

        jdbc.update("UPDATE posts SET description_accepted = false WHERE id = ?", describedPost);
        jdbc.update("UPDATE posts SET description_accepted = true WHERE id = ?", blankPost);
        jdbc.execute(new org.springframework.core.io.ClassPathResource("db/migration/V27__restore_listing_description_acceptance.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(jdbc.queryForObject("SELECT description_accepted FROM posts WHERE id = ?", Boolean.class, describedPost));
        assertTrue(jdbc.queryForObject("SELECT description_accepted FROM posts WHERE id = ?", Boolean.class, blankPost));
        UUID newDraft = UUID.randomUUID();
        jdbc.update("INSERT INTO posts(id, user_id, title, status, created_at) VALUES (?, ?, 'New draft', 'DRAFT', now())", newDraft, seller);
        assertFalse(jdbc.queryForObject("SELECT description_accepted FROM posts WHERE id = ?", Boolean.class, newDraft));
        assertTrue(upgrade.validateWithResult().validationSuccessful);
    }
}
