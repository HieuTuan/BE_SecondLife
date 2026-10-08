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
import com.secondlife.secondlife.service.AiPriceProvider;
import com.secondlife.secondlife.service.CloudinaryService;
import com.secondlife.secondlife.service.shipping.ShippingProvider;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        "app.shipping.ghn.enabled=true", "app.shipping.ghn.shop-id=123", "app.shipping.ghn.token=test-ghn-token",
        "app.shipping.ghn.webhook-secret=test-webhook-secret"
})
class GhnShippingIntegrationTest {
    private static final BigDecimal FUNDS = new BigDecimal("5000000");
    private static final BigDecimal PRICE = new BigDecimal("1000000");
    private static final BigDecimal FEE = new BigDecimal("27000");
    private static final String SECRET = "test-webhook-secret";
    private static final String LEGACY_TEST_RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String LEGACY_DELIVERY_CODE = "LEGACY-GHN-" + LEGACY_TEST_RUN;
    private static final String LEGACY_CANCEL_CODE = "CANCELLED-LEGACY-GHN-" + LEGACY_TEST_RUN;

    private static final GhnTestDatabase database = new GhnTestDatabase("GHN_FLOW_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("GHN_FLOW_TEST_JDBC_URL"); }
    @AfterAll static void stopOwnContainer() { database.close(); }
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
    @MockitoBean ShippingProvider carrier;
    @MockitoBean AiPriceProvider priceProvider;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoBean(name = "ollamaChatModel") ChatModel ollama;
    @MockitoBean(name = "googleGenAiChatModel") ChatModel google;

    @BeforeEach void carrierReplies() {
        jdbc.update("UPDATE commission_rules SET active = false WHERE active = true");
        when(ollama.getOptions()).thenReturn(ChatOptions.builder().build());
        when(google.getOptions()).thenReturn(ChatOptions.builder().build());
        when(carrier.quote(anyMap())).thenReturn(json(Map.of("total", FEE)));
        when(carrier.leadtime(anyMap())).thenReturn(json(Map.of("leadtime", Instant.now().plusSeconds(86400).getEpochSecond())));
        when(carrier.preview(anyMap())).thenReturn(json(Map.of("total_fee", FEE,
                "expected_delivery_time", Instant.now().plusSeconds(86400).toString())));
        when(carrier.create(anyMap())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        when(carrier.detail(anyString())).thenReturn(json(Map.of("status", "ready_to_pick")));
        when(carrier.label(anyString())).thenReturn(json(Map.of("url", "https://example.test/ghn-label.pdf")));
        when(carrier.cancel(anyString())).thenAnswer(invocation -> json(List.of(Map.of(
                "order_code", invocation.getArgument(0), "result", true, "message", "Success"))));
    }

    @Test void deliveryRequiresCarrierThenBuyerConfirmationAndPaysProductOnlyOnce() throws Exception {
        var f = fixture();
        var quote = quote(f.buyer(), f.post());
        assertMoney(PRICE, quote.get("productPrice").decimalValue());
        assertMoney(FEE, quote.get("shippingFee").decimalValue());
        assertMoney(PRICE.add(FEE), quote.get("totalPayable").decimalValue());
        assertMoney(PRICE, quote.get("insuranceValue").decimalValue());
        assertTrue(Instant.parse(quote.get("expiresAt").asText()).isAfter(Instant.now()));
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote), UUID.randomUUID());
        assertMoney(FUNDS.subtract(PRICE).subtract(FEE), balance(f.buyer()));
        assertEquals(1, transactions(orderId, "PAYMENT"));
        assertMoney(PRICE.add(FEE).negate(), transactionAmount(orderId, "PAYMENT"));

        mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/orders/" + orderId + "/shipped").header("Authorization", bearer(f.seller())))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(f.buyer()))
                        .contentType(MediaType.APPLICATION_JSON).content(body(shipmentRequest(UUID.randomUUID()))))
                .andExpect(status().isForbidden());
        var shipment = shipment(f.seller(), orderId, UUID.randomUUID());
        assertEquals("READY_TO_PICK", shipment.get("status").asText());
        UUID shipmentId = UUID.fromString(shipment.get("shipmentId").asText());
        String code = shipment.get("orderCode").asText();
        var payload = mapper.readTree(jdbc.queryForObject("SELECT payload FROM shipments WHERE id = ?", String.class, shipmentId));
        assertEquals(0, payload.get("cod_amount").asInt());
        assertEquals(1000000, payload.get("insurance_value").asInt());
        assertEquals(1000, payload.get("weight").asInt());
        assertEquals(10, payload.get("length").asInt());
        assertEquals("Seller", payload.get("from_name").asText());
        assertEquals(1442, payload.get("from_district_id").asInt());
        assertEquals(1444, payload.get("to_district_id").asInt());

        Instant pickedAt = Instant.now();
        callback(code, "picked", pickedAt);
        assertOrder(orderId, "SHIPPED", "HELD");
        mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isConflict());
        Instant deliveredAt = pickedAt.plusSeconds(30);
        callback(code, "delivered", deliveredAt);
        callback(code, "delivered", deliveredAt);
        assertOrder(orderId, "DELIVERED", "HELD");
        assertEquals(0, transactions(orderId, "EARNING"));
        assertMoney(BigDecimal.ZERO, balance(f.seller()));
        assertEquals(2, eventCount(shipmentId));

        var staff = user("STAFF", BigDecimal.ZERO);
        for (var actor : List.of(f.buyer(), f.seller(), staff)) {
            mvc.perform(get("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(actor)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].shipmentId").value(shipmentId.toString()));
            mvc.perform(get("/api/v1/shipments/" + shipmentId + "/events").header("Authorization", bearer(actor)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        }
        var stranger = user("BUYER", FUNDS);
        mvc.perform(get("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/shipments/" + shipmentId + "/events").header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        callback(code, "delivered", deliveredAt);
        assertOrder(orderId, "COMPLETED", "RELEASED");
        assertMoney(PRICE, balance(f.seller()));
        assertEquals(1, transactions(orderId, "EARNING"));
        assertMoney(PRICE, transactionAmount(orderId, "EARNING"));
    }

    @Test void commissionSnapshotSurvivesRuleEditAndSettlementPaysNetOnlyOnce() throws Exception {
        var admin = user("ADMIN", BigDecimal.ZERO);
        var rule = commissionRule(admin, "0.05", "0", null);
        var f = fixture();
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var snapshot = tree(mvc.perform(get("/api/v1/orders/" + orderId + "/commission")
                .header("Authorization", bearer(f.buyer()))).andExpect(status().isOk()).andReturn()).get("data");
        assertMoney(new BigDecimal("50000"), snapshot.get("platformCommission").decimalValue());
        assertMoney(new BigDecimal("950000"), snapshot.get("sellerPayout").decimalValue());
        mvc.perform(put("/api/admin/commission-rules/" + rule.get("id").asText())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(body(ruleBody("0.10", "0", null))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/" + orderId + "/settlement").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isNotFound());
        var s = shipment(f.seller(), orderId, UUID.randomUUID());
        callback(s.get("orderCode").asText(), "delivered", Instant.now());
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                    .andExpect(status().isOk());
        }
        var settlement = tree(mvc.perform(get("/api/v1/orders/" + orderId + "/settlement")
                .header("Authorization", bearer(f.seller()))).andExpect(status().isOk()).andReturn()).get("data");
        assertMoney(new BigDecimal("50000"), settlement.get("platformCommission").decimalValue());
        assertMoney(new BigDecimal("950000"), settlement.get("sellerPayout").decimalValue());
        assertMoney(FEE, settlement.get("shippingFee").decimalValue());
        assertMoney(new BigDecimal("950000"), balance(f.seller()));
        assertMoney(new BigDecimal("950000"), transactionAmount(orderId, "EARNING"));
        assertEquals(1, transactions(orderId, "EARNING"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_settlements WHERE order_id = ?", Integer.class, orderId));
    }

    @Test void defaultCommissionNeedsNoScopeAndAppliesMinimumAndMaximumAcrossCategoriesAndPrices() throws Exception {
        var admin = user("ADMIN", BigDecimal.ZERO);
        var payload = new java.util.HashMap<String, Object>();
        payload.put("name", "Default commission with limits"); payload.put("rate", "0.01");
        payload.put("minCommission", "20000"); payload.put("maxCommission", "30000");
        payload.put("active", true); payload.put("reason", "Configure one default policy");
        var rule = tree(mvc.perform(post("/api/admin/commission-rules").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body(payload)))
                .andExpect(status().isCreated()).andReturn()).get("data");
        UUID category = UUID.randomUUID();
        jdbc.update("INSERT INTO categories(id, name) VALUES (?, 'Commission test category')", category);
        var categorized = fixture(); categorized.post().setCategoryId(category); posts.saveAndFlush(categorized.post());
        UUID categoryOrder = order(categorized.buyer(), categorized.post(), quoteId(quote(categorized.buyer(), categorized.post())), UUID.randomUUID());
        assertEquals(rule.get("id").asText(), commission(categorized.buyer(), categoryOrder).get("ruleId").asText());
        assertMoney(new BigDecimal("20000"), commission(categorized.buyer(), categoryOrder).get("platformCommission").decimalValue());
        assertMoney(new BigDecimal("980000"), commission(categorized.buyer(), categoryOrder).get("sellerPayout").decimalValue());
        var fallback = fixture(); fallback.post().setPrice(new BigDecimal("4000000")); posts.saveAndFlush(fallback.post());
        UUID fallbackOrder = order(fallback.buyer(), fallback.post(), quoteId(quote(fallback.buyer(), fallback.post())), UUID.randomUUID());
        assertEquals(rule.get("id").asText(), commission(fallback.buyer(), fallbackOrder).get("ruleId").asText());
        assertMoney(new BigDecimal("30000"), commission(fallback.buyer(), fallbackOrder).get("platformCommission").decimalValue());
        assertMoney(new BigDecimal("3970000"), commission(fallback.buyer(), fallbackOrder).get("sellerPayout").decimalValue());
    }

    @Test void missingCommissionPolicyRollsBackCheckoutAndDoesNotConsumeQuote() throws Exception {
        var f = fixture();
        jdbc.update("UPDATE commission_rules SET active = false WHERE active");
        UUID quoteId = quoteId(quote(f.buyer(), f.post()));
        checkout(f.buyer(), f.post(), quoteId, UUID.randomUUID()).andExpect(status().isConflict());
        assertMoney(FUNDS, balance(f.buyer()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE post_id = ?", Integer.class, f.post().getId()));
        assertNull(jdbc.queryForObject("SELECT consumed_order_id FROM shipping_quotes WHERE id = ?", UUID.class, quoteId));
        assertEquals("ACTIVE", posts.findById(f.post().getId()).orElseThrow().getStatus());
    }

    @Test void defaultCommissionRejectsInvalidLimitsScopeFieldsAndUnauthorizedWritesAndKeepsAudit() throws Exception {
        var admin = user("ADMIN", BigDecimal.ZERO);
        var rule = commissionRule(admin, "0.05", "0", null);
        var url = "/api/admin/commission-rules";
        for (var actor : List.of(user("BUYER", FUNDS), user("SELLER", BigDecimal.ZERO), user("STAFF", BigDecimal.ZERO))) {
            mvc.perform(post(url).header("Authorization", bearer(actor)).contentType(MediaType.APPLICATION_JSON)
                    .content(body(ruleBody("0.05", "0", null)))).andExpect(status().isForbidden());
        }
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body(ruleBody("0.05", "0", null))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(body(ruleBody("0.05", "0", null)))).andExpect(status().isConflict());
        for (var invalid : List.of(
                ruleBody("1.01", "0", null),
                ruleBody("0.05", "100", "99"),
                ruleBody("-0.01", "0", null),
                ruleBody("0.05", "-1", null))) {
            mvc.perform(post(url).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body(invalid)))
                    .andExpect(status().isBadRequest());
        }
        for (var entry : Map.<String, Object>of("type", "CATEGORY", "categoryId", UUID.randomUUID(),
                "transactionValueFrom", 0, "transactionValueTo", 2000000).entrySet()) {
            var scoped = ruleBody("0.05", "0", null); scoped.put(entry.getKey(), entry.getValue());
            mvc.perform(post(url).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                    .content(body(scoped))).andExpect(status().isBadRequest());
            mvc.perform(put(url + "/" + rule.get("id").asText()).header("Authorization", bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON).content(body(scoped))).andExpect(status().isBadRequest());
        }
        mvc.perform(get(url + "?sort=%5B%22ASC%22%5D").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(post(url + "/" + rule.get("id").asText() + "/deactivate").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Disable default"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(get(url + "/" + rule.get("id").asText() + "/history").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "UPDATE commission_rule_audits SET reason = 'mutated' WHERE rule_id = ?", UUID.fromString(rule.get("id").asText())));
        // The database also prevents activating a second default, outside service locking.
        var inactive = ruleBody("0.03", "0", null); inactive.put("active", false);
        var another = tree(mvc.perform(post(url).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(body(inactive))).andExpect(status().isCreated()).andReturn()).get("data");
        jdbc.update("UPDATE commission_rules SET active = true WHERE id = ?", UUID.fromString(rule.get("id").asText()));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "UPDATE commission_rules SET active = true WHERE id = ?", UUID.fromString(another.get("id").asText())));
    }

    @Test void concurrentSettlementUsesOneNetPayoutAndImmutableFinancialRecords() throws Exception {
        var admin = user("ADMIN", BigDecimal.ZERO);
        commissionRule(admin, "0.05", "0", "30000");
        var f = fixture();
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var s = shipment(f.seller(), orderId, UUID.randomUUID());
        callback(s.get("orderCode").asText(), "delivered", Instant.now());
        assertEquals(List.of(200, 200), parallel(
                () -> mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer()))).andReturn().getResponse().getStatus(),
                () -> mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer()))).andReturn().getResponse().getStatus()));
        assertMoney(new BigDecimal("970000"), balance(f.seller()));
        assertEquals(1, transactions(orderId, "EARNING"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_settlements WHERE order_id = ?", Integer.class, orderId));
        for (var table : List.of("order_commission_snapshots", "order_settlements")) {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("DELETE FROM " + table + " WHERE order_id = ?", orderId));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE " + table + " SET commission_base = 1 WHERE order_id = ?", orderId));
        }
        var stranger = user("BUYER", FUNDS);
        for (var suffix : List.of("commission", "settlement")) {
            mvc.perform(get("/api/v1/orders/" + orderId + "/" + suffix).header("Authorization", bearer(stranger)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/orders/" + orderId + "/" + suffix).header("Authorization", bearer(admin)))
                    .andExpect(status().isOk());
        }
    }

    @Test void cancelledOrdersRefundFullPriceWithoutFinalCommission() throws Exception {
        commissionRule(user("ADMIN", BigDecimal.ZERO), "0.05", "0", null);
        var f = fixture(); UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        for (int i = 0; i < 2; i++) mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk());
        assertMoney(FUNDS, balance(f.buyer())); assertMoney(BigDecimal.ZERO, balance(f.seller()));
        assertEquals(1, transactions(orderId, "REFUND"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_settlements WHERE order_id = ?", Integer.class, orderId));
        mvc.perform(get("/api/v1/orders/" + orderId + "/settlement").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isNotFound());
    }

    @Test void frozenEscrowAndChangedOrderPriceBlockSettlement() throws Exception {
        commissionRule(user("ADMIN", BigDecimal.ZERO), "0.05", "0", null);
        var f = fixture(); UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var s = shipment(f.seller(), orderId, UUID.randomUUID()); callback(s.get("orderCode").asText(), "delivered", Instant.now());
        jdbc.update("UPDATE orders SET escrow_status = 'FROZEN' WHERE id = ?", orderId);
        mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer()))).andExpect(status().isConflict());
        assertMoney(BigDecimal.ZERO, balance(f.seller()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_settlements WHERE order_id = ?", Integer.class, orderId));
        jdbc.update("UPDATE orders SET escrow_status = 'HELD', final_price = 999999 WHERE id = ?", orderId);
        mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer()))).andExpect(status().isConflict());
        assertEquals(0, transactions(orderId, "EARNING"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_settlements WHERE order_id = ?", Integer.class, orderId));
    }

    @Test void failedWalletCreditRollsBackSettlementAndAllowsSafeRetry() throws Exception {
        commissionRule(user("ADMIN", BigDecimal.ZERO), "0.05", "0", null);
        var f = fixture(); UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var s = shipment(f.seller(), orderId, UUID.randomUUID()); callback(s.get("orderCode").asText(), "delivered", Instant.now());
        jdbc.execute("""
                CREATE FUNCTION fail_test_commission_earning() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.type = 'EARNING' THEN RAISE EXCEPTION 'Simulated failed wallet credit'; END IF;
                  RETURN NEW;
                END; $$
                """);
        jdbc.execute("CREATE TRIGGER trg_test_commission_earning BEFORE INSERT ON wallet_transactions FOR EACH ROW EXECUTE FUNCTION fail_test_commission_earning()");
        try {
            mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                    .andExpect(status().isInternalServerError());
            assertMoney(BigDecimal.ZERO, balance(f.seller())); assertOrder(orderId, "DELIVERED", "HELD");
            assertEquals(0, transactions(orderId, "EARNING"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_settlements WHERE order_id = ?", Integer.class, orderId));
        } finally {
            jdbc.execute("DROP TRIGGER trg_test_commission_earning ON wallet_transactions");
            jdbc.execute("DROP FUNCTION fail_test_commission_earning()");
        }
        mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk());
        assertMoney(new BigDecimal("950000"), balance(f.seller())); assertOrder(orderId, "COMPLETED", "RELEASED");
        assertEquals(1, transactions(orderId, "EARNING"));
    }

    private JsonNode commission(User actor, UUID orderId) throws Exception {
        return tree(mvc.perform(get("/api/v1/orders/" + orderId + "/commission").header("Authorization", bearer(actor)))
                .andExpect(status().isOk()).andReturn()).get("data");
    }

    private JsonNode commissionRule(User admin, String rate, String min, String max) throws Exception {
        return tree(mvc.perform(post("/api/admin/commission-rules").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body(ruleBody(rate, min, max))))
                .andExpect(status().isCreated()).andReturn()).get("data");
    }
    private Map<String, Object> ruleBody(String rate, String min, String max) {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("name", "Commission test " + UUID.randomUUID());
        payload.put("rate", rate); payload.put("minCommission", min); payload.put("maxCommission", max);
        payload.put("active", true); payload.put("reason", "Configure commission for regression test");
        return payload;
    }

    @Test void quoteOwnershipExpiryAndChangedParcelRejectCheckoutWithoutDebiting() throws Exception {
        var f = fixture();
        var unrelatedSeller = user("SELLER", BigDecimal.ZERO);
        mvc.perform(put("/api/v1/shipping/pickup-address").header("Authorization", bearer(unrelatedSeller))
                        .contentType(MediaType.APPLICATION_JSON).content(body(address("Unrelated seller", 1666))))
                .andExpect(status().isOk());
        UUID firstQuote = quoteId(quote(f.buyer(), f.post()));
        var quotePayload = mapper.readTree(jdbc.queryForObject("SELECT payload FROM shipping_quotes WHERE id = ?", String.class, firstQuote));
        assertEquals("Seller", quotePayload.get("from_name").asText());
        assertEquals(1442, quotePayload.get("from_district_id").asInt());
        var stranger = user("BUYER", FUNDS);
        checkout(stranger, f.post(), firstQuote, UUID.randomUUID()).andExpect(status().isForbidden());
        jdbc.update("UPDATE shipping_quotes SET expires_at = now() - interval '1 minute' WHERE id = ?", firstQuote);
        checkout(f.buyer(), f.post(), firstQuote, UUID.randomUUID()).andExpect(status().isConflict());
        UUID staleParcelQuote = quoteId(quote(f.buyer(), f.post()));
        mvc.perform(put("/api/v1/posts/" + f.post().getId() + "/shipping-package")
                        .header("Authorization", bearer(f.seller())).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("weight", 1200, "length", 10, "width", 10, "height", 10))))
                .andExpect(status().isOk());
        checkout(f.buyer(), f.post(), staleParcelQuote, UUID.randomUUID()).andExpect(status().isConflict());
        assertMoney(FUNDS, balance(f.buyer()));
        assertMoney(FUNDS, balance(stranger));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE post_id = ?", Integer.class, f.post().getId()));
        assertNull(jdbc.queryForObject("SELECT consumed_order_id FROM shipping_quotes WHERE id = ?", UUID.class, staleParcelQuote));
        mvc.perform(get("/api/v1/shipping/pickup-address").header("Authorization", bearer(f.seller())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.districtId").value(1442));
        mvc.perform(put("/api/v1/posts/" + f.post().getId() + "/shipping-package")
                        .header("Authorization", bearer(stranger)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("weight", 1200, "length", 10, "width", 10, "height", 10))))
                .andExpect(status().isForbidden());
    }

    @Test void orderRetriesAndCompetingBuyersCannotChargeOrBuyTwice() throws Exception {
        var f = fixture();
        UUID shippingQuoteId = quoteId(quote(f.buyer(), f.post()));
        UUID requestId = UUID.randomUUID();
        var retried = parallel(
                () -> checkout(f.buyer(), f.post(), shippingQuoteId, requestId).andReturn(),
                () -> checkout(f.buyer(), f.post(), shippingQuoteId, requestId).andReturn());
        assertEquals(List.of(200, 200), retried.stream().map(r -> r.getResponse().getStatus()).toList());
        assertEquals(tree(retried.get(0)).get("id").asText(), tree(retried.get(1)).get("id").asText());
        UUID orderId = UUID.fromString(tree(retried.get(0)).get("id").asText());
        assertEquals(1, transactions(orderId, "PAYMENT"));
        assertMoney(FUNDS.subtract(PRICE).subtract(FEE), balance(f.buyer()));

        var contested = fixture();
        var anotherBuyer = user("BUYER", FUNDS);
        UUID quoteA = quoteId(quote(contested.buyer(), contested.post()));
        UUID quoteB = quoteId(quote(anotherBuyer, contested.post()));
        var competing = parallel(
                () -> checkout(contested.buyer(), contested.post(), quoteA, UUID.randomUUID()).andReturn(),
                () -> checkout(anotherBuyer, contested.post(), quoteB, UUID.randomUUID()).andReturn());
        var statuses = competing.stream().map(r -> r.getResponse().getStatus()).toList();
        assertEquals(1, Collections.frequency(statuses, 200));
        assertEquals(1, Collections.frequency(statuses, 409));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE post_id = ?", Integer.class, contested.post().getId()));
        assertMoney(FUNDS.multiply(BigDecimal.valueOf(2)).subtract(PRICE).subtract(FEE), balance(contested.buyer()).add(balance(anotherBuyer)));
        assertEquals("SOLD", posts.findById(contested.post().getId()).orElseThrow().getStatus());
    }

    @Test void uncertainCarrierCreationPersistsAndRetriesWithStableClientCode() throws Exception {
        var f = fixture();
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var submitted = new ArrayList<String>();
        var attempts = new AtomicInteger();
        doAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(0);
            submitted.add(payload.get("client_order_code").toString());
            if (attempts.incrementAndGet() == 1) throw new ShippingProviderException("GHN timed out after accepting create", 504);
            return created(payload);
        }).when(carrier).create(anyMap());
        UUID requestId = UUID.randomUUID();
        mvc.perform(post("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(f.seller()))
                        .contentType(MediaType.APPLICATION_JSON).content(body(shipmentRequest(requestId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CREATION_UNCERTAIN"));
        UUID uncertainId = jdbc.queryForObject("SELECT id FROM shipments WHERE order_id = ?", UUID.class, orderId);
        assertEquals("CREATION_UNCERTAIN", shipmentStatus(uncertainId));
        var recovered = shipment(f.seller(), orderId, requestId);
        assertEquals(uncertainId.toString(), recovered.get("shipmentId").asText());
        assertEquals("READY_TO_PICK", recovered.get("status").asText());
        assertEquals(submitted.get(0), submitted.get(1));
        var replay = shipment(f.seller(), orderId, requestId);
        assertEquals(recovered.get("orderCode").asText(), replay.get("orderCode").asText());
        verify(carrier, times(2)).create(anyMap());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM shipments WHERE order_id = ?", Integer.class, orderId));
        assertEquals(1, transactions(orderId, "PAYMENT"));
        assertOrder(orderId, "PROCESSING", "HELD");
    }

    @Test void callbacksRequireSecretDeduplicateAndNeverRegressTerminalState() throws Exception {
        var f = fixture();
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var shipment = shipment(f.seller(), orderId, UUID.randomUUID());
        UUID shipmentId = UUID.fromString(shipment.get("shipmentId").asText());
        String code = shipment.get("orderCode").asText();
        Instant pickedAt = Instant.now();
        String pickedBody = body(callbackBody(code, "picked", pickedAt));
        mvc.perform(post("/api/v1/shipping/callback").contentType(MediaType.APPLICATION_JSON).content(pickedBody))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/shipping/callback").header("X-GHN-Secret", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(pickedBody))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/shipping/callback").header("X-GHN-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("ShopID", 999, "OrderCode", code,
                                "Type", "switch_status", "Time", pickedAt.toString(), "Status", "picked"))))
                .andExpect(status().isForbidden());
        assertEquals(0, eventCount(shipmentId));
        callback(code, "picked", pickedAt);
        callback(code, "picked", pickedAt);
        assertEquals(1, eventCount(shipmentId));
        callback(code, "ready_to_pick", pickedAt.minusSeconds(30));
        assertOrder(orderId, "SHIPPED", "HELD");
        assertEquals("PICKED", shipmentStatus(shipmentId));
        callback(code, "delivered", pickedAt.plusSeconds(30));
        callback(code, "picked", pickedAt.plusSeconds(60));
        assertEquals("DELIVERED", shipmentStatus(shipmentId));
        assertOrder(orderId, "DELIVERED", "HELD");
        assertEquals(0, transactions(orderId, "EARNING"));
        assertEquals(0, transactions(orderId, "REFUND"));
    }

    @Test void uncreatedShipmentCancellationRefundsProductAndFeeOnce() throws Exception {
        var f = fixture();
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk());
        assertOrder(orderId, "CANCELLED", "REFUNDED");
        assertMoney(FUNDS, balance(f.buyer()));
        assertMoney(PRICE.add(FEE), transactionAmount(orderId, "REFUND"));
        mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk());
        assertEquals(1, transactions(orderId, "REFUND"));
        verify(carrier, never()).cancel(anyString());
    }

    @Test void activeCarrierShipmentBlocksRefundUntilCancellationIsConfirmed() throws Exception {
        var f = fixture();
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var shipment = shipment(f.seller(), orderId, UUID.randomUUID());
        UUID shipmentId = UUID.fromString(shipment.get("shipmentId").asText());
        String code = shipment.get("orderCode").asText();
        callback(code, "picked", Instant.now());
        mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/shipments/" + shipmentId + "/cancel").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/shipments/" + shipmentId + "/cancel").header("Authorization", bearer(f.seller())))
                .andExpect(status().isConflict());
        verify(carrier, never()).cancel(anyString());
        assertNotEquals("CANCELLED", shipmentStatus(shipmentId));
        assertEquals(0, transactions(orderId, "REFUND"));
        assertMoney(FUNDS.subtract(PRICE).subtract(FEE), balance(f.buyer()));

        var cancellable = fixture();
        UUID cancellableOrder = order(cancellable.buyer(), cancellable.post(), quoteId(quote(cancellable.buyer(), cancellable.post())), UUID.randomUUID());
        var ready = shipment(cancellable.seller(), cancellableOrder, UUID.randomUUID());
        UUID readyId = UUID.fromString(ready.get("shipmentId").asText());
        String readyCode = ready.get("orderCode").asText();
        doThrow(new ShippingProviderException("GHN cancellation timed out", 504)).when(carrier).cancel(readyCode);
        mvc.perform(post("/api/v1/shipments/" + readyId + "/cancel").header("Authorization", bearer(cancellable.seller())))
                .andExpect(status().is5xxServerError());
        assertEquals("READY_TO_PICK", shipmentStatus(readyId));
        mvc.perform(put("/api/v1/orders/" + cancellableOrder + "/cancel").header("Authorization", bearer(cancellable.buyer())))
                .andExpect(status().isConflict());
        assertEquals(0, transactions(cancellableOrder, "REFUND"));
        doReturn(json(List.of(Map.of("order_code", readyCode, "result", true, "message", "Success"))))
                .when(carrier).cancel(readyCode);
        mvc.perform(post("/api/v1/shipments/" + readyId + "/cancel").header("Authorization", bearer(cancellable.seller())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(put("/api/v1/orders/" + cancellableOrder + "/cancel").header("Authorization", bearer(cancellable.buyer())))
                .andExpect(status().isOk());
        assertMoney(FUNDS.subtract(FEE), balance(cancellable.buyer()));
        assertMoney(PRICE, transactionAmount(cancellableOrder, "REFUND"));
        assertEquals(1, transactions(cancellableOrder, "REFUND"));
    }

    @Test void staffReturnFreezesEscrowAndRefundsProductOnlyAfterReturnDelivery() throws Exception {
        var f = fixture();
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var outbound = shipment(f.seller(), orderId, UUID.randomUUID());
        callback(outbound.get("orderCode").asText(), "delivered", Instant.now());
        doReturn(json(Map.of("total_fee", 31000, "expected_delivery_time", Instant.now().plusSeconds(86400).toString())))
                .when(carrier).preview(argThat(payload -> payload.get("from_district_id").equals(1444) && payload.get("to_district_id").equals(1442)));
        var staff = user("STAFF", BigDecimal.ZERO);
        Map<String, Object> returnRequest = Map.of("requestId", UUID.randomUUID(), "leg", "BUYER_TO_SELLER",
                "reason", "Staff decision R1", "fromAddress", address("Buyer", 1444), "toAddress", address("Seller", 1442));
        for (var actor : List.of(f.buyer(), f.seller())) {
            mvc.perform(post("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(actor))
                            .contentType(MediaType.APPLICATION_JSON).content(body(returnRequest)))
                    .andExpect(status().isForbidden());
        }
        var returned = tree(mvc.perform(post("/api/v1/orders/" + orderId + "/shipments")
                        .header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON).content(body(returnRequest)))
                .andExpect(status().isOk()).andReturn()).get("data");
        UUID returnId = UUID.fromString(returned.get("shipmentId").asText());
        assertMoney(new BigDecimal("31000"), returned.get("quotedFee").decimalValue());
        mvc.perform(get("/api/v1/shipments/" + returnId + "/label").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value("https://example.test/ghn-label.pdf"));
        verify(carrier).label(returned.get("orderCode").asText());
        for (var actor : List.of(f.buyer(), f.seller())) {
            mvc.perform(post("/api/v1/shipments/" + returnId + "/cancel").header("Authorization", bearer(actor)))
                    .andExpect(status().isForbidden());
        }
        verify(carrier, never()).cancel(anyString());
        assertOrder(orderId, "DELIVERED", "FROZEN");
        mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/shipments/" + returnId + "/refund").header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "returned"))))
                .andExpect(status().isConflict());
        Instant returnedAt = Instant.now().plusSeconds(30);
        callback(returned.get("orderCode").asText(), "delivered", returnedAt);
        mvc.perform(post("/api/v1/shipments/" + returnId + "/refund").header("Authorization", bearer(f.buyer()))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "returned"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/shipments/" + returnId + "/refund").header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "returned"))))
                .andExpect(status().isOk());
        callback(returned.get("orderCode").asText(), "delivered", returnedAt);
        mvc.perform(post("/api/v1/shipments/" + returnId + "/refund").header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "returned"))))
                .andExpect(status().isOk());
        assertOrder(orderId, "CANCELLED", "REFUNDED");
        assertMoney(FUNDS.subtract(FEE), balance(f.buyer()));
        assertMoney(BigDecimal.ZERO, balance(f.seller()));
        assertMoney(PRICE, transactionAmount(orderId, "REFUND"));
        assertEquals(1, transactions(orderId, "REFUND"));
        assertEquals(0, transactions(orderId, "EARNING"));
    }

    @Test void inspectedProductIsQuotedAndShippedFromItsStoredInspectionCenter() throws Exception {
        var f = fixture();
        var staff = user("STAFF", BigDecimal.ZERO);
        UUID inspectionId = UUID.randomUUID();
        jdbc.update("UPDATE posts SET status = 'PENDING_INSPECTION' WHERE id = ?", f.post().getId());
        jdbc.update("""
                INSERT INTO inspection_orders(id, post_id, status, inspection_fee, shipping_fee, created_at)
                VALUES (?, ?, 'PENDING', 0, 0, now())
                """, inspectionId, f.post().getId());
        Map<String, Object> toCenter = Map.of("requestId", UUID.randomUUID(), "leg", "SELLER_TO_CENTER",
                "toAddress", address("Inspection center", 1666));
        mvc.perform(post("/api/v1/inspection-orders/" + inspectionId + "/shipments")
                        .header("Authorization", bearer(f.seller())).contentType(MediaType.APPLICATION_JSON).content(body(toCenter)))
                .andExpect(status().isForbidden());
        var centerInbound = tree(mvc.perform(post("/api/v1/inspection-orders/" + inspectionId + "/shipments")
                        .header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON).content(body(toCenter)))
                .andExpect(status().isOk()).andReturn()).get("data");
        var inspector = user("INSPECTOR", BigDecimal.ZERO);
        jdbc.update("UPDATE inspection_orders SET inspector_id = ? WHERE id = ?", inspector.getId(), inspectionId);
        mvc.perform(post("/api/v1/inspector/orders/" + inspectionId + "/result").header("Authorization", bearer(inspector))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "PASSED", "note", "Inspection result before arrival"))))
                .andExpect(status().isConflict());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM inspection_orders WHERE id = ?", String.class, inspectionId));
        callback(centerInbound.get("orderCode").asText(), "delivered", Instant.now());
        jdbc.update("UPDATE inspection_orders SET status = 'PASSED' WHERE id = ?", inspectionId);
        jdbc.update("UPDATE posts SET status = 'ACTIVE' WHERE id = ?", f.post().getId());

        UUID shippingQuoteId = quoteId(quote(f.buyer(), f.post()));
        var quotedPayload = mapper.readTree(jdbc.queryForObject("SELECT payload FROM shipping_quotes WHERE id = ?", String.class, shippingQuoteId));
        assertEquals("CENTER_TO_BUYER", jdbc.queryForObject("SELECT leg FROM shipping_quotes WHERE id = ?", String.class, shippingQuoteId));
        assertEquals("Inspection center", quotedPayload.get("from_name").asText());
        assertEquals(1666, quotedPayload.get("from_district_id").asInt());
        assertEquals(1444, quotedPayload.get("to_district_id").asInt());
        var previewed = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(carrier, atLeastOnce()).preview(previewed.capture());
        assertEquals(quotedPayload, mapper.readTree(body(previewed.getAllValues().getLast())), "GHN must preview the exact stored center route");
        UUID orderId = order(f.buyer(), f.post(), shippingQuoteId, UUID.randomUUID());
        mvc.perform(post("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(f.seller()))
                        .contentType(MediaType.APPLICATION_JSON).content(body(shipmentRequest(UUID.randomUUID()))))
                .andExpect(status().isConflict());
        Map<String, Object> centerOutboundRequest = Map.of("requestId", UUID.randomUUID(), "leg", "CENTER_TO_BUYER");
        mvc.perform(post("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(f.seller()))
                        .contentType(MediaType.APPLICATION_JSON).content(body(centerOutboundRequest)))
                .andExpect(status().isForbidden());
        var centerOutbound = tree(mvc.perform(post("/api/v1/orders/" + orderId + "/shipments")
                        .header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON).content(body(centerOutboundRequest)))
                .andExpect(status().isOk()).andReturn()).get("data");
        UUID shipmentId = UUID.fromString(centerOutbound.get("shipmentId").asText());
        var shippedPayload = mapper.readTree(jdbc.queryForObject("SELECT payload FROM shipments WHERE id = ?", String.class, shipmentId));
        assertEquals("CENTER_TO_BUYER", centerOutbound.get("leg").asText());
        assertEquals("Inspection center", shippedPayload.get("from_name").asText());
        assertEquals(1666, shippedPayload.get("from_district_id").asInt());
        assertEquals(1444, shippedPayload.get("to_district_id").asInt());
        for (String field : List.of("from_name", "from_address", "from_district_id", "from_ward_code", "to_address", "to_district_id", "to_ward_code"))
            assertEquals(quotedPayload.get(field), shippedPayload.get(field), "Carrier route must match the paid quote: " + field);
        assertMoney(FEE, centerOutbound.get("quotedFee").decimalValue());
        assertMoney(FUNDS.subtract(PRICE).subtract(FEE), balance(f.buyer()));
        assertOrder(orderId, "PROCESSING", "HELD");

        callback(centerOutbound.get("orderCode").asText(), "delivered", Instant.now());
        var returnToSeller = tree(mvc.perform(post("/api/v1/orders/" + orderId + "/shipments")
                        .header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("requestId", UUID.randomUUID(), "leg", "BUYER_TO_SELLER", "reason", "Staff center return decision"))))
                .andExpect(status().isOk()).andReturn()).get("data");
        var returnedPayload = mapper.readTree(jdbc.queryForObject("SELECT payload FROM shipments WHERE id = ?", String.class,
                UUID.fromString(returnToSeller.get("shipmentId").asText())));
        assertEquals(1444, returnedPayload.get("from_district_id").asInt());
        assertEquals("Seller", returnedPayload.get("to_name").asText());
        assertEquals(1442, returnedPayload.get("to_district_id").asInt());
    }

    @Test void failedBuyerReturnCannotRefundWhenCarrierReturnsGoodsToBuyer() throws Exception {
        var f = fixture();
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var outbound = shipment(f.seller(), orderId, UUID.randomUUID());
        callback(outbound.get("orderCode").asText(), "delivered", Instant.now());
        var staff = user("STAFF", BigDecimal.ZERO);
        var returned = tree(mvc.perform(post("/api/v1/orders/" + orderId + "/shipments")
                        .header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("requestId", UUID.randomUUID(), "leg", "BUYER_TO_SELLER", "reason", "Staff decision R2"))))
                .andExpect(status().isOk()).andReturn()).get("data");
        UUID returnId = UUID.fromString(returned.get("shipmentId").asText());
        callback(returned.get("orderCode").asText(), "returned", Instant.now().plusSeconds(20));
        assertEquals("RETURNED", shipmentStatus(returnId));
        mvc.perform(post("/api/v1/shipments/" + returnId + "/refund").header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Return delivery failed"))))
                .andExpect(status().isConflict());
        assertOrder(orderId, "DELIVERED", "FROZEN");
        assertMoney(FUNDS.subtract(PRICE).subtract(FEE), balance(f.buyer()));
        assertEquals(0, transactions(orderId, "REFUND"));
        assertEquals(0, transactions(orderId, "EARNING"));

        var replacement = tree(mvc.perform(post("/api/v1/orders/" + orderId + "/shipments")
                        .header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("requestId", UUID.randomUUID(), "leg", "BUYER_TO_SELLER", "reason", "Staff decision R2 retry"))))
                .andExpect(status().isOk()).andReturn()).get("data");
        UUID replacementId = UUID.fromString(replacement.get("shipmentId").asText());
        assertNotEquals(returnId, replacementId);
        assertOrder(orderId, "DELIVERED", "FROZEN");
        mvc.perform(post("/api/v1/shipments/" + replacementId + "/refund").header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Replacement not delivered yet"))))
                .andExpect(status().isConflict());
        assertEquals(0, transactions(orderId, "REFUND"));
        callback(replacement.get("orderCode").asText(), "delivered", Instant.now().plusSeconds(40));
        mvc.perform(post("/api/v1/shipments/" + replacementId + "/refund").header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Replacement reached seller"))))
                .andExpect(status().isOk());
        assertOrder(orderId, "CANCELLED", "REFUNDED");
        assertMoney(FUNDS.subtract(FEE), balance(f.buyer()));
        assertEquals(1, transactions(orderId, "REFUND"));
    }

    @Test void feeCorrectionAtDeliveryTimestampUpdatesFeeWithoutRegressingStatus() throws Exception {
        var f = fixture();
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var shipment = shipment(f.seller(), orderId, UUID.randomUUID());
        UUID shipmentId = UUID.fromString(shipment.get("shipmentId").asText());
        String code = shipment.get("orderCode").asText();
        Instant deliveredAt = Instant.now();
        mvc.perform(post("/api/v1/shipping/callback").header("X-GHN-Secret", SECRET).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("ShopID", 123, "OrderCode", code, "Type", "switch_status", "Time", deliveredAt.toString(),
                                "Status", "delivered", "TotalFee", 0, "Fee", Map.of()))))
                .andExpect(status().isOk());
        assertMoney(FEE, jdbc.queryForObject("SELECT actual_fee FROM shipments WHERE id = ?", BigDecimal.class, shipmentId));
        mvc.perform(post("/api/v1/shipping/callback").header("X-GHN-Secret", SECRET).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("ShopID", 123, "OrderCode", code, "Type", "update_fee", "Time", deliveredAt.toString(),
                                "Status", "delivered", "TotalFee", 28000))))
                .andExpect(status().isOk());
        assertMoney(new BigDecimal("28000"), jdbc.queryForObject("SELECT actual_fee FROM shipments WHERE id = ?", BigDecimal.class, shipmentId));
        mvc.perform(post("/api/v1/shipping/callback").header("X-GHN-Secret", SECRET).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("ShopID", 123, "OrderCode", code, "Type", "switch_status", "Time", deliveredAt.plusSeconds(10).toString(),
                                "Status", "delivered", "TotalFee", 0, "Fee", Map.of()))))
                .andExpect(status().isOk());
        assertMoney(new BigDecimal("28000"), jdbc.queryForObject("SELECT actual_fee FROM shipments WHERE id = ?", BigDecimal.class, shipmentId));
        assertEquals("DELIVERED", shipmentStatus(shipmentId));
        assertOrder(orderId, "DELIVERED", "HELD");
        assertEquals(0, transactions(orderId, "EARNING"));
    }

    @Test void damageReportedAfterDeliveryKeepsDeliveredStateAndFreezesEscrow() throws Exception {
        var f = fixture();
        UUID orderId = order(f.buyer(), f.post(), quoteId(quote(f.buyer(), f.post())), UUID.randomUUID());
        var shipment = shipment(f.seller(), orderId, UUID.randomUUID());
        UUID shipmentId = UUID.fromString(shipment.get("shipmentId").asText());
        String code = shipment.get("orderCode").asText();
        Instant deliveredAt = Instant.now();
        callback(code, "delivered", deliveredAt);
        assertOrder(orderId, "DELIVERED", "HELD");
        callback(code, "damage", deliveredAt.plusSeconds(10));
        assertEquals("DELIVERED", shipmentStatus(shipmentId));
        assertOrder(orderId, "DELIVERED", "FROZEN");
        mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isConflict());
        assertMoney(FUNDS.subtract(PRICE).subtract(FEE), balance(f.buyer()));
        assertMoney(BigDecimal.ZERO, balance(f.seller()));
        assertEquals(0, transactions(orderId, "EARNING"));
        assertEquals(0, transactions(orderId, "REFUND"));
    }

    @Test void uncertainInspectionShippingKeepsOriginalRouteAndAllowsReplacementAfterReturn() throws Exception {
        var f = fixture();
        var staff = user("STAFF", BigDecimal.ZERO);
        UUID inspectionId = UUID.randomUUID(), requestId = UUID.randomUUID();
        jdbc.update("UPDATE posts SET status = 'PENDING_INSPECTION' WHERE id = ?", f.post().getId());
        jdbc.update("""
                INSERT INTO inspection_orders(id, post_id, status, inspection_fee, shipping_fee, created_at)
                VALUES (?, ?, 'PENDING', 0, 0, now())
                """, inspectionId, f.post().getId());
        var attempts = new AtomicInteger();
        var submitted = new ArrayList<JsonNode>();
        doAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(0);
            submitted.add(mapper.readTree(body(payload)));
            if (attempts.incrementAndGet() == 1) throw new ShippingProviderException("GHN center shipment creation timed out", 504);
            return created(payload);
        }).when(carrier).create(anyMap());
        Map<String, Object> centerAddress = Map.of("name", "Inspection center", "phone", "0901234567", "address", "123 Test street",
                "provinceName", "Ho Chi Minh", "wardName", "Test ward", "newAddress", true);
        Map<String, Object> originalRequest = Map.of("requestId", requestId, "leg", "SELLER_TO_CENTER",
                "toAddress", centerAddress, "reason", "Assigned center route");
        var uncertain = tree(mvc.perform(post("/api/v1/inspection-orders/" + inspectionId + "/shipments")
                        .header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON).content(body(originalRequest)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CREATION_UNCERTAIN")).andReturn()).get("data");
        UUID shipmentId = UUID.fromString(uncertain.get("shipmentId").asText());
        mvc.perform(put("/api/v1/shipping/pickup-address").header("Authorization", bearer(f.seller()))
                        .contentType(MediaType.APPLICATION_JSON).content(body(address("Seller moved", 1777))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/posts/" + f.post().getId() + "/shipping-package").header("Authorization", bearer(f.seller()))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("weight", 2300, "length", 20, "width", 12, "height", 11))))
                .andExpect(status().isOk());
        for (var invalid : List.of(
                Map.of("requestId", requestId, "leg", "SELLER_TO_CENTER", "toAddress", address("Different center", 1667), "reason", "Assigned center route"),
                Map.of("requestId", requestId, "leg", "SELLER_TO_CENTER", "toAddress", centerAddress, "reason", "Changed decision"),
                Map.of("requestId", UUID.randomUUID(), "leg", "SELLER_TO_CENTER", "toAddress", centerAddress, "reason", "Assigned center route"))) {
            mvc.perform(post("/api/v1/inspection-orders/" + inspectionId + "/shipments").header("Authorization", bearer(staff))
                            .contentType(MediaType.APPLICATION_JSON).content(body(invalid)))
                    .andExpect(status().isConflict());
        }
        verify(carrier, times(1)).create(anyMap());
        var recovered = tree(mvc.perform(post("/api/v1/inspection-orders/" + inspectionId + "/shipments")
                        .header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON).content(body(originalRequest)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY_TO_PICK")).andReturn()).get("data");
        assertEquals(shipmentId.toString(), recovered.get("shipmentId").asText());
        assertEquals(submitted.get(0), submitted.get(1));
        assertEquals("Seller", submitted.get(1).get("from_name").asText());
        assertEquals(1442, submitted.get(1).get("from_district_id").asInt());
        assertTrue(submitted.get(1).get("is_new_to_address").asBoolean());
        assertFalse(submitted.get(1).has("to_district_name"));
        assertFalse(submitted.get(1).has("to_district_id"));
        assertFalse(submitted.get(1).has("to_ward_code"));
        assertEquals(1000, submitted.get(1).get("weight").asInt());
        assertEquals(10, submitted.get(1).get("length").asInt());
        callback(recovered.get("orderCode").asText(), "returned", Instant.now());
        var replacement = tree(mvc.perform(post("/api/v1/inspection-orders/" + inspectionId + "/shipments")
                        .header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("requestId", UUID.randomUUID(), "leg", "SELLER_TO_CENTER",
                                "toAddress", centerAddress, "reason", "Replacement center shipment"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY_TO_PICK")).andReturn()).get("data");
        assertNotEquals(recovered.get("shipmentId").asText(), replacement.get("shipmentId").asText());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM shipments WHERE inspection_order_id = ?", Integer.class, inspectionId));
        assertEquals(1777, submitted.get(2).get("from_district_id").asInt());
        assertEquals(2300, submitted.get(2).get("weight").asInt());
    }

    @Test void staffCanImportVerifiedLegacyDeliveryAndBuyerStillControlsSettlement() throws Exception {
        var f = fixture();
        var staff = user("STAFF", BigDecimal.ZERO);
        UUID orderId = UUID.randomUUID(), requestId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders(id, post_id, buyer_id, seller_id, final_price, status, escrow_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'SHIPPED', 'HELD', now(), now())
                """, orderId, f.post().getId(), f.buyer().getId(), f.seller().getId(), PRICE);
        jdbc.update("UPDATE posts SET status = 'SOLD' WHERE id = ?", f.post().getId());
        jdbc.update("UPDATE user_wallets SET balance = ? WHERE user_id = ?", FUNDS.subtract(PRICE), f.buyer().getId());
        String updatedAt = Instant.now().toString();
        doReturn(json(Map.of("shop_id", 999, "order_code", "WRONG-SHOP", "client_order_code", "wrong-shop-client",
                "status", "delivered", "updated_date", updatedAt, "cod_amount", 0))).when(carrier).detail("WRONG-SHOP");
        doReturn(json(Map.of("shop_id", 123, "order_code", "COD-GHN", "client_order_code", "cod-client",
                "status", "delivered", "updated_date", updatedAt, "cod_amount", 1000))).when(carrier).detail("COD-GHN");
        doReturn(json(Map.of("shop_id", 123, "order_code", LEGACY_DELIVERY_CODE, "client_order_code", "legacy-client-" + LEGACY_TEST_RUN,
                "status", "delivered", "updated_date", updatedAt, "cod_amount", 0))).when(carrier).detail(LEGACY_DELIVERY_CODE);
        String url = "/api/v1/orders/" + orderId + "/shipments/import";
        Map<String, Object> importRequest = Map.of("requestId", requestId, "orderCode", LEGACY_DELIVERY_CODE, "reason", "Staff verified legacy carrier order");
        mvc.perform(post(url).header("Authorization", bearer(f.buyer())).contentType(MediaType.APPLICATION_JSON).content(body(importRequest)))
                .andExpect(status().isForbidden());
        for (String invalidCode : List.of("WRONG-SHOP", "COD-GHN")) {
            mvc.perform(post(url).header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("requestId", UUID.randomUUID(), "orderCode", invalidCode, "reason", "Staff legacy check"))))
                    .andExpect(status().isConflict());
        }
        assertOrder(orderId, "SHIPPED", "HELD");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shipments WHERE order_id = ?", Integer.class, orderId));
        var imported = tree(mvc.perform(post(url).header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON).content(body(importRequest)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DELIVERED")).andReturn()).get("data");
        UUID shipmentId = UUID.fromString(imported.get("shipmentId").asText());
        assertEquals(LEGACY_DELIVERY_CODE, imported.get("orderCode").asText());
        assertEquals("legacy-client-" + LEGACY_TEST_RUN, jdbc.queryForObject("SELECT client_order_code FROM shipments WHERE id = ?", String.class, shipmentId));
        assertOrder(orderId, "DELIVERED", "HELD");
        assertNull(jdbc.queryForObject("SELECT shipping_quote_id FROM orders WHERE id = ?", UUID.class, orderId));
        assertEquals(1, eventCount(shipmentId));
        assertEquals(0, transactions(orderId, "EARNING"));
        assertEquals(0, transactions(orderId, "PAYMENT"));
        assertMoney(FUNDS.subtract(PRICE), balance(f.buyer()));
        var replayed = tree(mvc.perform(post(url).header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON).content(body(importRequest)))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertEquals(shipmentId.toString(), replayed.get("shipmentId").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM shipments WHERE order_id = ?", Integer.class, orderId));
        mvc.perform(post("/api/admin/orders/" + orderId + "/commission-snapshot")
                        .header("Authorization", bearer(user("ADMIN", BigDecimal.ZERO)))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Explicitly bind legacy delivery to configured policy"))))
                .andExpect(status().isOk());
        assertEquals(1, eventCount(shipmentId));
        mvc.perform(put("/api/v1/orders/" + orderId + "/delivered").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isOk());
        assertOrder(orderId, "COMPLETED", "RELEASED");
        assertMoney(PRICE, balance(f.seller()));
        assertMoney(FUNDS.subtract(PRICE), balance(f.buyer()));
        assertEquals(1, transactions(orderId, "EARNING"));
    }

    @Test void verifiedLegacyCarrierCancellationAllowsOneProductRefund() throws Exception {
        var f = fixture();
        var staff = user("STAFF", BigDecimal.ZERO);
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders(id, post_id, buyer_id, seller_id, final_price, status, escrow_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'SHIPPED', 'HELD', now(), now())
                """, orderId, f.post().getId(), f.buyer().getId(), f.seller().getId(), PRICE);
        jdbc.update("UPDATE posts SET status = 'SOLD' WHERE id = ?", f.post().getId());
        jdbc.update("UPDATE user_wallets SET balance = ? WHERE user_id = ?", FUNDS.subtract(PRICE), f.buyer().getId());
        mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", bearer(f.buyer())))
                .andExpect(status().isConflict());
        doReturn(json(Map.of("shop_id", 123, "order_code", LEGACY_CANCEL_CODE, "client_order_code", "cancelled-legacy-client-" + LEGACY_TEST_RUN,
                "status", "cancel", "updated_date", Instant.now().toString(), "cod_amount", 0)))
                .when(carrier).detail(LEGACY_CANCEL_CODE);
        mvc.perform(post("/api/v1/orders/" + orderId + "/shipments/import").header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("requestId", UUID.randomUUID(),
                                "orderCode", LEGACY_CANCEL_CODE, "reason", "Staff verified legacy cancellation"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertOrder(orderId, "PROCESSING", "HELD");
        assertEquals(0, transactions(orderId, "REFUND"));
        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", bearer(f.buyer())))
                    .andExpect(status().isOk());
        }
        assertOrder(orderId, "CANCELLED", "REFUNDED");
        assertMoney(FUNDS, balance(f.buyer()));
        assertMoney(PRICE, transactionAmount(orderId, "REFUND"));
        assertEquals(1, transactions(orderId, "REFUND"));
        assertEquals(0, transactions(orderId, "EARNING"));
        verify(carrier, never()).cancel(anyString());
    }

    private record Fixture(User seller, User buyer, Post post) {}
    private Fixture fixture() throws Exception {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM commission_rules WHERE active", Integer.class) == 0)
            commissionRule(user("ADMIN", BigDecimal.ZERO), "0", "0", null);
        var seller = user("SELLER", BigDecimal.ZERO);
        var buyer = user("BUYER", FUNDS);
        Post post = new Post();
        post.setUser(seller); post.setTitle("GHN product " + UUID.randomUUID());
        post.setDescription("Used product in good condition"); post.setStatus("ACTIVE"); post.setPrice(PRICE);
        post.setShippingWeight(1000); post.setShippingLength(10); post.setShippingWidth(10); post.setShippingHeight(10);
        post = posts.saveAndFlush(post);
        mvc.perform(put("/api/v1/shipping/pickup-address").header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON).content(body(address("Seller", 1442))))
                .andExpect(status().isOk());
        return new Fixture(seller, buyer, post);
    }
    private User user(String role, BigDecimal funds) {
        var user = new User("ghn-" + UUID.randomUUID() + "@example.test", "test-password-hash", AccountStatus.ACTIVE);
        user.addRole(roles.findByCodeWithPermissions(role).orElseThrow()); user = users.saveAndFlush(user);
        var wallet = new UserWallet(); wallet.setUser(user); wallet.setBalance(funds); wallets.saveAndFlush(wallet);
        return user;
    }
    private Map<String, Object> address(String name, int districtId) {
        return Map.of("name", name, "phone", "0901234567", "address", "123 Test street",
                "provinceName", "Ho Chi Minh", "districtName", "Test district", "wardName", "Test ward",
                "districtId", districtId, "wardCode", "20308", "newAddress", false);
    }
    private JsonNode quote(User buyer, Post post) throws Exception {
        return tree(mvc.perform(post("/api/v1/shipping/quotes").header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("postId", post.getId(), "deliveryAddress", address("Buyer", 1444)))))
                .andExpect(status().isOk()).andReturn()).get("data");
    }
    private UUID quoteId(JsonNode quote) { return UUID.fromString(quote.get("quoteId").asText()); }
    private org.springframework.test.web.servlet.ResultActions checkout(User buyer, Post post, UUID quoteId, UUID requestId) throws Exception {
        return mvc.perform(post("/api/v1/orders").header("Authorization", bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("postId", post.getId(), "shippingQuoteId", quoteId, "requestId", requestId))));
    }
    private UUID order(User buyer, Post post, UUID quoteId, UUID requestId) throws Exception {
        return UUID.fromString(tree(checkout(buyer, post, quoteId, requestId).andExpect(status().isOk()).andReturn()).get("id").asText());
    }
    private Map<String, Object> shipmentRequest(UUID requestId) { return Map.of("requestId", requestId, "leg", "SELLER_TO_BUYER"); }
    private JsonNode shipment(User seller, UUID orderId, UUID requestId) throws Exception {
        return tree(mvc.perform(post("/api/v1/orders/" + orderId + "/shipments").header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON).content(body(shipmentRequest(requestId))))
                .andExpect(status().isOk()).andReturn()).get("data");
    }
    private JsonNode created(Map<String, Object> payload) {
        return json(Map.of("order_code", "GHN-" + payload.get("client_order_code"), "status", "ready_to_pick",
                "total_fee", FEE, "expected_delivery_time", Instant.now().plusSeconds(86400).toString()));
    }
    private Map<String, Object> callbackBody(String code, String status, Instant time) {
        return Map.of("ShopID", 123, "OrderCode", code, "Type", "switch_status", "Time", time.toString(), "Status", status);
    }
    private void callback(String code, String status, Instant time) throws Exception {
        mvc.perform(post("/api/v1/shipping/callback").header("X-GHN-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(body(callbackBody(code, status, time))))
                .andExpect(status().isOk());
    }
    private void assertOrder(UUID orderId, String status, String escrow) {
        assertEquals(status, jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId));
        assertEquals(escrow, jdbc.queryForObject("SELECT escrow_status FROM orders WHERE id = ?", String.class, orderId));
    }
    private String shipmentStatus(UUID shipmentId) { return jdbc.queryForObject("SELECT status FROM shipments WHERE id = ?", String.class, shipmentId); }
    private int eventCount(UUID shipmentId) { return jdbc.queryForObject("SELECT COUNT(*) FROM shipment_events WHERE shipment_id = ?", Integer.class, shipmentId); }
    private int transactions(UUID orderId, String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE reference_id = ? AND type = ?", Integer.class, orderId, type); }
    private BigDecimal transactionAmount(UUID orderId, String type) { return jdbc.queryForObject("SELECT SUM(amount) FROM wallet_transactions WHERE reference_id = ? AND type = ?", BigDecimal.class, orderId, type); }
    private BigDecimal balance(User user) { return wallets.findByUserId(user.getId()).orElseThrow().getBalance(); }
    private void assertMoney(BigDecimal expected, BigDecimal actual) { assertNotNull(actual); assertEquals(0, expected.compareTo(actual)); }
    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
    private String body(Object value) { return mapper.writeValueAsString(value); }
    private JsonNode json(Object value) { return mapper.valueToTree(value); }
    private JsonNode tree(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    private <T> List<T> parallel(Callable<T> first, Callable<T> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            Callable<T> a = () -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return first.call(); };
            Callable<T> b = () -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return second.call(); };
            var one = executor.submit(a); var two = executor.submit(b);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            return List.of(one.get(45, TimeUnit.SECONDS), two.get(45, TimeUnit.SECONDS));
        }
    }
}
