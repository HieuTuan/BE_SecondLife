package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.UpdateCreditPricingRequest;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.CreditPricingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class CreditPricingConfigurationIntegrationTest {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired CreditPricingService pricing;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;

    @Test
    void adminSeesAllTypesAsUnconfiguredWithoutInventingPricesOrWritingRows() {
        jdbc.update("DELETE FROM credit_pricing_rules");
        var result = pricing.getAdminPrices();
        assertEquals(CreditType.values().length, result.size());
        assertTrue(result.stream().allMatch(rule -> rule.unitPrice() == null && !rule.active()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM credit_pricing_rules", Integer.class));
    }

    @Test
    void missingRuleCanBeConfiguredAndUpdatedWithoutChangingIdentity() {
        UUID adminId = users.saveAndFlush(new User("pricing-" + UUID.randomUUID() + "@test.invalid",
                "test-hash", AccountStatus.ACTIVE)).getId();
        for (CreditType type : CreditType.values()) {
            jdbc.update("DELETE FROM credit_pricing_rules WHERE credit_type = ?", type.name());
            var created = pricing.updatePrice(adminId, type, new UpdateCreditPricingRequest(new BigDecimal("2000")));
            assertEquals(new BigDecimal("2000.00"), created.unitPrice());
            assertEquals("VND", created.currency());
            assertTrue(created.active());
            UUID id = jdbc.queryForObject("SELECT id FROM credit_pricing_rules WHERE credit_type = ?",
                    UUID.class, type.name());
            assertNotNull(id);
            var updated = pricing.updatePrice(adminId, type,
                    new UpdateCreditPricingRequest(new BigDecimal("2500.50")));
            assertEquals(new BigDecimal("2500.50"), updated.unitPrice());
            assertEquals(id, jdbc.queryForObject("SELECT id FROM credit_pricing_rules WHERE credit_type = ?",
                    UUID.class, type.name()));
            assertEquals(adminId, jdbc.queryForObject("SELECT updated_by FROM credit_pricing_rules WHERE credit_type = ?",
                    UUID.class, type.name()));
        }
    }

    @Test
    void concurrentFirstConfigurationCreatesOneRule() throws Exception {
        jdbc.update("DELETE FROM credit_pricing_rules WHERE credit_type = 'LISTING'");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return pricing.updatePrice(null, CreditType.LISTING,
                    new UpdateCreditPricingRequest(new BigDecimal("20000"))); });
            var second = executor.submit(() -> { start.await(); return pricing.updatePrice(null, CreditType.LISTING,
                    new UpdateCreditPricingRequest(new BigDecimal("25000"))); });
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM credit_pricing_rules WHERE credit_type = 'LISTING'",
                Integer.class));
    }

    @Test
    void invalidPriceDoesNotCreateConfiguration() {
        jdbc.update("DELETE FROM credit_pricing_rules WHERE credit_type = 'AI_CHAT'");
        for (BigDecimal value : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("0.001")}) {
            assertThrows(BadRequestException.class, () -> pricing.updatePrice(null, CreditType.AI_CHAT,
                    new UpdateCreditPricingRequest(value)));
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM credit_pricing_rules WHERE credit_type = 'AI_CHAT'",
                Integer.class));
    }
}
