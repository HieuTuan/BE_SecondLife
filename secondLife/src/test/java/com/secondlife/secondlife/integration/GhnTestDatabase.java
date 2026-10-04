package com.secondlife.secondlife.integration;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class GhnTestDatabase implements AutoCloseable {
    private final String urlEnvironmentVariable;
    private PostgreSQLContainer container;
    private String jdbcUrl;
    private String username;
    private String password;

    GhnTestDatabase(String urlEnvironmentVariable) {
        this.urlEnvironmentVariable = urlEnvironmentVariable;
    }

    static boolean available(String urlEnvironmentVariable) {
        if (suppliedUrl(urlEnvironmentVariable) != null) return true;
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    String getJdbcUrl() { initialize(); return jdbcUrl; }
    String getUsername() { initialize(); return username; }
    String getPassword() { initialize(); return password; }

    private synchronized void initialize() {
        if (jdbcUrl != null) return;
        String supplied = suppliedUrl(urlEnvironmentVariable);
        if (supplied != null) {
            // Never run these write-heavy fixtures against an arbitrary local or remote database.
            if (!supplied.matches("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1)(?::[0-9]{1,5})?/secondlife_ghn_test_[A-Za-z0-9_]+"))
                throw new IllegalArgumentException("GHN tests require a loopback JDBC URL and a dedicated secondlife_ghn_test_ database without URL parameters");
            username = System.getenv().getOrDefault("GHN_TEST_DB_USERNAME", "postgres");
            password = System.getenv().getOrDefault("GHN_TEST_DB_PASSWORD", "");
            jdbcUrl = supplied;
            return;
        }
        container = new PostgreSQLContainer("postgres:16-alpine");
        container.start();
        username = container.getUsername();
        password = container.getPassword();
        jdbcUrl = container.getJdbcUrl();
    }

    private static String suppliedUrl(String environmentVariable) {
        String value = System.getenv(environmentVariable);
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override public synchronized void close() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }
}
