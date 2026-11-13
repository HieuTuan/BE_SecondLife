package com.secondlife.secondlife.config;

import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.pattern.ValidatePattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.flyway.allow-legacy-v77", havingValue = "true")
public class LegacyFlywayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LegacyFlywayConfiguration.class);
    private static final MigrationVersion LEGACY_VERSION = MigrationVersion.fromVersion("77");
    private static final String LEGACY_SCRIPT = "V77__cleanup_redundant_tables_and_add_missing_fks.sql";
    private static final Integer LEGACY_CHECKSUM = 1851285675;

    @Bean
    FlywayMigrationStrategy legacyV77MigrationStrategy() {
        return this::migrate;
    }

    private void migrate(Flyway flyway) {
        List<MigrationInfo> missing = List.of(flyway.info().all()).stream()
                .filter(migration -> migration.getState() == MigrationState.MISSING_SUCCESS
                        || migration.getState() == MigrationState.MISSING_FAILED)
                .toList();
        if (missing.size() != 1 || !isKnownLegacyMigration(missing.getFirst())) {
            flyway.migrate();
            return;
        }

        var configuration = flyway.getConfiguration();
        var patterns = new ArrayList<>(List.of(configuration.getIgnoreMigrationPatterns()));
        patterns.add(ValidatePattern.fromPattern("versioned:missing"));
        log.warn("Allowing the known applied V77 migration whose SQL source is unavailable: {}", LEGACY_SCRIPT);
        Flyway.configure(configuration.getClassLoader())
                .configuration(configuration)
                .ignoreMigrationPatterns(patterns.toArray(ValidatePattern[]::new))
                .load()
                .migrate();
    }

    private boolean isKnownLegacyMigration(MigrationInfo migration) {
        return migration.getState() == MigrationState.MISSING_SUCCESS
                && LEGACY_VERSION.equals(migration.getVersion())
                && LEGACY_SCRIPT.equals(migration.getScript())
                && LEGACY_CHECKSUM.equals(migration.getAppliedChecksum());
    }
}
