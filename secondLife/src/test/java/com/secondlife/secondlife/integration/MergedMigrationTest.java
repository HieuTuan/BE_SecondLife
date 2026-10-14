package com.secondlife.secondlife.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MergedMigrationTest {
    private static final String DATABASE_ENV = "GHN_FLOW_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }
    @AfterAll static void closeDatabase() { database.close(); }

    @Test void runtimeMigrationVersionsAreUniqueAfterBranchMerge() throws Exception {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*__*.sql");
        assertTrue(resources.length > 0, "Runtime migration resources must be present");
        Map<String, List<String>> versions = new LinkedHashMap<>();
        for (var resource : resources) {
            String filename = resource.getFilename();
            String version = filename.substring(1, filename.indexOf("__"));
            versions.computeIfAbsent(version, key -> new ArrayList<>()).add(filename);
        }
        var duplicates = versions.entrySet().stream().filter(entry -> entry.getValue().size() > 1).toList();
        assertTrue(duplicates.isEmpty(), () -> "Duplicate runtime migration versions: " + duplicates);
    }

    @Test @EnabledIf("databaseAvailable")
    void upgradesExistingSchemaWithoutChangingAppliedMigrationsAndCanRunAgain() throws Exception {
        String original = database.getJdbcUrl();
        String name = "secondlife_ghn_test_merge_" + UUID.randomUUID().toString().replace("-", "");
        String url = original.substring(0, original.lastIndexOf('/') + 1) + name;
        try (var adminConnection = DriverManager.getConnection(original, database.getUsername(), database.getPassword())) {
            var admin = new JdbcTemplate(new SingleConnectionDataSource(adminConnection, true));
            admin.execute("CREATE DATABASE " + name);
            try {
                flyway(url, "39").migrate();
                try (var connection = DriverManager.getConnection(url, database.getUsername(), database.getPassword())) {
                    var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                    var applied = jdbc.queryForList("SELECT version,script,checksum FROM flyway_schema_history ORDER BY installed_rank");
                    var upgrade = flyway(url, "46");
                    assertEquals(7, upgrade.migrate().migrationsExecuted);
                    assertEquals(applied, jdbc.queryForList("SELECT version,script,checksum FROM flyway_schema_history WHERE version::int <= 39 ORDER BY installed_rank"));
                    for (String table : List.of("seller_reputations", "user_reports", "report_evidence_uploads", "inspection_travel_tiers", "inspection_checklist_templates", "notifications")) {
                        assertEquals(table, jdbc.queryForObject("SELECT to_regclass(?)::text", String.class, table));
                    }
                    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='manager_id'", Integer.class));
                    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='inspection_orders' AND column_name='condition_score'", Integer.class));
                    assertEquals(0, upgrade.migrate().migrationsExecuted);
                    assertTrue(upgrade.validateWithResult().validationSuccessful);
                }
            } finally { admin.execute("DROP DATABASE " + name); }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @EnabledIf("databaseAvailable")
    void seedsHibernateCreatedTravelTiersWithoutDatabaseDefaults(boolean hasExistingTier) throws Exception {
        String original = database.getJdbcUrl();
        String name = "secondlife_ghn_test_existing_tiers_" + UUID.randomUUID().toString().replace("-", "");
        String url = original.substring(0, original.lastIndexOf('/') + 1) + name;
        try (var adminConnection = DriverManager.getConnection(original, database.getUsername(), database.getPassword())) {
            var admin = new JdbcTemplate(new SingleConnectionDataSource(adminConnection, true));
            admin.execute("CREATE DATABASE " + name);
            try {
                flyway(url, "43").migrate();
                try (var connection = DriverManager.getConnection(url, database.getUsername(), database.getPassword())) {
                    var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                    // Hibernate generates UUID/timestamps in Java; its table has no SQL defaults.
                    jdbc.execute("""
                        CREATE TABLE inspection_travel_tiers (
                            id UUID PRIMARY KEY, tier_number INT NOT NULL UNIQUE,
                            name VARCHAR(100) NOT NULL, from_km NUMERIC(8,2) NOT NULL,
                            to_km NUMERIC(8,2), price_per_km NUMERIC(18,2) NOT NULL,
                            status VARCHAR(30) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
                            updated_at TIMESTAMPTZ
                        )
                        """);
                    if (hasExistingTier) {
                        jdbc.update("""
                            INSERT INTO inspection_travel_tiers
                                (id,tier_number,name,from_km,to_km,price_per_km,status,created_at,updated_at)
                            VALUES (?,1,'Existing custom fee',0,5,9000,'INACTIVE',now(),now())
                            """, UUID.randomUUID());
                    }
                    var existing = jdbc.queryForList("SELECT * FROM inspection_travel_tiers WHERE tier_number=1");
                    var upgrade = flyway(url, "46");
                    assertEquals(3, upgrade.migrate().migrationsExecuted);
                    assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM inspection_travel_tiers", Integer.class));
                    assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM inspection_travel_tiers WHERE id IS NOT NULL AND created_at IS NOT NULL AND updated_at IS NOT NULL", Integer.class));
                    if (hasExistingTier) {
                        assertEquals(existing, jdbc.queryForList("SELECT * FROM inspection_travel_tiers WHERE tier_number=1"));
                    }
                    assertEquals(0, upgrade.migrate().migrationsExecuted);
                    assertTrue(upgrade.validateWithResult().validationSuccessful);
                }
            } finally { admin.execute("DROP DATABASE " + name); }
        }
    }

    private Flyway flyway(String url, String target) {
        return Flyway.configure().dataSource(url, database.getUsername(), database.getPassword()).target(target).load();
    }
}
