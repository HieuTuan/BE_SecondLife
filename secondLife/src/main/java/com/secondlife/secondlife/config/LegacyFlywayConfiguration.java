package com.secondlife.secondlife.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.pattern.ValidatePattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LegacyFlywayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LegacyFlywayConfiguration.class);
    private static final AppliedScript LEGACY_V77 = new AppliedScript(
            "V77__cleanup_redundant_tables_and_add_missing_fks.sql", 1851285675);
    // These migrations are already applied on the shared database, but their
    // SQL files are absent from this branch. Wallet changes use separate versions.
    private static final Map<String, AppliedScript> KNOWN_APPLIED_SCRIPTS = Map.of(
            "104", new AppliedScript("V104__account_for_topup_package_revenue.sql", -77729515),
            "105", new AppliedScript("V105__set_returning_status_for_posts_awaiting_hub_return.sql", -1984584117));

    @Bean
    FlywayMigrationStrategy legacyMigrationStrategy(
            @Value("${app.flyway.allow-legacy-v77:false}") boolean allowLegacyV77) {
        return flyway -> allowKnownMissingMigrations(flyway, allowLegacyV77).migrate();
    }

    Flyway allowKnownMissingMigrations(Flyway flyway, boolean allowLegacyV77) {
        List<MigrationInfo> missing = List.of(flyway.info().all()).stream()
                .filter(migration -> migration.getState() == MigrationState.MISSING_SUCCESS
                        || migration.getState() == MigrationState.MISSING_FAILED)
                .toList();
        if (missing.isEmpty() || !missing.stream().allMatch(migration -> isKnownApplied(migration, allowLegacyV77))) {
            return flyway;
        }

        var configuration = flyway.getConfiguration();
        var patterns = new ArrayList<>(List.of(configuration.getIgnoreMigrationPatterns()));
        patterns.add(ValidatePattern.fromPattern("versioned:missing"));
        log.warn("Allowing known applied migrations whose SQL files are absent: {}",
                missing.stream().map(MigrationInfo::getScript).toList());
        return Flyway.configure(configuration.getClassLoader())
                .configuration(configuration)
                .ignoreMigrationPatterns(patterns.toArray(ValidatePattern[]::new))
                .load();
    }

    private boolean isKnownApplied(MigrationInfo migration, boolean allowLegacyV77) {
        if (migration.getState() != MigrationState.MISSING_SUCCESS || migration.getVersion() == null) {
            return false;
        }
        String version = migration.getVersion().getVersion();
        AppliedScript script = allowLegacyV77 && "77".equals(version)
                ? LEGACY_V77 : KNOWN_APPLIED_SCRIPTS.get(version);
        return script != null && script.name().equals(migration.getScript())
                && Objects.equals(script.checksum(), migration.getAppliedChecksum());
    }

    private record AppliedScript(String name, int checksum) {}
}
