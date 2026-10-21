package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.integration.ai.AiPriceProvider;
import com.secondlife.secondlife.service.CloudinaryService;
import com.secondlife.secondlife.integration.shipping.LalamoveSignatures;
import com.secondlife.secondlife.integration.shipping.LalamoveWebhookVerifier;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.concurrent.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false",
        "app.shipping.provider=LALAMOVE", "app.shipping.lalamove.enabled=true",
        "app.shipping.lalamove.key=pk_test_integration", "app.shipping.lalamove.secret=sk_test_integration",
        "app.shipping.lalamove.base-url=https://rest.sandbox.lalamove.com/v3",
        "app.shipping.lalamove.market=VN", "app.shipping.lalamove.language=vi_VN",
        "app.shipping.lalamove.timeout-ms=5000",
        "app.shipping.lalamove.max-response-characters=4194304", "app.shipping.lalamove.webhook-max-future-skew-seconds=300"
})
class LalamoveShippingIntegrationTest {
    private static final BigDecimal FUNDS = new BigDecimal("5000000");
    private static final BigDecimal PRICE = new BigDecimal("1000000");
    private static final BigDecimal FEE = new BigDecimal("27000");
    private static final String KEY = "pk_test_integration";
    private static final String SECRET = "sk_test_integration";
    private static final GhnTestDatabase database = new GhnTestDatabase("GHN_FLOW_TEST_JDBC_URL");

    static boolean databaseAvailable() { return GhnTestDatabase.available("GHN_FLOW_TEST_JDBC_URL"); }
    @AfterAll static void closeOwnDatabase() { database.close(); }
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PostRepository posts;
    @Autowired UserWalletRepository wallets;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider jwt;
    @Autowired com.secondlife.secondlife.service.OrderAutoReleaseService autoRelease;
    @Autowired com.secondlife.secondlife.service.OrderService orderService;
    @Autowired com.secondlife.secondlife.repository.OrderRepository orderRepository;
    @MockitoBean(name = "orderReleaseClock") Clock releaseClock;
    @MockitoBean ShippingProvider carrier;
    @MockitoBean AiPriceProvider priceProvider;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoBean(name = "ollamaChatModel") ChatModel ollama;
    @MockitoBean(name = "googleGenAiChatModel") ChatModel google;
    @MockitoBean com.secondlife.secondlife.service.ReverseGeocodingService geocoding;
    @MockitoBean org.springframework.mail.javamail.JavaMailSender mail;
    @MockitoBean org.springframework.messaging.simp.SimpMessagingTemplate messages;

    @BeforeEach void carrierReplies() {
        when(releaseClock.instant()).thenReturn(Instant.now());
        when(geocoding.shipping(any())).thenAnswer(invocation -> {
            com.secondlife.secondlife.dto.request.ShippingAddressRequest request=invocation.getArgument(0);
            return new com.secondlife.secondlife.dto.request.ShippingAddress(request.name(),request.phone(),
                    "123 Nguyễn Huệ, Thành phố Hồ Chí Minh",request.latitude(),request.longitude());
        });
        jdbc.update("UPDATE commission_rules SET active = false WHERE active");
        when(ollama.getOptions()).thenReturn(ChatOptions.builder().build());
        when(google.getOptions()).thenReturn(ChatOptions.builder().build());
        when(carrier.code()).thenReturn("LALAMOVE");
        when(carrier.forProvider(anyString())).thenReturn(carrier);
        when(carrier.preview(anyMap())).thenAnswer(invocation -> preview(FEE));
        when(carrier.create(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(0);
            return json(Map.of("order_code", "LALA-" + payload.get("client_order_code"), "status", "ASSIGNING_DRIVER",
                    "total_fee", FEE, "driver_id", "79973", "share_link", "https://share.sandbox.lalamove.com/test-order"));
        });
    }

    @Test void paidCoordinateQuoteSignedCarrierDeliveryAndBuyerConfirmationSettleCommissionNetExactlyOnce() throws Exception {
        Fixture f = fixture();
        User admin = user("ADMIN", BigDecimal.ZERO);
        JsonNode before = systemWallet(admin);
        JsonNode quote = quote(f);
        assertMoney(PRICE.add(FEE), quote.path("totalPayable").decimalValue());
        UUID quoteId = UUID.fromString(quote.path("quoteId").asText());
        assertEquals("LALAMOVE", jdbc.queryForObject("SELECT provider FROM shipping_quotes WHERE id = ?", String.class, quoteId));
        UUID orderId = checkout(f, quoteId);
        assertMoney(FUNDS.subtract(PRICE).subtract(FEE), balance(f.buyer()));
        assertEquals(1, transactions(orderId, "PAYMENT"));

        JsonNode shipment = create(f.seller(), orderId, UUID.randomUUID());
        UUID shipmentId = UUID.fromString(shipment.path("shipmentId").asText());
        String code = shipment.path("orderCode").asText();
        assertEquals("LALAMOVE", shipment.path("provider").asText());
        assertEquals("ASSIGNING_DRIVER", shipment.path("providerStatus").asText());
        JsonNode savedPayload = mapper.readTree(jdbc.queryForObject("SELECT payload FROM shipments WHERE id = ?", String.class, shipmentId));
        assertEquals(new BigDecimal("10.777"), savedPayload.path("from_latitude").decimalValue());
        assertEquals(new BigDecimal("106.7"), savedPayload.path("to_longitude").decimalValue());

        Instant pickedAt = Instant.now().minusSeconds(15);
        callback(event(code, "PICKED_UP", pickedAt, UUID.randomUUID().toString(), FEE)).andExpect(status().isOk());
        assertOrder(orderId, "SHIPPED", "HELD");
        assertEquals(pickedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS),orderRepository.findById(orderId).orElseThrow().getCarrierPickedUpAt());
        callback(event(code, "COMPLETED", pickedAt.plusSeconds(5), UUID.randomUUID().toString(), FEE)).andExpect(status().isOk());
        assertOrder(orderId, "DELIVERED", "HELD");
        assertEquals(0, transactions(orderId, "EARNING"));
        assertMoney(BigDecimal.ZERO, balance(f.seller()));

        for (int retry = 0; retry < 2; retry++)
            mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                    .andExpect(status().isOk());
        assertOrder(orderId, "COMPLETED", "RELEASED");
        assertMoney(new BigDecimal("950000"), balance(f.seller()));
        assertEquals(1, transactions(orderId, "EARNING"));
        JsonNode settlement = tree(mvc.perform(get("/api/v1/orders/" + orderId + "/settlement")
                .header("Authorization", bearer(f.seller()))).andExpect(status().isOk()).andReturn()).path("data");
        assertMoney(new BigDecimal("50000"), settlement.path("platformCommission").decimalValue());
        assertMoney(new BigDecimal("950000"), settlement.path("sellerPayout").decimalValue());
        assertMoney(FEE, settlement.path("shippingFee").decimalValue());
        JsonNode after = systemWallet(admin);
        assertMoney(before.path("heldBalance").decimalValue(), after.path("heldBalance").decimalValue());
        assertMoney(before.path("shippingBalance").decimalValue().add(FEE), after.path("shippingBalance").decimalValue());
        assertMoney(before.path("revenueBalance").decimalValue().add(new BigDecimal("50000")), after.path("revenueBalance").decimalValue());
        assertEquals(1, systemTransactions(orderId, "SETTLEMENT"));
        assertReputationOnce(f.seller(), orderId);
    }

    @Test void systemWalletHoldsOrderPaymentAndReturnsBothProductAndUnusedShippingOnCancellation() throws Exception {
        Fixture f = fixture();
        User admin = user("ADMIN", BigDecimal.ZERO);
        JsonNode before = systemWallet(admin);
        UUID orderId = checkout(f, quoteId(quote(f)));
        JsonNode held = systemWallet(admin);
        assertMoney(before.path("heldBalance").decimalValue().add(PRICE), held.path("heldBalance").decimalValue());
        assertMoney(before.path("shippingBalance").decimalValue().add(FEE), held.path("shippingBalance").decimalValue());
        assertMoney(before.path("revenueBalance").decimalValue(), held.path("revenueBalance").decimalValue());
        mvc.perform(get("/api/v1/admin/system-wallet").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isForbidden());
        for (int retry = 0; retry < 2; retry++)
            mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", bearer(f.buyer())))
                    .andExpect(status().isOk());
        JsonNode refunded = systemWallet(admin);
        for (String field : List.of("heldBalance", "shippingBalance", "revenueBalance", "totalBalance"))
            assertMoney(before.path(field).decimalValue(), refunded.path(field).decimalValue());
        assertMoney(FUNDS, balance(f.buyer()));
        assertEquals(1, transactions(orderId, "REFUND"));
    }

    private void assertReputationOnce(User seller,UUID orderId) {
        assertEquals(1L,jdbc.queryForObject("SELECT good_transactions FROM seller_reputations WHERE seller_id=?",Long.class,seller.getId()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM seller_reputation_events WHERE order_id=? AND event_type='ORDER_COMPLETED'",Integer.class,orderId));
    }

    @Test void paidOrderSnapshotsDeadlineAndSellerCancellationCountsOnceWhileBuyerCancellationIsExcluded() throws Exception {
        Fixture f=fixture(); UUID id=checkout(f,quoteId(quote(f)));
        var before=orderRepository.findById(id).orElseThrow(); assertNotNull(before.getPaidAt());
        assertEquals(Duration.ofHours(48),Duration.between(before.getPaidAt(),before.getHandoverDeadline()));
        String url="/api/v1/orders/"+id+"/cancel";
        mvc.perform(put(url).header("Authorization",bearer(f.seller()))).andExpect(status().isBadRequest());
        for(int i=0;i<2;i++) mvc.perform(put(url).header("Authorization",bearer(f.seller())).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("reasonCode","SELLER_OUT_OF_STOCK","reason","Item unavailable")))).andExpect(status().isOk());
        assertMoney(FUNDS,balance(f.buyer())); assertEquals(1,transactions(id,"REFUND"));
        mvc.perform(get("/api/v1/sellers/"+f.seller().getId()+"/reputation")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.score").value(0)).andExpect(jsonPath("$.data.evaluatedOrders").value(1))
            .andExpect(jsonPath("$.data.sellerCancellationOrders").value(1));
        jdbc.update("UPDATE commission_rules SET active=false WHERE active");
        Fixture other=fixture(); UUID buyerCancelled=checkout(other,quoteId(quote(other)));
        mvc.perform(put("/api/v1/orders/"+buyerCancelled+"/cancel").header("Authorization",bearer(other.buyer()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/sellers/"+other.seller().getId()+"/reputation")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.score").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.evaluatedOrders").value(0));
    }

    @Test void syncObservationDoesNotInventPickupTimeAndOlderVerifiedPickupStillSuppliesActualTimestamp() throws Exception {
        Fixture f=fixture(); UUID id=checkout(f,quoteId(quote(f)));
        JsonNode shipment=create(f.seller(),id,UUID.randomUUID()); String code=shipment.path("orderCode").asText();
        when(carrier.detail(code)).thenReturn(json(Map.of("order_code",code,"status","PICKED_UP")));
        mvc.perform(post("/api/v1/shipments/"+shipment.path("shipmentId").asText()+"/sync").header("Authorization",bearer(f.buyer())))
            .andExpect(status().isOk());
        assertNull(orderRepository.findById(id).orElseThrow().getCarrierPickedUpAt());
        Instant actual=Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        callback(event(code,"PICKED_UP",actual,UUID.randomUUID().toString(),FEE)).andExpect(status().isOk());
        assertEquals(actual,orderRepository.findById(id).orElseThrow().getCarrierPickedUpAt());
        assertOrder(id,"SHIPPED","HELD");
    }

    private JsonNode systemWallet(User admin) throws Exception {
        return tree(mvc.perform(get("/api/v1/admin/system-wallet").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    @Test void autoSettlementWaitsFor48HoursAfterCarrierDeliveryAndManualReplayDoesNotPayAgain() throws Exception {
        Fixture f = fixture();
        User admin = user("ADMIN", BigDecimal.ZERO);
        JsonNode before = systemWallet(admin);
        UUID id = deliveredOrder(f);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        when(releaseClock.instant()).thenReturn(now);
        deliveredAt(id, now.minus(Duration.ofHours(48)).plusSeconds(1));
        assertFalse(autoRelease.releaseIfDue(id));
        assertOrder(id, "DELIVERED", "HELD");
        assertEquals(0, transactions(id, "EARNING"));
        deliveredAt(id, now.minus(Duration.ofHours(48)));
        var due = orderRepository.findDueSettlementOrders(com.secondlife.secondlife.enums.OrderStatus.DELIVERED,
                com.secondlife.secondlife.enums.EscrowStatus.HELD, autoRelease.dueCutoff(),
                org.springframework.data.domain.PageRequest.of(0, 1000));
        assertTrue(due.stream().anyMatch(row -> row.getId().equals(id)));
        var last = due.stream().filter(row -> row.getId().equals(id)).findFirst().orElseThrow();
        assertTrue(orderRepository.findDueSettlementOrdersAfter(com.secondlife.secondlife.enums.OrderStatus.DELIVERED,
                com.secondlife.secondlife.enums.EscrowStatus.HELD, autoRelease.dueCutoff(), last.getDeliveredAt(), last.getId(),
                org.springframework.data.domain.PageRequest.of(0, 1000)).stream().noneMatch(row -> row.getId().equals(id)));
        assertTrue(autoRelease.releaseIfDue(id));
        assertFalse(autoRelease.releaseIfDue(id));
        mvc.perform(put("/api/v1/orders/" + id + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk());
        assertOrder(id, "COMPLETED", "RELEASED");
        assertMoney(new BigDecimal("950000"), balance(f.seller()));
        assertEquals(1, transactions(id, "EARNING"));
        assertEquals(1, systemTransactions(id, "SETTLEMENT"));
        assertReputationOnce(f.seller(), id);
        JsonNode after = systemWallet(admin);
        assertMoney(before.path("heldBalance").decimalValue(), after.path("heldBalance").decimalValue());
        assertMoney(before.path("revenueBalance").decimalValue().add(new BigDecimal("50000")), after.path("revenueBalance").decimalValue());
        JsonNode history = tree(mvc.perform(get("/api/v1/admin/system-wallet/transactions?page=0&size=1")
                .header("Authorization", bearer(admin))).andExpect(status().isOk()).andReturn()).path("data");
        assertEquals("SETTLEMENT", history.path("items").get(0).path("type").asText());
        assertEquals(id.toString(), history.path("items").get(0).path("orderId").asText());
    }

    @Test void buyerConfirmationAndAutomaticSettlementRacingPaySellerOnlyOnce() throws Exception {
        Fixture f = fixture(); UUID id = deliveredOrder(f);
        deliveredAt(id, Instant.now().minus(Duration.ofHours(49)));
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var automatic = executor.submit(() -> { start.await(); return autoRelease.releaseIfDue(id); });
            var buyer = executor.submit(() -> { start.await(); return orderService.confirmDelivery(f.buyer().getId(), id); });
            start.countDown();
            automatic.get(30, TimeUnit.SECONDS);
            assertEquals(com.secondlife.secondlife.enums.OrderStatus.COMPLETED, buyer.get(30, TimeUnit.SECONDS).status());
            assertOrder(id, "COMPLETED", "RELEASED");
            assertMoney(new BigDecimal("950000"), balance(f.seller()));
            assertEquals(1, transactions(id, "EARNING"));
            assertEquals(1, systemTransactions(id, "SETTLEMENT"));
        assertReputationOnce(f.seller(), id);
        } finally { executor.shutdownNow(); }
    }

    @Test void insufficientBuyerFundsRollBackSystemHoldOrderAndQuoteConsumption() throws Exception {
        Fixture f = fixture(); User admin = user("ADMIN", BigDecimal.ZERO);
        UUID quote = quoteId(quote(f)); JsonNode before = systemWallet(admin);
        jdbc.update("UPDATE user_wallets SET balance = 0 WHERE user_id = ?", f.buyer().getId());
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer(f.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("postId", f.post().getId(),
                        "shippingQuoteId", quote, "requestId", UUID.randomUUID())))).andExpect(status().isBadRequest());
        JsonNode after = systemWallet(admin);
        assertMoney(before.path("totalBalance").decimalValue(), after.path("totalBalance").decimalValue());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE post_id = ?", Integer.class, f.post().getId()));
        assertNull(jdbc.queryForObject("SELECT consumed_order_id FROM shipping_quotes WHERE id = ?", UUID.class, quote));
        assertEquals("ACTIVE", posts.findById(f.post().getId()).orElseThrow().getStatus());
    }

    @Test void failedSellerCreditRollsBackSettlementAndSystemBalancesThenRetrySucceeds() throws Exception {
        Fixture f = fixture(); UUID id = deliveredOrder(f); User admin = user("ADMIN", BigDecimal.ZERO);
        JsonNode before = systemWallet(admin);
        jdbc.update("UPDATE user_wallets SET balance = 9999999999999999.99 WHERE user_id = ?", f.seller().getId());
        mvc.perform(put("/api/v1/orders/" + id + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isInternalServerError());
        assertOrder(id, "DELIVERED", "HELD");
        assertEquals(0, transactions(id, "EARNING"));
        assertEquals(0, systemTransactions(id, "SETTLEMENT"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_settlements WHERE order_id = ?", Integer.class, id));
        assertMoney(before.path("totalBalance").decimalValue(), systemWallet(admin).path("totalBalance").decimalValue());
        jdbc.update("UPDATE user_wallets SET balance = 0 WHERE user_id = ?", f.seller().getId());
        mvc.perform(put("/api/v1/orders/" + id + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk());
        assertMoney(new BigDecimal("950000"), balance(f.seller()));
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"SELLER","BUYER","CARRIER"})
    void approvedReturnFreezesEscrowAndRefundsProductWithoutPayingSellerOrCommission(String responsibleParty) throws Exception {
        Fixture f = fixture(); User admin = user("ADMIN", BigDecimal.ZERO);
        JsonNode before = systemWallet(admin); UUID id = deliveredOrder(f);
        JsonNode report = tree(mvc.perform(post("/api/v1/reports").header("Authorization", bearer(f.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("requestId", UUID.randomUUID(), "orderId", id,
                        "reasonCode", "MISLEADING_DESCRIPTION", "description", "Delivered product differs from its listing", "evidenceUrls", List.of()))))
                .andExpect(status().isCreated()).andReturn()).path("data");
        UUID reportId = UUID.fromString(report.path("id").asText());
        mvc.perform(post("/api/v1/staff/reports/" + reportId + "/start-review").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/staff/reports/" + reportId + "/resolve").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("decision", "CONFIRMED", "responsibleParty", responsibleParty,
                        "reason", "Staff approved the product return", "resolutionAction", "RETURN_AND_REFUND"))))
                .andExpect(status().isOk());
        JsonNode returned = tree(mvc.perform(post("/api/v1/orders/" + id + "/shipments")
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("requestId", UUID.randomUUID(), "leg", "BUYER_TO_SELLER", "reason", "Approved product return", "reportId", reportId))))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertOrder(id, "DELIVERED", "FROZEN");
        deliveredAt(id, Instant.now().minus(Duration.ofHours(49)));
        assertFalse(autoRelease.releaseIfDue(id));
        mvc.perform(put("/api/v1/orders/" + id + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isConflict());
        callback(event(returned.path("orderCode").asText(), "COMPLETED", Instant.now().minusSeconds(1),
                UUID.randomUUID().toString(), FEE)).andExpect(status().isOk());
        for (int retry = 0; retry < 2; retry++)
            mvc.perform(post("/api/v1/shipments/" + returned.path("shipmentId").asText() + "/refund")
                    .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("reason", "Product physically returned","responsibleParty",responsibleParty)))).andExpect(status().isOk());
        assertOrder(id, "CANCELLED", "REFUNDED");
        assertMoney(FUNDS.subtract(FEE), balance(f.buyer())); assertMoney(BigDecimal.ZERO, balance(f.seller()));
        assertEquals(0, transactions(id, "EARNING")); assertEquals(1, transactions(id, "REFUND"));
        assertEquals(1, systemTransactions(id, "REFUND"));
        mvc.perform(get("/api/v1/sellers/"+f.seller().getId()+"/reputation")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.evaluatedOrders").value("SELLER".equals(responsibleParty)?1:0))
            .andExpect(jsonPath("$.data.sellerReturnFaultOrders").value("SELLER".equals(responsibleParty)?1:0));
        JsonNode after = systemWallet(admin);
        assertMoney(before.path("heldBalance").decimalValue(), after.path("heldBalance").decimalValue());
        assertMoney(before.path("shippingBalance").decimalValue().add(FEE), after.path("shippingBalance").decimalValue());
        assertMoney(before.path("revenueBalance").decimalValue(), after.path("revenueBalance").decimalValue());
    }

    private UUID deliveredOrder(Fixture f) throws Exception {
        UUID id = checkout(f, quoteId(quote(f))); JsonNode shipment = create(f.seller(), id, UUID.randomUUID());
        callback(event(shipment.path("orderCode").asText(), "COMPLETED", Instant.now().minusSeconds(1),
                UUID.randomUUID().toString(), FEE)).andExpect(status().isOk());
        assertOrder(id, "DELIVERED", "HELD");
        return id;
    }

    private void deliveredAt(UUID id, Instant at) {
        jdbc.update("UPDATE orders SET shipping_delivered_at = ? WHERE id = ?", Timestamp.from(at), id);
    }

    private int systemTransactions(UUID id, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM system_wallet_transactions WHERE order_id = ? AND type = ?", Integer.class, id, type);
    }

    @Test void forgedWebhookCannotChangeOrderAndSignedHistoryNeverIncludesCredentialOrSignature() throws Exception {
        Fixture f = fixture();
        UUID orderId = checkout(f, quoteId(quote(f)));
        JsonNode shipment = create(f.seller(), orderId, UUID.randomUUID());
        UUID shipmentId = UUID.fromString(shipment.path("shipmentId").asText());
        ObjectNode signed = event(shipment.path("orderCode").asText(), "PICKED_UP", Instant.now().minusSeconds(3), UUID.randomUUID().toString(), FEE);
        ObjectNode tampered = signed.deepCopy();
        ((ObjectNode) tampered.path("data").path("order")).put("status", "COMPLETED");
        callback(tampered).andExpect(status().isForbidden());
        assertOrder(orderId, "CONFIRMED", "HELD");
        assertEquals(0, eventCount(shipmentId));
        mvc.perform(post(LalamoveWebhookVerifier.CALLBACK_PATH)).andExpect(status().isOk());
        assertEquals(0, eventCount(shipmentId));
        callback(signed).andExpect(status().isOk());
        String history = mvc.perform(get("/api/v1/shipments/" + shipmentId + "/events")
                .header("Authorization", bearer(f.buyer()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(history.contains(KEY));
        assertFalse(history.contains(SECRET));
        assertFalse(history.contains(signed.path("signature").asText()));
        assertEquals(1, eventCount(shipmentId));
    }

    @Test void driverCoordinatesAreAvailableOnlyToParticipantsAndNeverInventedAfterCompletion() throws Exception {
        Fixture f = fixture();
        UUID orderId = checkout(f, quoteId(quote(f)));
        JsonNode shipment = create(f.seller(), orderId, UUID.randomUUID());
        UUID id = UUID.fromString(shipment.path("shipmentId").asText());
        String code = shipment.path("orderCode").asText();
        when(carrier.driver(code, "79973")).thenReturn(json(Map.of("driverId", "79973", "name", "Sandbox driver",
                "coordinates", Map.of("lat", "10.781", "lng", "106.702", "updatedAt", Instant.now().toString()))));
        mvc.perform(get("/api/v1/shipments/" + id + "/tracking").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.locationStatus").value("UNAVAILABLE"));
        verify(carrier, never()).driver(anyString(), anyString());
        callback(event(code, "ON_GOING", Instant.now().minusSeconds(5), UUID.randomUUID().toString(), FEE)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shipments/" + id + "/tracking").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.locationStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.driver.coordinates.lat").value("10.781"))
                .andExpect(jsonPath("$.data.shareLink").value("https://share.sandbox.lalamove.com/test-order"));
        when(carrier.driver(code,"79973")).thenReturn(json(Map.of("driverId","79973","name","Sandbox driver",
                "coordinates",Map.of("lat","0.0000000","lng","0.0000000","updatedAt","1970-01-01T00:00:00.00Z"))));
        mvc.perform(get("/api/v1/shipments/"+id+"/tracking").header("Authorization",bearer(f.buyer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.locationStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.driver.coordinates").isEmpty())
                .andExpect(jsonPath("$.data.driver.name").value("Sandbox driver"));
        User stranger = user("BUYER", FUNDS);
        mvc.perform(get("/api/v1/shipments/" + id + "/tracking").header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        verify(carrier, times(2)).driver(code, "79973");
        callback(event(code, "COMPLETED", Instant.now().minusSeconds(1), UUID.randomUUID().toString(), FEE)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shipments/" + id + "/tracking").header("Authorization", bearer(f.seller())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.locationStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.driver").isEmpty());
        verify(carrier, times(2)).driver(code, "79973");
    }

    @Test void quoteChoosesVehicleOnServerAndIgnoresLegacyBuyerOverride() throws Exception {
        Fixture f=fixture();
        var requested=new java.util.concurrent.atomic.AtomicReference<Map<String,Object>>();
        when(carrier.preview(anyMap())).thenAnswer(invocation->{
            requested.set(new java.util.HashMap<>(invocation.<Map<String,Object>>getArgument(0)));return preview(FEE);
        });
        var result=tree(mvc.perform(post("/api/v1/shipping/quotes").header("Authorization",bearer(f.buyer()))
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("postId",f.post().getId(),"serviceType","BUYER_CHOICE",
                "deliveryAddress",address("Buyer","10.781","106.7")))))
            .andExpect(status().isOk()).andReturn()).path("data");
        assertEquals("MOTORCYCLE",result.path("serviceType").asText());
        var id=UUID.fromString(result.path("quoteId").asText());
        var stored=mapper.readTree(jdbc.queryForObject("SELECT payload FROM shipping_quotes WHERE id=?",String.class,id));
        assertEquals("MOTORCYCLE",stored.path("service_type").asText());
        assertFalse(requested.get().containsKey("service_type"));
    }
    @Test void expiredCheckoutQuoteRequiresBuyerToRequestNewPriceWithoutDebit() throws Exception {
        Fixture f=fixture();var id=quoteId(quote(f));
        jdbc.update("UPDATE shipping_quotes SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),id);
        mvc.perform(post("/api/v1/orders").header("Authorization",bearer(f.buyer())).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("postId",f.post().getId(),"shippingQuoteId",id,"requestId",UUID.randomUUID()))))
            .andExpect(status().isConflict());
        assertMoney(FUNDS,balance(f.buyer()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE shipping_quote_id=?",Integer.class,id));
    }
    @Test void sellerCannotChangeParcelAfterBuyerHasPaid() throws Exception {
        Fixture f=fixture();checkout(f,quoteId(quote(f)));
        mvc.perform(put("/api/v1/posts/"+f.post().getId()+"/shipping-package").header("Authorization",bearer(f.seller()))
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("weight",40000,"length",100,"width",80,"height",120))))
            .andExpect(status().isConflict());
    }
    @Test void expiredPaidQuoteWithHigherFreshFeeDoesNotCreateBookingOrDebitBuyerAgain() throws Exception {
        Fixture f = fixture();
        UUID quoteId = quoteId(quote(f));
        UUID orderId = checkout(f, quoteId);
        ObjectNode payload = (ObjectNode) mapper.readTree(jdbc.queryForObject("SELECT payload FROM shipping_quotes WHERE id = ?", String.class, quoteId));
        ((ObjectNode) payload.path("provider_quote")).put("expiresAt", Instant.now().minusSeconds(1).toString());
        jdbc.update("UPDATE shipping_quotes SET payload = ? WHERE id = ?", body(payload), quoteId);
        when(carrier.preview(anyMap())).thenReturn(preview(FEE.add(BigDecimal.ONE)));
        mvc.perform(post("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(f.seller()))
                .contentType(MediaType.APPLICATION_JSON).content(body(request(UUID.randomUUID())))).andExpect(status().isConflict());
        verify(carrier, never()).create(anyMap());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shipments WHERE order_id = ?", Integer.class, orderId));
        assertMoney(FUNDS.subtract(PRICE).subtract(FEE), balance(f.buyer()));
        assertEquals(1, transactions(orderId, "PAYMENT"));
        assertOrder(orderId, "CONFIRMED", "HELD");
    }

    @Test void duplicateAndOutOfOrderSignedEventsCannotRegressDeliveryOrOverwriteNewerFee() throws Exception {
        Fixture f = fixture();
        UUID orderId = checkout(f, quoteId(quote(f)));
        JsonNode shipment = create(f.seller(), orderId, UUID.randomUUID());
        UUID id = UUID.fromString(shipment.path("shipmentId").asText());
        String code = shipment.path("orderCode").asText();
        Instant now = Instant.now().minusSeconds(1);
        ObjectNode picked = event(code, "PICKED_UP", now.minusSeconds(10), UUID.randomUUID().toString(), FEE);
        callback(picked).andExpect(status().isOk());
        callback(picked).andExpect(status().isOk());
        callback(event(code, "COMPLETED", now, UUID.randomUUID().toString(), FEE)).andExpect(status().isOk());
        callback(event(code, "ON_GOING", now.minusSeconds(20), UUID.randomUUID().toString(), BigDecimal.ONE)).andExpect(status().isOk());
        assertOrder(orderId, "DELIVERED", "HELD");
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT provider_status FROM shipments WHERE id = ?", String.class, id));
        assertMoney(FEE, jdbc.queryForObject("SELECT actual_fee FROM shipments WHERE id = ?", BigDecimal.class, id));
        assertEquals(3, eventCount(id));
        assertEquals(0, transactions(orderId, "EARNING"));
    }

    @Test void ambiguousBookingReplayKeepsDurableIntentWithoutPostingAnotherCarrierOrder() throws Exception {
        Fixture f = fixture();
        UUID orderId = checkout(f, quoteId(quote(f)));
        UUID requestId = UUID.randomUUID();
        when(carrier.create(anyMap())).thenThrow(new ShippingProviderException("Network timeout after submitting carrier order"));
        JsonNode first = create(f.seller(), orderId, requestId);
        assertEquals("CREATION_UNCERTAIN", first.path("status").asText());
        JsonNode second = create(f.seller(), orderId, requestId);
        assertEquals(first.path("shipmentId").asText(), second.path("shipmentId").asText());
        assertEquals("CREATION_UNCERTAIN", second.path("status").asText());
        verify(carrier, times(1)).create(anyMap());
        mvc.perform(post("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(f.seller()))
                .contentType(MediaType.APPLICATION_JSON).content(body(request(UUID.randomUUID())))).andExpect(status().isConflict());
        assertMoney(FUNDS.subtract(PRICE).subtract(FEE), balance(f.buyer()));
        assertOrder(orderId, "CONFIRMED", "HELD");
    }

    @Test void checkoutSnapshotsConfirmationPolicyNotifiesStaffOnceAndRequiresSellerConfirmationBeforeBooking() throws Exception {
        var f=fixture();var staff=user("STAFF",BigDecimal.ZERO);UUID requestId=UUID.randomUUID();
        var q=quote(f);var checkout=Map.of("postId",f.post().getId(),"shippingQuoteId",quoteId(q),"requestId",requestId);
        var response=tree(mvc.perform(post("/api/v1/orders").header("Authorization",bearer(f.buyer()))
            .contentType(MediaType.APPLICATION_JSON).content(body(checkout))).andExpect(status().isOk()).andReturn());
        UUID id=UUID.fromString(response.path("id").asText());assertEquals("PROCESSING",response.path("status").asText());
        assertEquals("PENDING",response.path("confirmationStatus").asText());
        Instant paid=Instant.parse(response.path("paidAt").asText());
        assertEquals(Duration.ofHours(24),Duration.between(paid,Instant.parse(response.path("confirmationDeadline").asText())));
        assertEquals(Duration.ofHours(48),Duration.between(paid,Instant.parse(response.path("confirmationCancelAt").asText())));
        mvc.perform(post("/api/v1/orders").header("Authorization",bearer(f.buyer())).contentType(MediaType.APPLICATION_JSON).content(body(checkout)))
            .andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND reference_id=? AND type='ORDER'",Integer.class,staff.getId(),id));
        mvc.perform(get("/api/v1/notifications?type=ORDER&size=100").header("Authorization",bearer(staff)))
            .andDo(result->assertNull(result.getResolvedException(),()->"Notification query failed: "+result.getResolvedException()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].referenceId").value(org.hamcrest.Matchers.hasItem(id.toString())));
        for(String filter:List.of("", "&unreadOnly=true", "&type=ORDER&unreadOnly=true")) {
            mvc.perform(get("/api/v1/notifications?size=100"+filter).header("Authorization",bearer(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].referenceId").value(org.hamcrest.Matchers.hasItem(id.toString())));
        }
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization",bearer(staff)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));
        UUID notificationId=jdbc.queryForObject("SELECT id FROM notifications WHERE user_id=? AND reference_id=?",UUID.class,staff.getId(),id);
        mvc.perform(patch("/api/v1/notifications/"+notificationId+"/read").header("Authorization",bearer(f.buyer())))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/notifications/"+notificationId+"/read").header("Authorization",bearer(staff)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications?type=ORDER&unreadOnly=true").header("Authorization",bearer(staff)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(delete("/api/v1/notifications/"+notificationId).header("Authorization",bearer(staff))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications?type=ORDER").header("Authorization",bearer(staff)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(post("/api/v1/orders/"+id+"/shipments").header("Authorization",bearer(f.seller()))
            .contentType(MediaType.APPLICATION_JSON).content(body(request(UUID.randomUUID())))).andExpect(status().isConflict());
        verify(carrier,never()).create(anyMap());
        mvc.perform(put("/api/v1/orders/"+id+"/confirm").header("Authorization",bearer(f.seller())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        create(f.seller(),id,UUID.randomUUID());verify(carrier,times(1)).create(anyMap());
    }

    private record Fixture(User seller, User buyer, Post post) { }

    private Fixture fixture() throws Exception {
        User admin = user("ADMIN", BigDecimal.ZERO);
        mvc.perform(post("/api/v1/admin/commission-rules").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", "Lalamove test commission " + UUID.randomUUID(),
                        "rate", new BigDecimal("0.05"), "minCommission", BigDecimal.ZERO, "active", true,
                        "reason", "Configure default commission for shipping integration")))).andExpect(status().isCreated());
        User seller = user("SELLER", BigDecimal.ZERO);
        User buyer = user("BUYER", FUNDS);
        Post listing = new Post();
        listing.setUser(seller); listing.setTitle("Lalamove product " + UUID.randomUUID());
        listing.setDescription("Used product in good condition"); listing.setStatus("ACTIVE"); listing.setPrice(PRICE);
        listing.setShippingWeight(1000); listing.setShippingLength(10); listing.setShippingWidth(10); listing.setShippingHeight(10);
        listing = posts.saveAndFlush(listing);
        mvc.perform(put("/api/v1/shipping/pickup-address").header("Authorization", bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content(body(address("Seller", "10.777", "106.695"))))
                .andExpect(status().isOk());
        return new Fixture(seller, buyer, listing);
    }

    private User user(String role, BigDecimal funds) {
        User user = new User("lalamove-" + UUID.randomUUID() + "@example.test", "test-password-hash", AccountStatus.ACTIVE);
        user.addRole(roles.findByCodeWithPermissions(role).orElseThrow()); user = users.saveAndFlush(user);
        UserWallet wallet = new UserWallet(); wallet.setUser(user); wallet.setBalance(funds); wallets.saveAndFlush(wallet);
        return user;
    }

    private Map<String, Object> address(String name, String latitude, String longitude) {
        return Map.of("name", name, "phone", "+84901234567",
                "latitude", new BigDecimal(latitude), "longitude", new BigDecimal(longitude));
    }

    private JsonNode quote(Fixture f) throws Exception {
        return tree(mvc.perform(post("/api/v1/shipping/quotes").header("Authorization", bearer(f.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("postId", f.post().getId(),
                        "deliveryAddress", address("Buyer", "10.781", "106.7")))))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    private UUID quoteId(JsonNode quote) { return UUID.fromString(quote.path("quoteId").asText()); }

    private UUID checkout(Fixture f, UUID quoteId) throws Exception {
        UUID id=UUID.fromString(tree(mvc.perform(post("/api/v1/orders").header("Authorization", bearer(f.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("postId", f.post().getId(), "shippingQuoteId", quoteId,
                        "requestId", UUID.randomUUID())))).andExpect(status().isOk()).andReturn()).path("id").asText());
        mvc.perform(put("/api/v1/orders/"+id+"/confirm").header("Authorization",bearer(f.seller())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        return id;
    }

    private JsonNode create(User seller, UUID orderId, UUID requestId) throws Exception {
        return tree(mvc.perform(post("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content(body(request(requestId))))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    private Map<String, Object> request(UUID requestId) { return Map.of("requestId", requestId, "leg", "SELLER_TO_BUYER"); }

    private JsonNode preview(BigDecimal fee) {
        Instant expiry = Instant.now().plusSeconds(240);
        JsonNode context = json(Map.of("quotationId", "3463513590991397204", "expiresAt", expiry.toString(), "serviceType", "MOTORCYCLE",
                "stops", List.of(Map.of("stopId", "100", "coordinates", Map.of("lat", "10.777", "lng", "106.695")),
                        Map.of("stopId", "101", "coordinates", Map.of("lat", "10.781", "lng", "106.7"))),
                "priceBreakdown", Map.of("total", fee.toPlainString(), "currency", "VND")));
        return json(Map.of("total_fee", fee, "expires_at", expiry.toString(), "provider_quote", context));
    }

    private ObjectNode event(String code, String status, Instant time, String eventId, BigDecimal fee) {
        ObjectNode data = mapper.createObjectNode();
        ObjectNode order = data.putObject("order");
        order.put("orderId", code); order.put("status", status); order.put("driverId", "79973");
        order.putObject("priceBreakdown").put("total", fee.toPlainString()).put("currency", "VND");
        data.put("updatedAt", time.toString());
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("apiKey", KEY); envelope.put("timestamp", Long.parseLong(timestamp));
        envelope.put("signature", LalamoveSignatures.sign(SECRET, timestamp, "POST", LalamoveWebhookVerifier.CALLBACK_PATH, body(data)));
        envelope.put("eventId", eventId); envelope.put("eventType", "ORDER_STATUS_CHANGED"); envelope.put("eventVersion", "v3");
        envelope.set("data", data);
        return envelope;
    }

    private ResultActions callback(JsonNode event) throws Exception {
        return mvc.perform(post(LalamoveWebhookVerifier.CALLBACK_PATH).contentType(MediaType.APPLICATION_JSON).content(body(event)));
    }

    private void assertOrder(UUID id, String status, String escrow) {
        assertEquals(status, jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, id));
        assertEquals(escrow, jdbc.queryForObject("SELECT escrow_status FROM orders WHERE id = ?", String.class, id));
    }

    private int eventCount(UUID id) { return jdbc.queryForObject("SELECT COUNT(*) FROM shipment_events WHERE shipment_id = ?", Integer.class, id); }
    private int transactions(UUID id, String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE reference_id = ? AND type = ?", Integer.class, id, type); }
    private BigDecimal balance(User user) { return wallets.findByUserId(user.getId()).orElseThrow().getBalance(); }
    private void assertMoney(BigDecimal expected, BigDecimal actual) { assertNotNull(actual); assertEquals(0, expected.compareTo(actual)); }
    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
    private String body(Object value) { return mapper.writeValueAsString(value); }
    private JsonNode json(Object value) { return mapper.valueToTree(value); }
    private JsonNode tree(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
}
