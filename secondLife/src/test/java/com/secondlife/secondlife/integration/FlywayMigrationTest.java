package com.secondlife.secondlife.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
class FlywayMigrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void migrationsCreateIdentityAndVerificationTables() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var tables = statement.executeQuery("""
                     SELECT to_regclass('public.users') IS NOT NULL
                        AND to_regclass('public.seller_verifications') IS NOT NULL
                        AND to_regclass('public.seller_verification_events') IS NOT NULL
                        AND to_regclass('public.permission_assignable_roles') IS NOT NULL
                        AND to_regclass('public.flyway_schema_history') IS NOT NULL
                     """)) {
            assertTrue(tables.next() && tables.getBoolean(1));
        }
    }

    @Test
    void appServesHealthAndOpenApiAfterFlywayMigration() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/permissions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/admin/permissions/{permissionCode}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/permissions/{permissionCode}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/admin/permissions/{permissionCode}'].delete").exists());
    }
}
