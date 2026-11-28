package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.UpdateCreditPricingRequest;
import com.secondlife.secondlife.dto.response.CreditQuoteResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.CreditPricingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class Day05CreditIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private CreditPricingService pricingService;

    @Test
    void sellerReadsSeparateBalancesAndAdminControlsPrices() throws Exception {
        String seller = tokenForRole("SELLER");
        String admin = tokenForRole("ADMIN");
        String staff = tokenForRole("STAFF");
        String buyer = tokenForRole("BUYER");

        mockMvc.perform(get("/api/v1/seller/credits"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/seller/credits").header("Authorization", bearer(buyer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/seller/credits").header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listing").value(0))
                .andExpect(jsonPath("$.data.valuation").value(0));
        mockMvc.perform(get("/api/v1/seller/credit-pricing").header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prices.length()").value(2))
                .andExpect(jsonPath("$.data.discountTiers").doesNotExist());
        mockMvc.perform(put("/api/v1/admin/credit-pricing/LISTING")
                        .header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":\"100.00\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/credit-pricing/LISTING")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":\"-1\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/credit-pricing/LISTING")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":\"100.00\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/credit-pricing/VALUATION")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":\"25.00\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/seller/credit-pricing?listingQuantity=2&valuationQuantity=3")
                        .header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quote.subtotal").value(275.00))
                .andExpect(jsonPath("$.data.quote.discountRate").doesNotExist())
                .andExpect(jsonPath("$.data.quote.discountAmount").doesNotExist())
                .andExpect(jsonPath("$.data.quote.finalFee").value(275.00));
        mockMvc.perform(get("/api/v1/admin/credit-discount-tiers").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/credit-discount-tiers")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minQuantity\":4,\"maxQuantity\":6,\"discountRate\":0.2,\"active\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/admin/credit-discount-tiers/" + UUID.randomUUID())
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minQuantity\":60,\"maxQuantity\":80,\"discountRate\":0.2,\"active\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/topup/purchase/" + UUID.randomUUID())
                        .header("Authorization", bearer(seller)))
                .andExpect(status().isConflict());
    }

    @Test
    void bulkQuantitiesUseFullUnitPriceAtEveryFormerDiscountBoundary() {
        UUID adminId = null;
        pricingService.updatePrice(adminId, CreditType.LISTING,
                new UpdateCreditPricingRequest(new BigDecimal("100.00")));
        pricingService.updatePrice(adminId, CreditType.VALUATION,
                new UpdateCreditPricingRequest(new BigDecimal("25.00")));
        int[] quantities = {1, 4, 5, 9, 10, 29, 30, 49, 50};
        for (int i = 0; i < quantities.length; i++) {
            CreditQuoteResponse quote = pricingService.quote(quantities[i], 0, 0);
            assertEquals(0, quote.subtotal().compareTo(new BigDecimal(quantities[i] * 100 + ".00")));
            assertEquals(0, quote.finalFee().compareTo(quote.subtotal()));
            assertEquals("VND", quote.currency());
        }
    }

    private String tokenForRole(String code) {
        Role role = roleRepository.findByCodeWithPermissions(code).orElseThrow();
        User user = new User("day05-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("Password@123"), AccountStatus.ACTIVE);
        user.addRole(role);
        return jwtTokenProvider.generateAccessToken(userRepository.saveAndFlush(user));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
