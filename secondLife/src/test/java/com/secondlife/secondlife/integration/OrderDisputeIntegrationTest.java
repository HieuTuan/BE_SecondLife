package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.CommissionRuleRequest;
import com.secondlife.secondlife.dto.request.ReportRequests;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingParcel;
import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.ShippingQuote;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.integration.ai.AiPriceProvider;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.integration.storage.ReportEvidenceStorage;
import com.secondlife.secondlife.mapper.ShippingPayloads;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.ShippingQuoteRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.CloudinaryService;
import com.secondlife.secondlife.service.CommissionService;
import com.secondlife.secondlife.service.OrderAutoReleaseService;
import com.secondlife.secondlife.service.OrderService;
import com.secondlife.secondlife.service.OrderDisputeService;
import com.secondlife.secondlife.service.ReportEvidenceService;
import com.secondlife.secondlife.service.ReturnShippingFeeService;
import com.secondlife.secondlife.service.ShipmentService;
import com.secondlife.secondlife.service.SystemWalletService;
import com.secondlife.secondlife.service.UserReportService;
import com.secondlife.secondlife.service.WalletService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.secondlife.secondlife.scheduler.OrderDisputeRefundScheduler;
import com.secondlife.secondlife.scheduler.ReturnShippingDebtScheduler;
import com.secondlife.secondlife.repository.ReturnShippingFeeRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false",
        "app.orders.auto-release.enabled=false", "app.orders.auto-release.delay-hours=48",
        "app.orders.dispute-refund.interval-ms=3600000", "app.orders.return-shipping-debt.enabled=false"
})
class OrderDisputeIntegrationTest {
    private static final GhnTestDatabase DATABASE = new GhnTestDatabase("ORDER_DISPUTE_TEST_JDBC_URL");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final BigDecimal FUNDS = new BigDecimal("5000000.00");
    private static final BigDecimal PRICE = new BigDecimal("1000000.00");
    private static final BigDecimal FEE = new BigDecimal("27000.00");
    private static final String REVIEW_REASON = "Evidence supports the staff decision";
    private static final String REFUND_REASON = "Carrier confirmed the product returned to the seller";

    static boolean databaseAvailable() { return GhnTestDatabase.available("ORDER_DISPUTE_TEST_JDBC_URL"); }
    @AfterAll static void closeDatabase() { DATABASE.close(); }
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PostRepository posts;
    @Autowired OrderRepository orders;
    @Autowired ShippingQuoteRepository quotes;
    @Autowired ShipmentRepository shipmentRows;
    @Autowired UserWalletRepository walletRows;
    @Autowired UserReportService reports;
    @Autowired ShipmentService shipments;
    @Autowired OrderDisputeRefundScheduler disputeRefunds;
    @Autowired ReturnShippingFeeRepository feeRows;
    @Autowired OrderService orderService;
    @Autowired OrderAutoReleaseService autoRelease;
    @Autowired SystemWalletService systemWallet;
    @Autowired CommissionService commissions;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean WalletService wallets;
    @MockitoSpyBean ReportEvidenceService evidence;
    @MockitoSpyBean OrderDisputeService disputes;
    @MockitoSpyBean ReturnShippingFeeService returnFees;
    @MockitoBean(name = "orderReleaseClock") Clock releaseClock;
    @MockitoBean ShippingProvider carrier;
    @MockitoBean AiPriceProvider priceProvider;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoBean ReportEvidenceStorage storage;
    @MockitoBean JavaMailSender mail;
    @MockitoBean SimpMessagingTemplate messages;
    @MockitoBean(name = "ollamaChatModel") ChatModel ollama;
    @MockitoBean(name = "googleGenAiChatModel") ChatModel google;
    private User reviewer;

    @BeforeEach void configureExternalServicesAndCommission() {
        when(releaseClock.instant()).thenReturn(NOW);
        when(mail.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        when(ollama.getOptions()).thenReturn(ChatOptions.builder().build());
        when(google.getOptions()).thenReturn(ChatOptions.builder().build());
        when(carrier.code()).thenReturn("LALAMOVE");
        when(carrier.forProvider(anyString())).thenReturn(carrier);
        when(carrier.preview(anyMap())).thenReturn(json.valueToTree(Map.of("total_fee", FEE,
                "provider_quote", Map.of("quotationId", "dispute-test-quote"))));
        when(carrier.create(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(0);
            return json.valueToTree(Map.of("order_code", "LALA-" + payload.get("client_order_code"),
                    "status", "ASSIGNING_DRIVER", "total_fee", FEE));
        });
        reviewer = account("STAFF");
        jdbc.update("UPDATE commission_rules SET active=false WHERE active");
        commissions.create(reviewer.getId(), new CommissionRuleRequest("Dispute test commission",
                new BigDecimal("0.05"), BigDecimal.ZERO, null, true, "Configure integration fixture"));
    }

    @Test void walletBalanceDetailsReconcileHeldProductAndNetShipping() throws Exception {
        var firstPage = org.springframework.data.domain.PageRequest.of(0, 1);
        var heldFund = com.secondlife.secondlife.dto.response.SystemWalletBalanceDetails.Fund.PRODUCT_ESCROW;
        var shippingFund = com.secondlife.secondlife.dto.response.SystemWalletBalanceDetails.Fund.SHIPPING;
        var heldBefore = systemWallet.getBalanceDetails(heldFund, firstPage);
        var shippingBefore = systemWallet.getBalanceDetails(shippingFund, firstPage);
        var kept = deliveredOrder(NOW.minusSeconds(60));
        var refunded = deliveredOrder(NOW.minusSeconds(60));
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                systemWallet.refund(refunded.order(), PRICE.add(FEE)));

        String path = "/api/v1/admin/system-wallet/balance-details";
        mvc.perform(get(path).param("fund", "PRODUCT_ESCROW").param("size", "100")
                        .header("Authorization", bearer(reviewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(heldBefore.balance().add(PRICE).doubleValue()))
                .andExpect(jsonPath("$.data.recordsTotal").value(heldBefore.recordsTotal().add(PRICE).doubleValue()))
                .andExpect(jsonPath("$.data.entries.totalElements").value(heldBefore.entries().totalElements() + 1))
                .andExpect(jsonPath("$.data.entries.items[*].orderId", org.hamcrest.Matchers.hasItem(kept.order().getId().toString())))
                .andExpect(jsonPath("$.data.entries.items[*].orderId",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(refunded.order().getId().toString()))));

        mvc.perform(get(path).param("fund", "SHIPPING").param("size", "1")
                        .header("Authorization", bearer(reviewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(shippingBefore.balance().add(FEE).doubleValue()))
                .andExpect(jsonPath("$.data.recordsTotal").value(shippingBefore.recordsTotal().add(FEE).doubleValue()))
                .andExpect(jsonPath("$.data.credited").value(shippingBefore.credited().add(FEE.multiply(BigDecimal.TWO)).doubleValue()))
                .andExpect(jsonPath("$.data.debited").value(shippingBefore.debited().add(FEE).doubleValue()))
                .andExpect(jsonPath("$.data.entries.totalElements").value(shippingBefore.entries().totalElements() + 2))
                .andExpect(jsonPath("$.data.entries.items[0].orderId").value(refunded.order().getId().toString()))
                .andExpect(jsonPath("$.data.entries.items[0].amount").value(0));
        mvc.perform(get(path).param("fund", "SHIPPING").header("Authorization", bearer(kept.buyer())))
                .andExpect(status().isForbidden());
    }

    @Test void buyerMustAttachEvidenceBeforeOpeningAnOrderDispute() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        var missingEvidence = new ReportRequests.Create(UUID.randomUUID(), null, fixture.order().getId(),
                ReportRequests.ReasonCode.OTHER, "The delivered product differs from the listing", List.of());
        mvc.perform(post("/api/v1/reports").header("Authorization", bearer(fixture.buyer()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(missingEvidence)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vui lòng đính kèm ít nhất 1 ảnh hoặc video bằng chứng để gửi khiếu nại."));
        assertOrder(fixture, "DELIVERED", "HELD");
        assertEquals(0, reportCount(fixture));

        var withEvidence = request(fixture, ReportRequests.ReasonCode.OTHER);
        mvc.perform(post("/api/v1/reports").header("Authorization", bearer(fixture.buyer()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(withEvidence)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evidenceUrls[0]").value(withEvidence.evidenceUrls().getFirst()));
        assertOrder(fixture, "DELIVERED", "FROZEN");
        assertEquals(1, reportCount(fixture));
    }

    @Test void reportBeforeDeadlineFreezesEscrowAndIdenticalReplaySurvivesDeadline() {
        Fixture fixture = deliveredOrder(NOW.minus(Duration.ofHours(48)).plusNanos(1000));
        var request = request(fixture, ReportRequests.ReasonCode.OTHER);
        BigDecimal buyerBefore = balance(fixture.buyer());
        BigDecimal heldBefore = systemWallet.getWallet().heldBalance();
        ReportResponse created = reports.create(fixture.buyer().getId(), request);
        assertEquals(ReportRequests.Status.OPEN, created.status());
        assertOrder(fixture, "DELIVERED", "FROZEN");
        when(releaseClock.instant()).thenReturn(NOW.plusSeconds(1));
        assertEquals(created.id(), reports.create(fixture.buyer().getId(), request).id());
        assertEquals(1, reportCount(fixture));
        money(buyerBefore, balance(fixture.buyer()));
        money(heldBefore, systemWallet.getWallet().heldBalance());
        assertFalse(autoRelease.releaseIfDue(fixture.order().getId()));
        assertThrows(ConflictException.class, () -> orderService.confirmDelivery(fixture.buyer().getId(), fixture.order().getId()));
        assertEquals(0, ledger(fixture, "EARNING"));
        assertEquals(0, ledger(fixture, "REFUND"));
    }

    @Test void approvalUsesAcceptedReportTimeWhenSharedClockIsAheadOfDatabaseTransactionTime() {
        Instant observedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant acceptedAt = observedAt.plusSeconds(20);
        Fixture fixture = deliveredOrder(observedAt.plusSeconds(10));
        when(releaseClock.instant()).thenReturn(acceptedAt);
        ReportResponse report = createReport(fixture, ReportRequests.ReasonCode.OTHER);
        assertEquals(acceptedAt, report.createdAt());
        ReportResponse approved = approve(report);
        assertEquals(ReportRequests.ResolutionAction.RETURN_AND_REFUND, approved.resolutionAction());
        assertEquals(acceptedAt, approved.returnApprovedAt());
        assertOrder(fixture, "DELIVERED", "FROZEN");
    }
    @ParameterizedTest @ValueSource(longs = {0, 1})
    void reportAtOrAfterDeadlineIsRejectedWithoutFreeze(long secondsAfterDeadline) {
        Fixture fixture = deliveredOrder(NOW.minus(Duration.ofHours(48)).minusSeconds(secondsAfterDeadline));
        assertThrows(ConflictException.class, () -> createReport(fixture, ReportRequests.ReasonCode.OTHER));
        assertOrder(fixture, "DELIVERED", "HELD");
        assertEquals(0, reportCount(fixture));
    }

    @ParameterizedTest
    @CsvSource({"PROCESSING,HELD,true", "SHIPPED,HELD,true", "DELIVERED,HELD,false",
            "COMPLETED,RELEASED,true", "DELIVERED,RELEASED,true", "CANCELLED,REFUNDED,true"})
    void undeliveredAndClosedOrdersCannotOpenDisputes(String status, String escrow, boolean deliveredAtPresent) {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        jdbc.update("UPDATE orders SET status=?,escrow_status=?,shipping_delivered_at=? WHERE id=?",
                status, escrow, deliveredAtPresent ? Timestamp.from(NOW.minusSeconds(60)) : null, fixture.order().getId());
        assertThrows(ConflictException.class, () -> createReport(fixture, ReportRequests.ReasonCode.OTHER));
        assertEquals(0, reportCount(fixture));
    }

    @Test void onlyTheOrderBuyerCanReportAndChangedReplayIsRejected() {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        var request = request(fixture, ReportRequests.ReasonCode.OTHER);
        assertThrows(ForbiddenException.class, () -> reports.create(account("BUYER").getId(), request));
        assertThrows(ForbiddenException.class, () -> reports.create(fixture.seller().getId(), request));
        reports.create(fixture.buyer().getId(), request);
        var changed = new ReportRequests.Create(request.requestId(), null, fixture.order().getId(),
                request.reasonCode(), "Different report details", request.evidenceUrls());
        assertThrows(ConflictException.class, () -> reports.create(fixture.buyer().getId(), changed));
        assertEquals(1, reportCount(fixture));
    }

    @Test void reportInsertAndFreezeRollBackTogetherWhenEvidenceBindingFails() {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        var request = request(fixture, ReportRequests.ReasonCode.OTHER);
        ReportEvidenceService evidenceTarget = AopTestUtils.getUltimateTargetObject(evidence);
        doThrow(new ConflictException("Evidence binding failed"))
                .when(evidenceTarget).bind(eq(fixture.buyer().getId()), any(UUID.class), eq(request.evidenceUrls()));
        assertThrows(ConflictException.class, () -> reports.create(fixture.buyer().getId(), request));
        assertEquals(0, reportCount(fixture));
        assertOrder(fixture, "DELIVERED", "HELD");
    }

    @Test void dismissingOneReportKeepsOtherHoldAndFinalDismissalAllowsDueSettlement() {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse first = createReport(fixture, ReportRequests.ReasonCode.OTHER);
        ReportResponse second = createReport(fixture, ReportRequests.ReasonCode.MISLEADING_DESCRIPTION);
        dismiss(first);
        assertOrder(fixture, "DELIVERED", "FROZEN");
        dismiss(second);
        assertOrder(fixture, "DELIVERED", "HELD");
        when(releaseClock.instant()).thenReturn(NOW.plus(Duration.ofHours(48)));
        assertTrue(autoRelease.releaseIfDue(fixture.order().getId()));
        assertFalse(autoRelease.releaseIfDue(fixture.order().getId()));
        assertOrder(fixture, "COMPLETED", "RELEASED");
        assertEquals(1, ledger(fixture, "EARNING"));
    }

    @Test void dismissingAnotherReportDoesNotReleaseApprovedReturnHold() {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse approved = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        dismiss(createReport(fixture, ReportRequests.ReasonCode.MISLEADING_DESCRIPTION));
        assertOrder(fixture, "DELIVERED", "FROZEN");
        assertEquals(ReportRequests.ResolutionAction.RETURN_AND_REFUND, approved.resolutionAction());
        assertNotNull(approved.returnApprovedAt());
        assertNull(approved.returnShipmentId());
        when(releaseClock.instant()).thenReturn(NOW.plus(Duration.ofHours(48)));
        assertFalse(autoRelease.releaseIfDue(fixture.order().getId()));
    }

    @Test void dismissalPreservesPhysicalShippingExceptionHold() {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse report = createReport(fixture, ReportRequests.ReasonCode.OTHER);
        jdbc.update("UPDATE shipments SET status='RETURNED' WHERE id=?", fixture.outbound().getId());
        dismiss(report);
        assertOrder(fixture, "DELIVERED", "FROZEN");
    }

    @Test void failedProofOfDeliveryPreservesHoldEvenWhenShipmentRemainsDelivered() {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse report = createReport(fixture, ReportRequests.ReasonCode.OTHER);
        shipments.receiveLalamove(json.valueToTree(Map.of("order_code", fixture.outbound().getOrderCode(),
                "event_id", UUID.randomUUID().toString(), "event_type", "ORDER_STATUS_CHANGED", "status", "COMPLETED",
                "occurred_at", NOW.toString(), "pod_status", "FAILED")));
        assertEquals("DELIVERED", shipmentRows.findById(fixture.outbound().getId()).orElseThrow().getStatus());
        dismiss(report);
        assertOrder(fixture, "DELIVERED", "FROZEN");
        assertTrue(jdbc.queryForObject("SELECT shipping_hold FROM orders WHERE id=?", Boolean.class, fixture.order().getId()));
        assertThrows(ConflictException.class, () -> orderService.confirmDelivery(fixture.buyer().getId(), fixture.order().getId()));
        when(releaseClock.instant()).thenReturn(NOW.plus(Duration.ofHours(48)));
        assertFalse(autoRelease.releaseIfDue(fixture.order().getId()));
    }
    @Test void assignedReviewAndDisputePermissionAreRequiredForReturnApproval() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse report = createReport(fixture, ReportRequests.ReasonCode.OTHER);
        startAfterSellerResponse(report);
        assertThrows(ForbiddenException.class, () -> reports.resolve(account("STAFF").getId(), report.id(), approval()));
        mvc.perform(post("/api/v1/staff/reports/" + report.id() + "/resolve")
                        .with(fraudOnlyReviewer())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(approval())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/staff/reports/" + report.id() + "/resolve")
                        .header("Authorization", bearer(reviewer)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(approval())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.resolutionAction").value("RETURN_AND_REFUND"))
                .andExpect(jsonPath("$.data.returnApprovedAt").exists());
        assertOrder(fixture, "DELIVERED", "FROZEN");
    }

    @Test void invalidDecisionsCannotAuthorizeAReturn() {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse report = createReport(fixture, ReportRequests.ReasonCode.OTHER);
        startAfterSellerResponse(report);
        assertThrows(BadRequestException.class, () -> reports.resolve(reviewer.getId(), report.id(),
                new ReportRequests.Resolve(ReportRequests.Decision.DISMISSED, ReportRequests.ResponsibleParty.NONE,
                        REVIEW_REASON, ReportRequests.ResolutionAction.RETURN_AND_REFUND)));
        assertThrows(BadRequestException.class, () -> reports.resolve(reviewer.getId(), report.id(),
                new ReportRequests.Resolve(ReportRequests.Decision.CONFIRMED, ReportRequests.ResponsibleParty.NONE,
                        REVIEW_REASON, ReportRequests.ResolutionAction.RETURN_AND_REFUND)));
        assertOrder(fixture, "DELIVERED", "FROZEN");
    }

    @Test void returnBookingRequiresPermissionAndApprovedReportForTheSameOrder() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse open = createReport(fixture, ReportRequests.ReasonCode.OTHER);
        returnBooking(fixture.buyer(), fixture, Map.of("requestId", UUID.randomUUID(), "leg", "BUYER_TO_SELLER"))
                .andExpect(status().isBadRequest());
        returnBooking(fixture.buyer(), fixture, returnRequest(open.id())).andExpect(status().isConflict());
        ReportResponse approved = approve(open);
        Fixture other = deliveredOrder(NOW.minusSeconds(60));
        returnBooking(other.buyer(), other, returnRequest(approved.id())).andExpect(status().isConflict());
        returnBooking(reviewer, fixture, returnRequest(approved.id())).andExpect(status().isForbidden());
        returnBooking(account("ADMIN"), fixture, returnRequest(approved.id())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders/" + fixture.order().getId() + "/shipments")
                        .with(fraudOnlyReviewer())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(returnRequest(approved.id()))))
                .andExpect(status().isForbidden());
        verify(carrier, never()).create(anyMap());
    }

    @Test void orderFeedShowsApprovedReturnForBothSidesAndOnlyBuyerCanBookIt() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        User stranger = account("BUYER");
        ReportResponse open = createReport(fixture, ReportRequests.ReasonCode.OTHER);
        Map<String, Object> booking = Map.of("requestId", UUID.randomUUID(), "leg", "BUYER_TO_SELLER", "reportId", open.id());
        returnBooking(fixture.buyer(), fixture, booking).andExpect(status().isConflict());
        approve(open);

        for (User actor : List.of(fixture.buyer(), fixture.seller())) {
            mvc.perform(get("/api/v1/orders/feed").param("orderId", fixture.order().getId().toString())
                            .header("Authorization", bearer(actor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.detail.order.escrowStatus").value("FROZEN"))
                    .andExpect(jsonPath("$.data.detail.orderReturn.reportId").value(open.id().toString()))
                    .andExpect(jsonPath("$.data.detail.orderReturn.shipmentId").doesNotExist());
        }
        mvc.perform(get("/api/v1/orders/feed").param("orderId", fixture.order().getId().toString())
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detail").doesNotExist());
        returnBooking(stranger, fixture, booking).andExpect(status().isForbidden());
        returnBooking(fixture.seller(), fixture, booking).andExpect(status().isForbidden());

        String response = returnBooking(fixture.buyer(), fixture, booking).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID shipmentId = UUID.fromString(json.readTree(response).path("data").path("shipmentId").asText());
        returnBooking(fixture.buyer(), fixture, booking).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipmentId").value(shipmentId.toString()));
        verify(carrier).create(anyMap());

        carrierDelivered(shipmentId);
        mvc.perform(get("/api/v1/orders/feed").param("orderId", fixture.order().getId().toString())
                        .header("Authorization", bearer(fixture.seller())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detail.orderReturn.shipmentId").value(shipmentId.toString()))
                .andExpect(jsonPath("$.data.detail.orderReturn.shipmentStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.data.detail.orderReturn.orderCode").isNotEmpty());
        mvc.perform(post("/api/v1/shipments/" + shipmentId + "/refund").header("Authorization", bearer(fixture.buyer()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reason", REFUND_REASON))))
                .andExpect(status().isForbidden());
        refund(shipmentId);
        mvc.perform(get("/api/v1/orders/feed").param("orderId", fixture.order().getId().toString())
                        .header("Authorization", bearer(fixture.buyer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detail.order.escrowStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data.detail.orderReturn.refundedAt").isNotEmpty());
    }

    @Test void approvalBindsExactlyOneReturnAndCarrierDeliveryIsRequiredBeforeFullRefund() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse approved = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        Map<String, Object> booking = returnRequest(approved.id());
        UUID shipmentId = bookReturn(fixture, booking);
        assertEquals(shipmentId, reports.get(fixture.buyer().getId(), false, approved.id()).returnShipmentId());
        assertEquals(shipmentId, bookReturn(fixture, booking));
        returnBooking(fixture.buyer(), fixture, returnRequest(approved.id())).andExpect(status().isConflict());
        assertThrows(ConflictException.class, () -> refund(shipmentId));
        assertEquals(0, ledger(fixture, "REFUND"));
        carrierDelivered(shipmentId);
        assertThrows(ConflictException.class, () -> shipments.refundReturned(reviewer.getId(), true, shipmentId,
                REFUND_REASON, ReportRequests.ResponsibleParty.CARRIER));
        mvc.perform(post("/api/v1/shipments/" + shipmentId + "/refund")
                        .with(fraudOnlyReviewer())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reason", REFUND_REASON))))
                .andExpect(status().isForbidden());
        refund(shipmentId);
        ReportResponse first = reports.get(fixture.buyer().getId(), false, approved.id());
        refund(shipmentId);
        ReportResponse replay = reports.get(fixture.buyer().getId(), false, approved.id());
        assertEquals(first.refundedAt(), replay.refundedAt());
        assertEquals(reviewer.getId(), replay.refundedBy());
        money(PRICE, replay.refundAmount());
        money(FUNDS.subtract(FEE), balance(fixture.buyer()));
        money(BigDecimal.ZERO, balance(fixture.seller()));
        assertOrder(fixture, "CANCELLED", "REFUNDED");
        assertEquals(1, ledger(fixture, "REFUND"));
        assertEquals(1, systemLedger(fixture, "REFUND"));
        assertEquals(0, ledger(fixture, "EARNING"));
        assertEquals(0, systemLedger(fixture, "SETTLEMENT"));
    }

    @Test void deliveredDisputeIsRefundedAutomaticallyOnceAndRetriesAfterRollback() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        BigDecimal sellerFunds = new BigDecimal("50000.00");
        jdbc.update("UPDATE user_wallets SET balance=? WHERE user_id=?", sellerFunds, fixture.seller().getId());
        ReportResponse approved = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        UUID shipmentId = bookReturn(fixture, returnRequest(approved.id()));
        BigDecimal buyerBefore = balance(fixture.buyer());

        disputeRefunds.refundDeliveredReturns();
        assertOrder(fixture, "DELIVERED", "FROZEN");
        assertEquals(0, ledger(fixture, "REFUND"));

        carrierDelivered(shipmentId);
        ReturnShippingFeeService feeTarget = AopTestUtils.getUltimateTargetObject(returnFees);
        doThrow(new ConflictException("Simulated refund failure")).doCallRealMethod().when(feeTarget)
                .recordForRefund(any(), argThat(shipment -> shipmentId.equals(shipment.getId())), any(), any());
        disputeRefunds.refundDeliveredReturns();
        assertOrder(fixture, "DELIVERED", "FROZEN");
        assertNull(reports.get(fixture.buyer().getId(), false, approved.id()).refundedAt());
        money(buyerBefore, balance(fixture.buyer()));
        assertEquals(0, systemLedger(fixture, "REFUND"));

        disputeRefunds.refundDeliveredReturns();
        carrierDelivered(shipmentId);
        disputeRefunds.refundDeliveredReturns();
        ReportResponse refunded = reports.get(fixture.buyer().getId(), false, approved.id());
        assertNotNull(refunded.refundedAt());
        assertNull(refunded.refundedBy());
        money(PRICE, refunded.refundAmount());
        money(buyerBefore.add(PRICE), balance(fixture.buyer()));
        money(sellerFunds.subtract(FEE), balance(fixture.seller()));
        assertOrder(fixture, "CANCELLED", "REFUNDED");
        assertEquals(1, ledger(fixture, "REFUND"));
        assertEquals(1, systemLedger(fixture, "REFUND"));
        assertEquals(1, feeLedger(shipmentId));
        assertFee(shipmentId, "PAID", FEE, FEE, BigDecimal.ZERO);
        assertNull(jdbc.queryForObject("SELECT assessed_by FROM return_shipping_fees WHERE id=?", UUID.class, shipmentId));
    }

    @Test void sellerFaultCollectsReturnShippingWithoutReducingBuyerProductRefund() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        BigDecimal sellerFunds = new BigDecimal("50000.00");
        jdbc.update("UPDATE user_wallets SET balance=? WHERE user_id=?", sellerFunds, fixture.seller().getId());
        ReportResponse approved = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        UUID shipmentId = bookReturn(fixture, returnRequest(approved.id()));
        carrierDelivered(shipmentId);
        BigDecimal buyerBefore = balance(fixture.buyer());
        refund(shipmentId);
        money(sellerFunds.subtract(FEE), balance(fixture.seller()));
        money(buyerBefore.add(PRICE), balance(fixture.buyer()));
        refund(shipmentId);
        money(sellerFunds.subtract(FEE), balance(fixture.seller()));
        assertFee(shipmentId, "PAID", FEE, FEE, BigDecimal.ZERO);
        assertEquals(1, feeLedger(shipmentId));
        assertEquals(1, systemLedger(fixture, "RETURN_SHIPPING_FEE"));
    }

    @ParameterizedTest @CsvSource({"10000,10000,17000", "0,0,27000", "27000,27000,0"})
    void sellerPaysAvailableBalanceAndOnlyShortfallBecomesDebt(String funds, String collected, String debt) throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        jdbc.update("UPDATE user_wallets SET balance=? WHERE user_id=?", new BigDecimal(funds), fixture.seller().getId());
        ReportResponse approved = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        UUID shipmentId = bookReturn(fixture, returnRequest(approved.id()));
        carrierDelivered(shipmentId);
        BigDecimal buyerBefore = balance(fixture.buyer());
        BigDecimal shippingBefore = systemWallet.getWallet().shippingBalance();
        refund(shipmentId);
        BigDecimal paid = new BigDecimal(collected);
        BigDecimal outstanding = new BigDecimal(debt);
        money(new BigDecimal(funds).subtract(paid), balance(fixture.seller()));
        money(buyerBefore.add(PRICE), balance(fixture.buyer()));
        money(shippingBefore.add(paid), systemWallet.getWallet().shippingBalance());
        assertFee(shipmentId, outstanding.signum() == 0 ? "PAID" : "OUTSTANDING", FEE, paid, outstanding);
        assertEquals(paid.signum() == 0 ? 0 : 1, feeLedger(shipmentId));
        assertEquals(paid.signum() == 0 ? 0 : 1, systemLedger(fixture, "RETURN_SHIPPING_FEE"));
        refund(shipmentId);
        money(new BigDecimal(funds).subtract(paid), balance(fixture.seller()));
        assertFee(shipmentId, outstanding.signum() == 0 ? "PAID" : "OUTSTANDING", FEE, paid, outstanding);
    }

    @Test void actualFeeIsSnapshottedOnceAndLaterCarrierChangesDoNotRepriceTheDebt() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        jdbc.update("UPDATE user_wallets SET balance=50000 WHERE user_id=?", fixture.seller().getId());
        UUID shipmentId = bookReturn(fixture, returnRequest(approve(createReport(fixture, ReportRequests.ReasonCode.OTHER)).id()));
        carrierDelivered(shipmentId);
        BigDecimal actual = new BigDecimal("31000.00");
        jdbc.update("UPDATE shipments SET actual_fee=? WHERE id=?", actual, shipmentId);
        refund(shipmentId);
        assertFee(shipmentId, "PAID", actual, actual, BigDecimal.ZERO);
        Shipment returned = shipmentRows.findById(shipmentId).orElseThrow();
        shipments.receiveLalamove(json.valueToTree(Map.of("order_code", returned.getOrderCode(),
                "event_id", UUID.randomUUID().toString(), "event_type", "ORDER_AMOUNT_CHANGED",
                "status", "COMPLETED", "occurred_at", NOW.plusSeconds(1).toString(), "fee", new BigDecimal("45000"))));
        refund(shipmentId);
        assertFee(shipmentId, "PAID", actual, actual, BigDecimal.ZERO);
        money(new BigDecimal("19000"), balance(fixture.seller()));
        assertEquals(1, feeLedger(shipmentId));
    }

    @Test void zeroActualFeeIsValidAndNeverFallsBackToTheQuotation() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        UUID shipmentId = bookReturn(fixture, returnRequest(approve(createReport(fixture, ReportRequests.ReasonCode.OTHER)).id()));
        carrierDelivered(shipmentId);
        jdbc.update("UPDATE shipments SET actual_fee=0 WHERE id=?", shipmentId);
        refund(shipmentId);
        assertFee(shipmentId, "PAID", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(0, feeLedger(shipmentId));
        assertEquals(0, systemLedger(fixture, "RETURN_SHIPPING_FEE"));
    }

    @Test void missingActualFeeDoesNotBlockBuyerRefundAndRetryAssessesOnlyThePendingFee() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        jdbc.update("UPDATE user_wallets SET balance=10000 WHERE user_id=?", fixture.seller().getId());
        ReportResponse approved = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        UUID shipmentId = bookReturn(fixture, returnRequest(approved.id()));
        carrierDelivered(shipmentId);
        jdbc.update("UPDATE shipments SET actual_fee=NULL WHERE id=?", shipmentId);
        BigDecimal buyerBefore = balance(fixture.buyer());
        refund(shipmentId);
        assertFee(shipmentId, "PENDING_FEE", null, BigDecimal.ZERO, BigDecimal.ZERO);
        money(buyerBefore.add(PRICE), balance(fixture.buyer()));
        money(new BigDecimal("10000"), balance(fixture.seller()));
        refund(shipmentId);
        assertFee(shipmentId, "PENDING_FEE", null, BigDecimal.ZERO, BigDecimal.ZERO);
        Shipment returned = shipmentRows.findById(shipmentId).orElseThrow();
        when(carrier.detail(returned.getOrderCode())).thenReturn(json.valueToTree(Map.of(
                "order_code", returned.getOrderCode(), "status", "COMPLETED", "total_fee", FEE)));
        shipments.sync(reviewer.getId(), true, shipmentId);
        refund(shipmentId);
        assertFee(shipmentId, "OUTSTANDING", FEE, new BigDecimal("10000"), new BigDecimal("17000"));
        money(buyerBefore.add(PRICE), balance(fixture.buyer()));
        assertEquals(1, ledger(fixture, "REFUND"));
        assertEquals(1, feeLedger(shipmentId));
    }

    @ParameterizedTest @ValueSource(strings = {"BUYER", "CARRIER"})
    void legacyNonSellerReturnDoesNotCreateSellerShippingFeeOrDebt(String party) throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        jdbc.update("UPDATE user_wallets SET balance=50000 WHERE user_id=?", fixture.seller().getId());
        ReportResponse report = createReport(fixture, ReportRequests.ReasonCode.OTHER);
        startAfterSellerResponse(report);
        assertThrows(BadRequestException.class, () -> reports.resolve(reviewer.getId(), report.id(),
                new ReportRequests.Resolve(ReportRequests.Decision.CONFIRMED, ReportRequests.ResponsibleParty.valueOf(party),
                        REVIEW_REASON, ReportRequests.ResolutionAction.RETURN_AND_REFUND)));

        var approved = reports.resolve(reviewer.getId(), report.id(), new ReportRequests.Resolve(
                ReportRequests.Decision.CONFIRMED, null, REVIEW_REASON, ReportRequests.ResolutionAction.RETURN_AND_REFUND));
        assertEquals(ReportRequests.ResponsibleParty.SELLER, approved.responsibleParty());
        // Previously approved returns can retain their historical responsibility through refund.
        jdbc.update("UPDATE user_reports SET responsible_party = ? WHERE id = ?", party, report.id());
        UUID shipmentId = bookReturn(fixture, returnRequest(report.id()));
        carrierDelivered(shipmentId);
        shipments.refundReturned(reviewer.getId(), true, shipmentId, REFUND_REASON, null);
        money(new BigDecimal("50000"), balance(fixture.seller()));
        assertEquals(0, feeCount(shipmentId));
        assertEquals(0, feeLedger(shipmentId));
        assertEquals(0, systemLedger(fixture, "RETURN_SHIPPING_FEE"));
        money(FUNDS.subtract(FEE), balance(fixture.buyer()));
    }

    @Test void feeReadApisExposeOwnDebtButDenyBuyerAndUnprivilegedStaff() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        UUID shipmentId = bookReturn(fixture, returnRequest(approve(createReport(fixture, ReportRequests.ReasonCode.OTHER)).id()));
        carrierDelivered(shipmentId);
        String detail = "/api/v1/shipments/" + shipmentId + "/return-shipping-fee";
        mvc.perform(get(detail).header("Authorization", bearer(fixture.buyer()))).andExpect(status().isForbidden());
        refund(shipmentId);
        mvc.perform(get(detail).header("Authorization", bearer(fixture.seller())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.debtAmount").value(27000));
        mvc.perform(get(detail).header("Authorization", bearer(reviewer))).andExpect(status().isOk());
        mvc.perform(get(detail).with(fraudOnlyReviewer())).andExpect(status().isForbidden());
        mvc.perform(get(detail).header("Authorization", bearer(fixture.buyer()))).andExpect(status().isForbidden());
        mvc.perform(get(detail).header("Authorization", bearer(account("SELLER")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/seller/return-shipping-fees/debt").header("Authorization", bearer(fixture.seller())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.outstandingDebt").value(27000));
        mvc.perform(get("/api/v1/seller/return-shipping-fees?outstandingOnly=true&page=0&size=20")
                        .header("Authorization", bearer(fixture.seller())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].shipmentId").value(shipmentId.toString()));
    }

    @Test void refundFailureAfterSellerChargeRollsBackCashDebtAndBuyerRefund() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        jdbc.update("UPDATE user_wallets SET balance=10000 WHERE user_id=?", fixture.seller().getId());
        ReportResponse approved = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        UUID shipmentId = bookReturn(fixture, returnRequest(approved.id()));
        carrierDelivered(shipmentId);
        BigDecimal buyerBefore = balance(fixture.buyer());
        BigDecimal shippingBefore = systemWallet.getWallet().shippingBalance();
        OrderDisputeService target = AopTestUtils.getUltimateTargetObject(disputes);
        doThrow(new ConflictException("Simulated report audit failure after seller charge"))
                .when(target).recordRefund(any(), eq(reviewer.getId()), eq(PRICE));
        assertThrows(ConflictException.class, () -> refund(shipmentId));
        assertOrder(fixture, "DELIVERED", "FROZEN");
        money(new BigDecimal("10000"), balance(fixture.seller()));
        money(buyerBefore, balance(fixture.buyer()));
        money(shippingBefore, systemWallet.getWallet().shippingBalance());
        assertEquals(0, feeCount(shipmentId));
        assertEquals(0, feeLedger(shipmentId));
        assertEquals(0, systemLedger(fixture, "RETURN_SHIPPING_FEE"));
        assertEquals(0, ledger(fixture, "REFUND"));
        doCallRealMethod().when(target).recordRefund(any(), eq(reviewer.getId()), eq(PRICE));
        refund(shipmentId);
        assertFee(shipmentId, "OUTSTANDING", FEE, new BigDecimal("10000"), new BigDecimal("17000"));
    }

    @Test void historicalRefundReplayNeverBackfillsASellerCharge() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        jdbc.update("UPDATE user_wallets SET balance=50000 WHERE user_id=?", fixture.seller().getId());
        UUID shipmentId = bookReturn(fixture, returnRequest(approve(createReport(fixture, ReportRequests.ReasonCode.OTHER)).id()));
        carrierDelivered(shipmentId);
        ReturnShippingFeeService target = AopTestUtils.getUltimateTargetObject(returnFees);
        doNothing().when(target).recordForRefund(any(), any(), any(), any());
        refund(shipmentId);
        doCallRealMethod().when(target).recordForRefund(any(), any(), any(), any());
        refund(shipmentId);
        assertEquals(0, feeCount(shipmentId));
        money(new BigDecimal("50000"), balance(fixture.seller()));
    }

    @Test void firstSellerWalletIsCreatedEmptyAndDebtRemainsVisibleAcrossMultipleReturns() throws Exception {
        Fixture first = deliveredOrder(NOW.minusSeconds(60));
        jdbc.update("DELETE FROM user_wallets WHERE user_id=?", first.seller().getId());
        UUID firstReturn = bookReturn(first, returnRequest(approve(createReport(first, ReportRequests.ReasonCode.OTHER)).id()));
        carrierDelivered(firstReturn);
        refund(firstReturn);
        assertFee(firstReturn, "OUTSTANDING", FEE, BigDecimal.ZERO, FEE);
        money(BigDecimal.ZERO, balance(first.seller()));

        wallets.deposit(first.seller().getId(), new BigDecimal("10000"), UUID.randomUUID());
        Fixture second = deliveredOrder(NOW.minusSeconds(60), first.seller());
        UUID secondReturn = bookReturn(second, returnRequest(approve(createReport(second, ReportRequests.ReasonCode.OTHER)).id()));
        carrierDelivered(secondReturn);
        refund(secondReturn);
        assertFee(firstReturn, "OUTSTANDING", FEE, BigDecimal.ZERO, FEE);
        assertFee(secondReturn, "OUTSTANDING", FEE, new BigDecimal("10000"), new BigDecimal("17000"));
        mvc.perform(get("/api/v1/seller/return-shipping-fees/debt").header("Authorization", bearer(first.seller())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.outstandingDebt").value(44000));
    }

    @Test void automaticDebtCollectionRecordsEachInstallmentAndStopsAtZero() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        UUID sellerId = fixture.seller().getId();
        jdbc.update("UPDATE user_wallets SET balance=10000 WHERE user_id=?", sellerId);
        var report = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        UUID shipmentId = bookReturn(fixture, returnRequest(report.id()));
        carrierDelivered(shipmentId);
        refund(shipmentId);
        assertFee(shipmentId, "OUTSTANDING", FEE, new BigDecimal("10000"), new BigDecimal("17000"));
        var assessedAt = feeRows.findById(shipmentId).orElseThrow().getAssessedAt();
        var collector = new ReturnShippingDebtScheduler(feeRows, returnFees);
        collector.collectOutstandingDebt();
        assertEquals(1, feeLedger(shipmentId));

        // Exercise both a normal deposit and a balance already present before the next scheduled run.
        wallets.deposit(sellerId, new BigDecimal("5000"), UUID.randomUUID());
        collector.collectOutstandingDebt();
        money(BigDecimal.ZERO, balance(fixture.seller()));
        assertFee(shipmentId, "OUTSTANDING", FEE, new BigDecimal("15000"), new BigDecimal("12000"));

        jdbc.update("UPDATE user_wallets SET balance=20000 WHERE user_id=?", sellerId);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); collector.collectOutstandingDebt(); return null; });
            var b = executor.submit(() -> { start.await(); returnFees.collectOutstanding(shipmentId); return null; });
            start.countDown();
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
        }
        collector.collectOutstandingDebt();
        refund(shipmentId);
        assertFee(shipmentId, "PAID", FEE, FEE, BigDecimal.ZERO);
        money(new BigDecimal("8000"), balance(fixture.seller()));
        money(BigDecimal.ZERO, returnFees.debt(sellerId).outstandingDebt());
        assertEquals(3, feeLedger(shipmentId));

        String deductionsPath = "/api/v1/seller/return-shipping-fees/deductions";
        mvc.perform(get(deductionsPath).param("size", "2").header("Authorization", bearer(fixture.seller())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.items[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].orderId").value(fixture.order().getId().toString()))
                .andExpect(jsonPath("$.data.items[0].deductedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].amount").value(12000))
                .andExpect(jsonPath("$.data.items[1].amount").value(5000));
        mvc.perform(get(deductionsPath).param("size", "2").param("page", "1")
                        .header("Authorization", bearer(fixture.seller())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].amount").value(10000));
        mvc.perform(get(deductionsPath).param("outstandingOnly", "true")
                        .header("Authorization", bearer(fixture.seller())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get(deductionsPath).header("Authorization", bearer(fixture.buyer())))
                .andExpect(status().isForbidden());
        mvc.perform(get(deductionsPath).header("Authorization", bearer(deliveredOrder(NOW.minusSeconds(60)).seller())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        assertEquals(3, systemLedger(fixture, "RETURN_SHIPPING_FEE"));
        assertEquals(1, ledger(fixture, "REFUND"));
        var fee = feeRows.findById(shipmentId).orElseThrow();
        assertEquals(assessedAt, fee.getAssessedAt());
        assertNotNull(fee.getLastCollectedAt());
        assertEquals(List.of(new BigDecimal("-10000.00"), new BigDecimal("-5000.00"), new BigDecimal("-12000.00")),
                jdbc.queryForList("""
                        SELECT amount FROM wallet_transactions
                        WHERE reference_id=? AND type='RETURN_SHIPPING_FEE' ORDER BY created_at, id
                        """, BigDecimal.class, shipmentId));
    }

    @Test void concurrentReturnsForOneSellerCannotSpendTheSameBalanceTwice() throws Exception {
        Fixture first = deliveredOrder(NOW.minusSeconds(60));
        Fixture second = deliveredOrder(NOW.minusSeconds(60), first.seller());
        jdbc.update("UPDATE user_wallets SET balance=40000 WHERE user_id=?", first.seller().getId());
        UUID firstReturn = bookReturn(first, returnRequest(approve(createReport(first, ReportRequests.ReasonCode.OTHER)).id()));
        UUID secondReturn = bookReturn(second, returnRequest(approve(createReport(second, ReportRequests.ReasonCode.OTHER)).id()));
        carrierDelivered(firstReturn); carrierDelivered(secondReturn);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return refund(firstReturn); });
            var b = executor.submit(() -> { start.await(); return refund(secondReturn); });
            start.countDown(); a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        }
        money(BigDecimal.ZERO, balance(first.seller()));
        money(new BigDecimal("40000"), jdbc.queryForObject(
                "SELECT SUM(collected_amount) FROM return_shipping_fees WHERE seller_id=?", BigDecimal.class, first.seller().getId()));
        money(new BigDecimal("14000"), jdbc.queryForObject(
                "SELECT SUM(debt_amount) FROM return_shipping_fees WHERE seller_id=?", BigDecimal.class, first.seller().getId()));
        money(FUNDS.subtract(FEE), balance(first.buyer()));
        money(FUNDS.subtract(FEE), balance(second.buyer()));
    }

    @Test void deliveredUnlinkedAndUnselectedReturnsCannotRefundAnApprovedReport() {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse approved = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        Shipment unlinked = shipment(fixture.order(), "BUYER_TO_SELLER", "DELIVERED");
        assertThrows(BadRequestException.class, () -> refund(unlinked.getId()));
        unlinked.setReportId(approved.id()); shipmentRows.saveAndFlush(unlinked);
        assertThrows(ConflictException.class, () -> refund(unlinked.getId()));
        assertOrder(fixture, "DELIVERED", "FROZEN");
        assertEquals(0, ledger(fixture, "REFUND"));
    }

    @Test void refundFailureRollsBackSystemLedgerOrderAndReportAuditThenRetrySucceeds() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse approved = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        UUID shipmentId = bookReturn(fixture, returnRequest(approved.id()));
        carrierDelivered(shipmentId);
        BigDecimal buyerBefore = balance(fixture.buyer());
        BigDecimal heldBefore = systemWallet.getWallet().heldBalance();
        WalletService walletTarget = AopTestUtils.getUltimateTargetObject(wallets);
        doThrow(new ConflictException("Simulated buyer wallet failure"))
                .when(walletTarget).processRefund(eq(fixture.buyer().getId()), eq(PRICE), eq(fixture.order().getId()));
        assertThrows(ConflictException.class, () -> refund(shipmentId));
        assertOrder(fixture, "DELIVERED", "FROZEN");
        money(buyerBefore, balance(fixture.buyer()));
        money(heldBefore, systemWallet.getWallet().heldBalance());
        assertEquals(0, ledger(fixture, "REFUND"));
        assertEquals(0, systemLedger(fixture, "REFUND"));
        ReportResponse failed = reports.get(fixture.buyer().getId(), false, approved.id());
        assertNull(failed.refundedAt()); assertNull(failed.refundedBy()); assertNull(failed.refundAmount());
        doCallRealMethod().when(walletTarget).processRefund(eq(fixture.buyer().getId()), eq(PRICE), eq(fixture.order().getId()));
        refund(shipmentId);
        assertOrder(fixture, "CANCELLED", "REFUNDED");
        assertEquals(1, ledger(fixture, "REFUND"));
        assertEquals(1, systemLedger(fixture, "REFUND"));
    }

    @Test void concurrentRefundRequestsCreditExactlyOnce() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        jdbc.update("UPDATE user_wallets SET balance=10000 WHERE user_id=?", fixture.seller().getId());
        ReportResponse approved = approve(createReport(fixture, ReportRequests.ReasonCode.OTHER));
        UUID shipmentId = bookReturn(fixture, returnRequest(approved.id()));
        carrierDelivered(shipmentId);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return refund(shipmentId); });
            var second = executor.submit(() -> { start.await(); return refund(shipmentId); });
            start.countDown();
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        }
        money(FUNDS.subtract(FEE), balance(fixture.buyer()));
        assertEquals(1, ledger(fixture, "REFUND"));
        assertEquals(1, systemLedger(fixture, "REFUND"));
        assertFee(shipmentId, "OUTSTANDING", FEE, new BigDecimal("10000"), new BigDecimal("17000"));
        money(BigDecimal.ZERO, balance(fixture.seller()));
        assertEquals(1, feeLedger(shipmentId));
        assertEquals(1, systemLedger(fixture, "RETURN_SHIPPING_FEE"));
        assertNotNull(reports.get(fixture.buyer().getId(), false, approved.id()).refundedAt());
    }

    @Test void disputeAndBuyerConfirmationRaceCannotBothFreezeAndSettle() throws Exception {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var dispute = executor.submit(() -> {
                start.await();
                try { createReport(fixture, ReportRequests.ReasonCode.OTHER); return true; }
                catch (ConflictException closed) { return false; }
            });
            var confirmation = executor.submit(() -> {
                start.await();
                try { orderService.confirmDelivery(fixture.buyer().getId(), fixture.order().getId()); return true; }
                catch (ConflictException frozen) { return false; }
            });
            start.countDown();
            assertTrue(dispute.get(20, TimeUnit.SECONDS) ^ confirmation.get(20, TimeUnit.SECONDS));
        }
        if (reportCount(fixture) == 1) {
            assertOrder(fixture, "DELIVERED", "FROZEN");
            assertEquals(0, ledger(fixture, "EARNING"));
        } else {
            assertOrder(fixture, "COMPLETED", "RELEASED");
            assertEquals(1, ledger(fixture, "EARNING"));
        }
    }

    @Test void confirmedWithoutReturnUnfreezesFundsWhenThereAreNoOtherHolds() {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        ReportResponse report = createReport(fixture, ReportRequests.ReasonCode.OTHER);
        startAfterSellerResponse(report);
        assertThrows(BadRequestException.class, () -> reports.resolve(reviewer.getId(), report.id(),
                new ReportRequests.Resolve(ReportRequests.Decision.CONFIRMED, ReportRequests.ResponsibleParty.SELLER, REVIEW_REASON)));
        ReportResponse resolved = reports.resolve(reviewer.getId(), report.id(), new ReportRequests.Resolve(
                ReportRequests.Decision.CONFIRMED, null, REVIEW_REASON));
        assertEquals(ReportRequests.ResolutionAction.NONE, resolved.resolutionAction());
        assertEquals(ReportRequests.ResponsibleParty.NONE, resolved.responsibleParty());
        assertEquals(resolved, reports.resolve(reviewer.getId(), report.id(), new ReportRequests.Resolve(
                ReportRequests.Decision.CONFIRMED, null, REVIEW_REASON)));
        assertOrder(fixture, "DELIVERED", "HELD");
    }

    @Test void legacyOutboundPhysicalReturnStillRefundsWithoutAnOrderReport() {
        Fixture fixture = deliveredOrder(NOW.minusSeconds(60));
        jdbc.update("UPDATE orders SET escrow_status='FROZEN' WHERE id=?", fixture.order().getId());
        jdbc.update("UPDATE shipments SET status='RETURNED' WHERE id=?", fixture.outbound().getId());
        refund(fixture.outbound().getId()); refund(fixture.outbound().getId());
        assertOrder(fixture, "CANCELLED", "REFUNDED");
        assertEquals(0, reportCount(fixture));
        assertEquals(1, ledger(fixture, "REFUND"));
        assertEquals(1, systemLedger(fixture, "REFUND"));
        money(FUNDS.subtract(FEE), balance(fixture.buyer()));
    }
    private Fixture deliveredOrder(Instant deliveredAt) {
        return deliveredOrder(deliveredAt, account("SELLER"));
    }
    private Fixture deliveredOrder(Instant deliveredAt, User seller) {
        User buyer = account("BUYER");
        Post listing = new Post();
        listing.setUser(seller); listing.setTitle("Disputed product " + UUID.randomUUID());
        listing.setStatus("SOLD"); listing.setPrice(PRICE); listing = posts.saveAndFlush(listing);
        ShippingAddress pickup = new ShippingAddress("Seller", "+84901234567", "Seller address", new BigDecimal("10.777"), new BigDecimal("106.695"));
        ShippingAddress delivery = new ShippingAddress("Buyer", "+84901234568", "Buyer address", new BigDecimal("10.781"), new BigDecimal("106.7"));
        ShippingQuote quote = new ShippingQuote();
        quote.setBuyerId(buyer.getId()); quote.setPostId(listing.getId());
        quote.setPayload(json.writeValueAsString(ShippingPayloads.create(pickup, delivery, new ShippingParcel(1000, 10, 10, 10), listing.getTitle(), PRICE)));
        quote.setDeliveryAddress(json.writeValueAsString(delivery)); quote.setSellerPickupAddress(json.writeValueAsString(pickup));
        quote.setFee(FEE); quote.setExpiresAt(NOW.plusSeconds(300)); quote = quotes.saveAndFlush(quote);
        Order order = new Order();
        order.setPost(listing); order.setSeller(seller); order.setBuyer(buyer); order.setFinalPrice(PRICE);
        order.setShippingFee(FEE); order.setShippingQuoteId(quote.getId()); order.setDeliveryAddress(quote.getDeliveryAddress());
        order.setPaidAt(deliveredAt.minusSeconds(3600)); order.setSellerConfirmedAt(deliveredAt.minusSeconds(1800));
        order.setStatus(OrderStatus.DELIVERED); order.setEscrowStatus(EscrowStatus.HELD); order.setShippingDeliveredAt(deliveredAt);
        Order saved = new TransactionTemplate(transactions).execute(tx -> {
            Order paid = orders.saveAndFlush(order);
            commissions.capture(paid, reviewer.getId(), "Create paid dispute fixture");
            systemWallet.hold(paid); wallets.processPayment(buyer.getId(), PRICE.add(FEE), paid.getId());
            return paid;
        });
        return new Fixture(seller, buyer, saved, shipment(saved, "SELLER_TO_BUYER", "DELIVERED"));
    }

    private Shipment shipment(Order order, String leg, String status) {
        Shipment shipment = new Shipment();
        shipment.setId(UUID.randomUUID()); shipment.setOrderId(order.getId()); shipment.setSellerId(order.getSeller().getId());
        shipment.setBuyerId(order.getBuyer().getId()); shipment.setLeg(leg); shipment.setRequestId(UUID.randomUUID());
        shipment.setClientOrderCode("SL" + shipment.getId()); shipment.setOrderCode("LALA-" + shipment.getId());
        shipment.setStatus(status); shipment.setProviderStatus("COMPLETED"); shipment.setPayload("{}");
        shipment.setDeliveredAt(order.getShippingDeliveredAt());
        return shipmentRows.saveAndFlush(shipment);
    }

    private User account(String role) {
        User account = new User("dispute-" + UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        account.addRole(roles.findByCodeWithPermissions(role).orElseThrow()); account = users.saveAndFlush(account);
        UserWallet wallet = new UserWallet(); wallet.setUser(account); wallet.setBalance("BUYER".equals(role) ? FUNDS : BigDecimal.ZERO);
        walletRows.saveAndFlush(wallet); return account;
    }

    private ReportRequests.Create request(Fixture fixture, ReportRequests.ReasonCode reason) {
        UUID evidenceId = UUID.randomUUID();
        jdbc.update("INSERT INTO report_evidence_uploads(id,owner_id,storage_id,content_type,size_bytes) VALUES (?,?,?,'image/png',3)",
                evidenceId, fixture.buyer().getId(), "order-dispute/" + evidenceId);
        return new ReportRequests.Create(UUID.randomUUID(), null, fixture.order().getId(), reason,
                "The delivered product differs from the listing", List.of("/api/v1/reports/evidence/" + evidenceId));
    }
    private ReportResponse createReport(Fixture fixture, ReportRequests.ReasonCode reason) {
        return reports.create(fixture.buyer().getId(), request(fixture, reason));
    }
    private ReportRequests.Resolve approval() {
        return new ReportRequests.Resolve(ReportRequests.Decision.CONFIRMED, ReportRequests.ResponsibleParty.SELLER,
                REVIEW_REASON, ReportRequests.ResolutionAction.RETURN_AND_REFUND);
    }
    private void startAfterSellerResponse(ReportResponse report) {
        jdbc.update("""
                INSERT INTO report_submissions(id, report_id, author_id, author_role, request_id, description, evidence_urls, created_at)
                VALUES (?, ?, ?, 'SELLER', ?, 'Seller submitted a response', '[]'::jsonb, ?)
                """, UUID.randomUUID(), report.id(), report.sellerId(), UUID.randomUUID(), java.sql.Timestamp.from(NOW));
        jdbc.update("UPDATE user_reports SET seller_responded_at = ? WHERE id = ?", java.sql.Timestamp.from(NOW), report.id());
        reports.startReview(reviewer.getId(), report.id());
    }

    private ReportResponse approve(ReportResponse report) {
        startAfterSellerResponse(report); return reports.resolve(reviewer.getId(), report.id(), approval());
    }
    private void dismiss(ReportResponse report) {
        startAfterSellerResponse(report);
        reports.resolve(reviewer.getId(), report.id(), new ReportRequests.Resolve(ReportRequests.Decision.DISMISSED,
                ReportRequests.ResponsibleParty.NONE, REVIEW_REASON));
    }
    private Map<String, Object> returnRequest(UUID reportId) {
        return Map.of("requestId", UUID.randomUUID(), "leg", "BUYER_TO_SELLER", "reportId", reportId);
    }
    private ResultActions returnBooking(User actor, Fixture fixture, Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/v1/orders/" + fixture.order().getId() + "/shipments")
                .header("Authorization", bearer(actor)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
    }
    private UUID bookReturn(Fixture fixture, Map<String, Object> request) throws Exception {
        String response = returnBooking(fixture.buyer(), fixture, request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).path("data").path("shipmentId").asText());
    }
    private void carrierDelivered(UUID shipmentId) {
        Shipment shipment = shipmentRows.findById(shipmentId).orElseThrow();
        shipments.receiveLalamove(json.valueToTree(Map.of("order_code", shipment.getOrderCode(), "event_id", UUID.randomUUID().toString(),
                "event_type", "ORDER_STATUS_CHANGED", "status", "COMPLETED", "occurred_at", NOW.toString(), "fee", FEE)));
    }
    private Object refund(UUID shipmentId) {
        return shipments.refundReturned(reviewer.getId(), true, shipmentId, REFUND_REASON, ReportRequests.ResponsibleParty.SELLER);
    }
    private void assertOrder(Fixture fixture, String status, String escrow) {
        assertEquals(status, jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, fixture.order().getId()));
        assertEquals(escrow, jdbc.queryForObject("SELECT escrow_status FROM orders WHERE id=?", String.class, fixture.order().getId()));
    }
    private int reportCount(Fixture fixture) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_reports WHERE order_id=?", Integer.class, fixture.order().getId());
    }
    private int ledger(Fixture fixture, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE reference_id=? AND type=?", Integer.class, fixture.order().getId(), type);
    }
    private int systemLedger(Fixture fixture, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM system_wallet_transactions WHERE order_id=? AND type=?", Integer.class, fixture.order().getId(), type);
    }
    private BigDecimal balance(User user) { return walletRows.findByUserId(user.getId()).orElseThrow().getBalance(); }
    private int feeCount(UUID shipmentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM return_shipping_fees WHERE id=?", Integer.class, shipmentId);
    }
    private int feeLedger(UUID shipmentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE reference_id=? AND type='RETURN_SHIPPING_FEE'",
                Integer.class, shipmentId);
    }
    private void assertFee(UUID shipmentId, String state, BigDecimal fee, BigDecimal collected, BigDecimal debt) {
        var row = jdbc.queryForMap("SELECT status,fee_amount,collected_amount,debt_amount FROM return_shipping_fees WHERE id=?", shipmentId);
        assertEquals(state, row.get("status"));
        if (fee == null) assertNull(row.get("fee_amount"));
        else money(fee, (BigDecimal) row.get("fee_amount"));
        money(collected, (BigDecimal) row.get("collected_amount"));
        money(debt, (BigDecimal) row.get("debt_amount"));
    }
    private RequestPostProcessor fraudOnlyReviewer() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new CustomUserDetails(reviewer), null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"), new SimpleGrantedAuthority("STAFF_FRAUD_REVIEW"))));
    }
    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
    private void money(BigDecimal expected, BigDecimal actual) { assertNotNull(actual); assertEquals(0, expected.compareTo(actual)); }
    private record Fixture(User seller, User buyer, Order order, Shipment outbound) { }
}
