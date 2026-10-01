package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.credit.UpdateCreditPricingRequest;
import com.secondlife.secondlife.entity.CreditPurchase;
import com.secondlife.secondlife.entity.PaymentIntent;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.CreditPurchaseStatus;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.repository.CreditBalanceGrantRepository;
import com.secondlife.secondlife.repository.CreditBalanceRepository;
import com.secondlife.secondlife.repository.CreditLedgerRepository;
import com.secondlife.secondlife.repository.CreditPurchaseRepository;
import com.secondlife.secondlife.repository.PaymentCallbackEventRepository;
import com.secondlife.secondlife.repository.PaymentIntentRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.CreditPaymentCallbackService;
import com.secondlife.secondlife.service.CreditPricingService;
import com.secondlife.secondlife.service.payment.MockPaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
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
    private static final String SECRET = "day06-test-webhook-secret-at-least-32-characters";

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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private CreditPricingService pricingService;
    @Autowired private CreditPurchaseRepository purchaseRepository;
    @Autowired private PaymentIntentRepository intentRepository;
    @Autowired private CreditBalanceRepository balanceRepository;
    @Autowired private CreditLedgerRepository ledgerRepository;
    @Autowired private PaymentCallbackEventRepository eventRepository;
    @Autowired private CreditPaymentCallbackService callbackService;
    @MockitoSpyBean private MockPaymentProvider mockProvider;
    @MockitoSpyBean private CreditBalanceGrantRepository grantRepository;

    @Test
    void purchaseUsesServerPriceSnapshotAndProtectsSellerData() throws Exception {
        configurePrices();
        TestUser seller = userForRole("SELLER");
        TestUser otherSeller = userForRole("SELLER");
        TestUser buyer = userForRole("BUYER");

        mockMvc.perform(post("/api/seller/credit-purchases")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listingQuantity\":2,\"valuationQuantity\":3}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/seller/credit-purchases")
                        .header("Authorization", bearer(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listingQuantity\":2,\"valuationQuantity\":3}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/seller/credit-purchases")
                        .header("Authorization", bearer(seller.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listingQuantity\":-1,\"valuationQuantity\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/seller/credit-purchases")
                        .header("Authorization", bearer(seller.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listingQuantity\":0,\"valuationQuantity\":0}"))
                .andExpect(status().isBadRequest());

        UUID id = createPurchase(seller, 2, 3);
        CreditPurchase saved = purchaseRepository.findById(id).orElseThrow();
        assertEquals(2, saved.getListingQuantity());
        assertEquals(3, saved.getValuationQuantity());
        assertEquals(0, saved.getFinalFee().compareTo(new BigDecimal("261.25")));
        assertEquals(CreditPurchaseStatus.PAYMENT_PENDING, saved.getStatus());
        assertTrue(balanceRepository.findByUserId(seller.id()).isEmpty());

        pricingService.updatePrice(null, CreditType.LISTING,
                new UpdateCreditPricingRequest(new BigDecimal("200.00")));
        mockMvc.perform(get("/api/seller/credit-purchases")
                        .header("Authorization", bearer(seller.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].snapshot.listingUnitPrice").value(100.00))
                .andExpect(jsonPath("$.data.items[0].snapshot.finalFee").value(261.25));
        mockMvc.perform(get("/api/seller/credit-purchases")
                        .header("Authorization", bearer(otherSeller.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/seller/credit-ledger")
                        .header("Authorization", bearer(seller.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void signedSuccessGrantsOnceDespiteRepeatedEvents() throws Exception {
        configurePrices();
        TestUser seller = userForRole("SELLER");
        UUID purchaseId = createPurchase(seller, 2, 3);
        PaymentIntent intent = intentRepository.findByPurchaseId(purchaseId).orElseThrow();
        String first = callbackJson("event-" + UUID.randomUUID(), intent, "PAID", intent.getAmount());

        sendCallback(first, signature(first), 200);
        sendCallback(first, signature(first), 200);
        String second = callbackJson("event-" + UUID.randomUUID(), intent, "SUCCESS", intent.getAmount());
        sendCallback(second, signature(second), 200);
        assertEquals(CreditPurchaseStatus.PAID, purchaseRepository.findById(purchaseId).orElseThrow().getStatus());
        assertEquals(2, balance(seller.id(), CreditType.LISTING));
        assertEquals(3, balance(seller.id(), CreditType.VALUATION));
        var grants = ledgerRepository.findByUserIdOrderByCreatedAtDesc(seller.id(),
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertEquals(2, grants.size());
        assertTrue(grants.stream().allMatch(entry -> "GRANT".equals(entry.getEntryType())
                && purchaseId.equals(entry.getPurchaseId())));
        assertTrue(grants.stream().anyMatch(entry -> entry.getCreditType() == CreditType.LISTING
                && entry.getQuantityDelta() == 2 && entry.getBalanceAfter() == 2));
        assertTrue(grants.stream().anyMatch(entry -> entry.getCreditType() == CreditType.VALUATION
                && entry.getQuantityDelta() == 3 && entry.getBalanceAfter() == 3));
        mockMvc.perform(get("/api/seller/credit-ledger")
                        .header("Authorization", bearer(seller.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        assertEquals(2, eventRepository.findAll().stream()
                .filter(event -> event.getPaymentIntentId().equals(intent.getId())).count());
    }

    @Test
    void invalidAndCancelledCallbacksDoNotGrant() throws Exception {
        configurePrices();
        TestUser seller = userForRole("SELLER");
        UUID purchaseId = createPurchase(seller, 1, 0);
        PaymentIntent intent = intentRepository.findByPurchaseId(purchaseId).orElseThrow();
        String cancelled = callbackJson("event-" + UUID.randomUUID(), intent, "CANCELLED", intent.getAmount());
        sendCallback(cancelled, "00", 401);
        String wrongAmount = callbackJson("event-" + UUID.randomUUID(), intent, "PAID", new BigDecimal("1.00"));
        sendCallback(wrongAmount, signature(wrongAmount), 400);
        sendCallback(cancelled, signature(cancelled), 200);

        assertEquals(CreditPurchaseStatus.PAYMENT_FAILED,
                purchaseRepository.findById(purchaseId).orElseThrow().getStatus());
        assertTrue(balanceRepository.findByUserId(seller.id()).isEmpty());
        assertTrue(ledgerRepository.findByUserIdOrderByCreatedAtDesc(seller.id(),
                org.springframework.data.domain.Pageable.unpaged()).isEmpty());
        String conflicting = callbackJson(objectMapper.readTree(cancelled).get("eventId").asText(),
                intent, "PAID", intent.getAmount());
        sendCallback(conflicting, signature(conflicting), 409);
    }

    @Test
    void concurrentSuccessCallbacksGrantOnce() throws Exception {
        configurePrices();
        TestUser seller = userForRole("SELLER");
        UUID purchaseId = createPurchase(seller, 1, 1);
        PaymentIntent intent = intentRepository.findByPurchaseId(purchaseId).orElseThrow();
        String first = callbackJson("event-" + UUID.randomUUID(), intent, "PAID", intent.getAmount());
        String second = callbackJson("event-" + UUID.randomUUID(), intent, "PAID", intent.getAmount());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { start.await(); callbackService.processMockCallback(first, signature(first)); return null; });
            var two = executor.submit(() -> { start.await(); callbackService.processMockCallback(second, signature(second)); return null; });
            start.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        }
        assertEquals(1, balance(seller.id(), CreditType.LISTING));
        assertEquals(1, balance(seller.id(), CreditType.VALUATION));
        assertEquals(2, ledgerRepository.findByUserIdOrderByCreatedAtDesc(seller.id(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
    }

    @Test
    void concurrentPurchasesForSameSellerAccumulateOnOneBalanceRow() throws Exception {
        configurePrices();
        TestUser seller = userForRole("SELLER");
        PaymentIntent firstIntent = intentRepository.findByPurchaseId(createPurchase(seller, 1, 0)).orElseThrow();
        PaymentIntent secondIntent = intentRepository.findByPurchaseId(createPurchase(seller, 1, 0)).orElseThrow();
        String first = callbackJson("event-" + UUID.randomUUID(), firstIntent, "PAID", firstIntent.getAmount());
        String second = callbackJson("event-" + UUID.randomUUID(), secondIntent, "PAID", secondIntent.getAmount());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { start.await(); callbackService.processMockCallback(first, signature(first)); return null; });
            var two = executor.submit(() -> { start.await(); callbackService.processMockCallback(second, signature(second)); return null; });
            start.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        }
        assertEquals(2, balance(seller.id(), CreditType.LISTING));
        assertEquals(1, balanceRepository.findByUserId(seller.id()).size());
        assertEquals(2, ledgerRepository.findByUserIdOrderByCreatedAtDesc(seller.id(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
    }

    @Test
    void providerFailureAndGrantFailureRollBackForRetry() throws Exception {
        configurePrices();
        TestUser seller = userForRole("SELLER");
        doThrow(new IllegalStateException("provider unavailable")).when(mockProvider).createIntent(any());
        mockMvc.perform(post("/api/seller/credit-purchases")
                        .header("Authorization", bearer(seller.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingQuantity\":1,\"valuationQuantity\":1}"))
                .andExpect(status().isInternalServerError());
        assertEquals(0, purchaseRepository.findByUserIdOrderByCreatedAtDesc(seller.id(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
        reset(mockProvider);

        UUID purchaseId = createPurchase(seller, 1, 1);
        PaymentIntent intent = intentRepository.findByPurchaseId(purchaseId).orElseThrow();
        String success = callbackJson("event-" + UUID.randomUUID(), intent, "PAID", intent.getAmount());
        doThrow(new IllegalStateException("grant unavailable")).when(grantRepository)
                .grant(eq(seller.id()), eq(CreditType.VALUATION), eq(1));
        sendCallback(success, signature(success), 500);
        assertTrue(balanceRepository.findByUserId(seller.id()).isEmpty());
        assertEquals(CreditPurchaseStatus.PAYMENT_PENDING,
                purchaseRepository.findById(purchaseId).orElseThrow().getStatus());
        assertTrue(eventRepository.findByProviderAndProviderEventId("MOCK",
                objectMapper.readTree(success).get("eventId").asText()).isEmpty());
        reset(grantRepository);
        sendCallback(success, signature(success), 200);
        assertEquals(1, balance(seller.id(), CreditType.LISTING));
        assertEquals(1, balance(seller.id(), CreditType.VALUATION));
    }

    private void configurePrices() {
        pricingService.updatePrice(null, CreditType.LISTING,
                new UpdateCreditPricingRequest(new BigDecimal("100.00")));
        pricingService.updatePrice(null, CreditType.VALUATION,
                new UpdateCreditPricingRequest(new BigDecimal("25.00")));
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
        String response = mockMvc.perform(post("/api/seller/credit-purchases")
                        .header("Authorization", bearer(seller.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingQuantity\":%d,\"valuationQuantity\":%d}".formatted(listing, valuation)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());
    }

    private String callbackJson(String eventId, PaymentIntent intent, String status, BigDecimal amount) {
        return "{\"eventId\":\"%s\",\"providerIntentId\":\"%s\",\"providerStatus\":\"%s\",\"amount\":%s,\"currency\":\"%s\"}"
                .formatted(eventId, intent.getProviderIntentId(), status, amount, intent.getCurrency());
    }

    private String signature(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private void sendCallback(String body, String signature, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/payment-callbacks/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Mock-Signature", signature)
                        .content(body))
                .andExpect(status().is(expectedStatus));
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
