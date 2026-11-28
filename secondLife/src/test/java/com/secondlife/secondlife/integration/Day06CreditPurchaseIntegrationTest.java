package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.UpdateCreditPricingRequest;
import com.secondlife.secondlife.entity.CreditPurchase;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.CreditPurchaseStatus;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.repository.CreditBalanceRepository;
import com.secondlife.secondlife.repository.CreditLedgerRepository;
import com.secondlife.secondlife.repository.CreditPurchaseRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class Day06CreditPurchaseIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserWalletRepository walletRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private CreditPricingService pricingService;
    @Autowired private CreditPurchaseRepository purchaseRepository;
    @Autowired private CreditLedgerRepository ledgerRepository;
    @Autowired private CreditBalanceRepository balanceRepository;

    @Test
    void purchaseUsesServerPriceSnapshotAndProtectsSellerData() throws Exception {
        configurePrices();
        TestUser seller = userForRole("SELLER");
        TestUser otherSeller = userForRole("SELLER");
        TestUser buyer = userForRole("BUYER");
        fundWallet(seller.id(), new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/v1/seller/credit-purchases")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listingQuantity\":2,\"valuationQuantity\":3,\"aiChatQuantity\":0}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/seller/credit-purchases")
                        .header("Authorization", bearer(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listingQuantity\":2,\"valuationQuantity\":3,\"aiChatQuantity\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/seller/credit-purchases")
                        .header("Authorization", bearer(seller.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listingQuantity\":-1,\"valuationQuantity\":1,\"aiChatQuantity\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/seller/credit-purchases")
                        .header("Authorization", bearer(seller.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listingQuantity\":0,\"valuationQuantity\":0,\"aiChatQuantity\":0}"))
                .andExpect(status().isBadRequest());

        UUID id = createPurchase(seller, 2, 3);
        CreditPurchase saved = purchaseRepository.findById(id).orElseThrow();
        assertEquals(2, saved.getListingQuantity());
        assertEquals(3, saved.getValuationQuantity());
        assertEquals(0, saved.getFinalFee().compareTo(new BigDecimal("275.00")));
        assertEquals(0, saved.getDiscountAmount().signum());
        assertEquals(CreditPurchaseStatus.PAID, saved.getStatus());
        assertEquals(2, balance(seller.id(), CreditType.LISTING));
        assertEquals(3, balance(seller.id(), CreditType.VALUATION));

        pricingService.updatePrice(null, CreditType.LISTING,
                new UpdateCreditPricingRequest(new BigDecimal("200.00")));
        mockMvc.perform(get("/api/v1/seller/credit-purchases")
                        .header("Authorization", bearer(seller.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].snapshot.listingUnitPrice").value(100.00))
                .andExpect(jsonPath("$.data.items[0].snapshot.finalFee").value(275.00))
                .andExpect(jsonPath("$.data.items[0].snapshot.discountTierId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].snapshot.discountRate").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].snapshot.discountAmount").doesNotExist());
        mockMvc.perform(get("/api/v1/seller/credit-purchases")
                        .header("Authorization", bearer(otherSeller.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/seller/credit-ledger")
                        .header("Authorization", bearer(seller.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    private void configurePrices() {
        pricingService.updatePrice(null, CreditType.LISTING,
                new UpdateCreditPricingRequest(new BigDecimal("100.00")));
        pricingService.updatePrice(null, CreditType.VALUATION,
                new UpdateCreditPricingRequest(new BigDecimal("25.00")));
        pricingService.updatePrice(null, CreditType.AI_CHAT,
                new UpdateCreditPricingRequest(new BigDecimal("10.00")));
    }

    private void fundWallet(UUID userId, BigDecimal amount) {
        walletRepository.ensureWalletExists(UUID.randomUUID(), userId);
        UserWallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        wallet.setBalance(amount);
        walletRepository.saveAndFlush(wallet);
    }

    private TestUser userForRole(String roleCode) {
        Role role = roleRepository.findByCodeWithPermissions(roleCode).orElseThrow();
        User user = new User("day06-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("Password@123"), AccountStatus.ACTIVE);
        user.addRole(role);
        user = userRepository.saveAndFlush(user);
        return new TestUser(user.getId(), jwtTokenProvider.generateAccessToken(user));
    }

    private UUID createPurchase(TestUser seller, int listing, int valuation) throws Exception {
        String response = mockMvc.perform(post("/api/v1/seller/credit-purchases")
                        .header("Authorization", bearer(seller.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingQuantity\":%d,\"valuationQuantity\":%d,\"aiChatQuantity\":0}".formatted(listing, valuation)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());
    }

    private long balance(UUID userId, CreditType type) {
        return balanceRepository.findByUserId(userId).stream()
                .filter(row -> row.getCreditType() == type)
                .findFirst().orElseThrow().getQuantity();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(UUID id, String token) {
    }
}
