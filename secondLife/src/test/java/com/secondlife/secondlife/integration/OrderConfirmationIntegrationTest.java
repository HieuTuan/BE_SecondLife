package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.scheduler.OrderConfirmationScheduler;

import com.secondlife.secondlife.integration.ai.AiPriceProvider;

import com.secondlife.secondlife.integration.storage.ReportEvidenceStorage;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.CloudinaryService;
import com.secondlife.secondlife.service.OrderConfirmationService;
import com.secondlife.secondlife.service.SellerReputationService;
import com.secondlife.secondlife.service.SystemWalletService;
import com.secondlife.secondlife.service.UserReportService;
import com.secondlife.secondlife.service.WalletService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIf("databaseAvailable")
@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(locations="classpath:application-test.properties",properties={
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class OrderConfirmationIntegrationTest {
    private static final GhnTestDatabase database=new GhnTestDatabase("CONFIRMATION_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("CONFIRMATION_TEST_JDBC_URL"); }
    @AfterAll static void closeDatabase() { database.close(); }
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",database::getJdbcUrl);
        r.add("spring.datasource.username",database::getUsername);
        r.add("spring.datasource.password",database::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PostRepository posts;
    @Autowired OrderRepository orders;
    @Autowired JwtTokenProvider jwt;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
    private static final Instant NOW=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    private static final BigDecimal FUNDS=new BigDecimal("2000000");
    @Autowired OrderConfirmationService confirmations;
    @Autowired SellerReputationService reputation;
    @Autowired UserReportService reports;
    @Autowired SystemWalletService systemWallet;
    @Autowired UserWalletRepository walletRows;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoSpyBean WalletService wallets;
    @MockitoBean(name="orderConfirmationClock") Clock clock;
    @MockitoBean org.springframework.mail.javamail.JavaMailSender mail;
    @MockitoBean org.springframework.messaging.simp.SimpMessagingTemplate messages;
    @BeforeEach void fixedClockAndMail() {
        when(clock.instant()).thenReturn(NOW);
        when(mail.createMimeMessage()).thenAnswer(i->new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    }
    @MockitoBean AiPriceProvider priceProvider;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoBean ReportEvidenceStorage evidenceStorage;
    @MockitoBean(name="ollamaChatModel") ChatModel ollama;
    @MockitoBean(name="googleGenAiChatModel") ChatModel google;

    @Test void onlyOwningSellerCanConfirmPaidOrderAndRetriesPreserveTimestamp() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var stranger=user("SELLER");
        var order=paidOrder(seller,buyer);
        String url="/api/v1/orders/"+order.getId()+"/confirm";
        mvc.perform(put(url).header("Authorization",bearer(buyer))).andExpect(status().isForbidden());
        mvc.perform(put(url).header("Authorization",bearer(stranger))).andExpect(status().isForbidden());
        var first=mvc.perform(put(url).header("Authorization",bearer(seller)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.sellerConfirmedAt").exists()).andReturn();
        var retry=mvc.perform(put(url).header("Authorization",bearer(seller)))
            .andExpect(status().isOk()).andReturn();
        assertEquals(mapper.readTree(first.getResponse().getContentAsString()).path("data").path("sellerConfirmedAt").asText(),
            mapper.readTree(retry.getResponse().getContentAsString()).path("data").path("sellerConfirmedAt").asText());
        assertEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order.getId()));
    }

    @Test void beforeDeadlineDoesNotDeductPointsOrCancel() {
        var seller=user("SELLER"); var buyer=user("BUYER"); goodSales(seller,buyer,5);
        var order=paidOrder(seller,buyer); when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(24)).minusSeconds(1));
        assertFalse(confirmations.processIfDue(order.getId()));
        money("100.00",reputation.get(seller.getId()).score()); assertEquals(0,penalties(order));
        assertEquals(0,notifications(order,"REPUTATION")); assertEquals(OrderStatus.PROCESSING,orders.findById(order.getId()).orElseThrow().getStatus());
    }
    @Test void firstDeadlineDeductsOnlyOnceAndLeavesOrderOpen() {
        var seller=user("SELLER"); var buyer=user("BUYER"); goodSales(seller,buyer,5);
        var order=paidOrder(seller,buyer); when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(24)));
        assertTrue(confirmations.processIfDue(order.getId())); assertFalse(confirmations.processIfDue(order.getId()));
        var r=reputation.get(seller.getId()); money("98.00",r.score()); money("100.00",r.transactionScore()); money("2.00",r.confirmationPenaltyPoints());
        assertEquals(5,r.goodTransactions()); assertEquals(0,r.badTransactions()); assertEquals(1,penalties(order));
        assertEquals(1,notifications(order,"REPUTATION"));
        assertEquals(OrderStatus.PROCESSING,orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(EscrowStatus.HELD,orders.findById(order.getId()).orElseThrow().getEscrowStatus());
        assertEquals(0,refunds(order));
    }
    @Test void lateConfirmationChargesFirstMilestoneOnceAndStopsCancellation() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); goodSales(seller,buyer,5);
        var order=paidOrder(seller,buyer); when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(25)));
        mvc.perform(put("/api/v1/orders/"+order.getId()+"/confirm").header("Authorization",bearer(seller))).andExpect(status().isOk());
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(60)));
        assertFalse(confirmations.processIfDue(order.getId()));
        mvc.perform(put("/api/v1/orders/"+order.getId()+"/confirm").header("Authorization",bearer(seller))).andExpect(status().isOk());
        money("98.00",reputation.get(seller.getId()).score()); assertEquals(1,penalties(order)); assertEquals(1,notifications(order,"REPUTATION"));
        assertEquals(0,refunds(order));
    }
    @Test void secondDeadlineCancelsRefundsAndCountsBadOnlyOnceEvenIfFirstScanWasMissed() {
        var seller=user("SELLER"); var buyer=user("BUYER"); goodSales(seller,buyer,5);
        var order=paidOrder(seller,buyer); when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(48)));
        assertTrue(confirmations.processIfDue(order.getId())); assertFalse(confirmations.processIfDue(order.getId()));
        var r=reputation.get(seller.getId()); money("78.33",r.score()); money("83.33",r.transactionScore()); money("5.00",r.confirmationPenaltyPoints());
        assertEquals(5,r.goodTransactions()); assertEquals(1,r.badTransactions()); assertEquals(1,r.nonFulfillmentOrders());
        assertEquals(2,penalties(order)); assertEquals(2,notifications(order,"REPUTATION")); assertEquals(1,refunds(order));
        assertEquals(OrderStatus.CANCELLED,orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(EscrowStatus.REFUNDED,orders.findById(order.getId()).orElseThrow().getEscrowStatus());
        assertEquals("SELLER_CONFIRMATION_TIMEOUT",orders.findById(order.getId()).orElseThrow().getCancellationReasonCode());
        assertEquals("ACTIVE",posts.findById(order.getPost().getId()).orElseThrow().getStatus());
        money(FUNDS.toPlainString(),balance(buyer));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE order_confirmation_penalties SET points=99 WHERE order_id=?",order.getId()));
    }
    @Test void confirmationAtCancellationDeadlineIsRejectedBeforeAnyMoneyChanges() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var order=paidOrder(seller,buyer);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(48)));
        mvc.perform(put("/api/v1/orders/"+order.getId()+"/confirm").header("Authorization",bearer(seller))).andExpect(status().isConflict());
        assertEquals(0,refunds(order)); assertEquals(0,penalties(order));
    }
    @Test void failedRefundRollsBackOrderSystemFundsPenaltyAndNotificationThenRetryWorks() {
        var seller=user("SELLER"); var buyer=user("BUYER"); var order=paidOrder(seller,buyer);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(49)));
        var held=systemWallet.getWallet().heldBalance();
        doThrow(new com.secondlife.secondlife.exception.ConflictException("Temporary refund failure"))
            .when(wallets).processRefund(eq(buyer.getId()),any(BigDecimal.class),eq(order.getId()));
        assertThrows(com.secondlife.secondlife.exception.ConflictException.class,()->confirmations.processIfDue(order.getId()));
        assertEquals(OrderStatus.PROCESSING,orders.findById(order.getId()).orElseThrow().getStatus());
        money(held.toPlainString(),systemWallet.getWallet().heldBalance()); assertEquals(0,penalties(order)); assertEquals(0,refunds(order));
        assertEquals(0,notifications(order,"REPUTATION"));
        doCallRealMethod().when(wallets).processRefund(eq(buyer.getId()),any(BigDecimal.class),eq(order.getId()));
        assertTrue(confirmations.processIfDue(order.getId())); money(FUNDS.toPlainString(),balance(buyer));
    }
    @Test void frozenShippedAndHistoricalOrdersAreNeverAutomaticallyPenalized() {
        var seller=user("SELLER"); var buyer=user("BUYER"); var order=paidOrder(seller,buyer);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(100)));
        jdbc.update("UPDATE orders SET escrow_status='FROZEN' WHERE id=?",order.getId()); assertFalse(confirmations.processIfDue(order.getId()));
        jdbc.update("UPDATE orders SET escrow_status='HELD',status='SHIPPED' WHERE id=?",order.getId()); assertFalse(confirmations.processIfDue(order.getId()));
        jdbc.update("UPDATE orders SET status='PROCESSING',confirmation_deadline=NULL,confirmation_cancel_at=NULL,confirmation_late_penalty=NULL,confirmation_timeout_penalty=NULL WHERE id=?",order.getId());
        assertFalse(confirmations.processIfDue(order.getId())); assertEquals(0,refunds(order)); assertEquals(0,penalties(order));
    }
    @Test void activeCarrierBookingBlocksAutomaticRefundAndReputationPenalty() {
        var seller=user("SELLER"); var buyer=user("BUYER"); var order=paidOrder(seller,buyer);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(49)));
        var shipment=new Shipment(); shipment.setId(UUID.randomUUID()); shipment.setOrderId(order.getId());
        shipment.setSellerId(seller.getId()); shipment.setBuyerId(buyer.getId()); shipment.setRequestId(UUID.randomUUID());
        shipment.setLeg("SELLER_TO_BUYER"); shipment.setClientOrderCode("SL"+shipment.getId()); shipment.setOrderCode("carrier-"+shipment.getId());
        shipment.setStatus("READY_TO_PICK"); shipment.setPayload("{}");
        new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status-> {
            jdbc.update("INSERT INTO shipments(id,order_id,seller_id,buyer_id,request_id,leg,client_order_code,order_code,status,payload,provider,created_at,updated_at) VALUES (?,?,?,?,?,'SELLER_TO_BUYER',?,?,'READY_TO_PICK','{}','LALAMOVE',now(),now())",
                shipment.getId(),order.getId(),seller.getId(),buyer.getId(),shipment.getRequestId(),shipment.getClientOrderCode(),shipment.getOrderCode()); return null;
        });
        assertFalse(confirmations.processIfDue(order.getId())); assertEquals(0,refunds(order)); assertEquals(0,penalties(order));
    }
    @Test void concurrentWorkersCannotDoubleRefundOrDoubleDeduct() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var order=paidOrder(seller,buyer);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(50)));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();return confirmations.processIfDue(order.getId());});
            var b=pool.submit(()->{start.await();return confirmations.processIfDue(order.getId());}); start.countDown();
            assertEquals(1,(a.get(15,java.util.concurrent.TimeUnit.SECONDS)?1:0)+(b.get(15,java.util.concurrent.TimeUnit.SECONDS)?1:0));
        } finally { pool.shutdownNow(); }
        assertEquals(2,penalties(order)); assertEquals(1,refunds(order)); assertEquals(1,reputation.get(seller.getId()).badTransactions());
        money(FUNDS.toPlainString(),balance(buyer));
    }
    @Test void scoreCannotBecomeNegativeAndNewSellerKeepsNoTransactionsAssessment() {
        var seller=user("SELLER"); var buyer=user("BUYER"); var order=paidOrder(seller,buyer);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(24)));
        assertTrue(confirmations.processIfDue(order.getId())); var provisional=reputation.get(seller.getId());
        assertNull(provisional.score()); assertEquals("NO_TRANSACTIONS",provisional.assessmentStatus()); money("2.00",provisional.confirmationPenaltyPoints());
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(48))); assertTrue(confirmations.processIfDue(order.getId()));
        money("0.00",reputation.get(seller.getId()).score());
    }
    @Test void staffCanMonitorButCannotConfirmAndBuyerCannotReadStaffQueue() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF"); var stranger=user("BUYER"); var order=paidOrder(seller,buyer);
        String url="/api/v1/orders/"+order.getId();
        mvc.perform(put(url+"/confirm").header("Authorization",bearer(staff))).andExpect(status().isForbidden());
        mvc.perform(get(url+"/confirmation").header("Authorization",bearer(staff))).andExpect(status().isOk());
        mvc.perform(get(url+"/confirmation").header("Authorization",bearer(stranger))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/staff/orders?status=PROCESSING&size=100").header("Authorization",bearer(staff))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/staff/orders").header("Authorization",bearer(buyer))).andExpect(status().isForbidden());
    }

    @Test void manualSellerCancellationAfterFirstDeadlineCannotAvoidEarnedLatePenalty() throws Exception {
        var seller=user("SELLER");var buyer=user("BUYER");goodSales(seller,buyer,5);var o=paidOrder(seller,buyer);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(25)));
        mvc.perform(put("/api/v1/orders/"+o.getId()+"/cancel").header("Authorization",bearer(seller))
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SELLER_OUT_OF_STOCK\",\"reason\":\"Cannot fulfill order\"}"))
            .andExpect(status().isOk());
        assertEquals(1,penalties(o));money("81.33",reputation.get(seller.getId()).score());
        assertEquals(1,reputation.get(seller.getId()).badTransactions());assertEquals(1,refunds(o));
    }
    @Test void buyerCancellationAfterSecondDeadlineCannotHideSellerTimeoutWhenSchedulerHasNotRun() throws Exception {
        var seller=user("SELLER");var buyer=user("BUYER");goodSales(seller,buyer,5);var o=paidOrder(seller,buyer);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(49)));
        mvc.perform(put("/api/v1/orders/"+o.getId()+"/cancel").header("Authorization",bearer(buyer))).andExpect(status().isOk());
        assertEquals(2,penalties(o));assertEquals(1,reputation.get(seller.getId()).badTransactions());
        assertEquals("SELLER_CONFIRMATION_TIMEOUT",orders.findById(o.getId()).orElseThrow().getCancellationReasonCode());
        assertEquals(1,refunds(o));money(FUNDS.toPlainString(),balance(buyer));
    }
    @Test void rolledBackTimeoutDoesNotPublishNotificationsOrEmails() {
        var seller=user("SELLER");var buyer=user("BUYER");var o=paidOrder(seller,buyer);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(49)));
        clearInvocations(messages,mail);
        String suffix=UUID.randomUUID().toString().replace("-","");
        String function="test_reject_timeout_"+suffix,trigger="test_timeout_notif_"+suffix;
        jdbc.execute("CREATE FUNCTION "+function+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.reference_id='"+o.getId()+"'::uuid AND NEW.type='REPUTATION' AND position('3.00' in NEW.message)>0 THEN RAISE EXCEPTION 'Simulated notification storage failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON notifications FOR EACH ROW EXECUTE FUNCTION "+function+"()");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,()->confirmations.processIfDue(o.getId()));
            assertEquals(0,penalties(o));assertEquals(0,refunds(o));assertEquals(0,notifications(o,"REPUTATION"));
            verifyNoInteractions(messages);verify(mail,never()).send(any(jakarta.mail.internet.MimeMessage.class));
        } finally { jdbc.execute("DROP TRIGGER "+trigger+" ON notifications");jdbc.execute("DROP FUNCTION "+function+"()"); }
    }
    @Test void schedulerContinuesPastFailedOrderAcrossBatches() {
        var failedSeller=user("SELLER");var failedBuyer=user("BUYER");var failed=paidOrder(failedSeller,failedBuyer);
        var healthySeller=user("SELLER");var healthyBuyer=user("BUYER");var healthy=paidOrder(healthySeller,healthyBuyer);
        jdbc.update("UPDATE orders SET confirmation_deadline=confirmation_deadline-interval '1 second' WHERE id=?",failed.getId());
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(49)));
        doThrow(new com.secondlife.secondlife.exception.ConflictException("Refund temporarily unavailable"))
            .when(wallets).processRefund(eq(failedBuyer.getId()),any(BigDecimal.class),eq(failed.getId()));
        var policy=new com.secondlife.secondlife.config.OrderConfirmationProperties(false,24,48,new BigDecimal("2"),new BigDecimal("3"),60000,1);
        new com.secondlife.secondlife.scheduler.OrderConfirmationScheduler(orders,confirmations,policy).processDueOrders();
        assertEquals(OrderStatus.PROCESSING,orders.findById(failed.getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.CANCELLED,orders.findById(healthy.getId()).orElseThrow().getStatus());
        assertEquals(1,refunds(healthy));assertEquals(0,refunds(failed));
    }
    @Test void snapshotDeadlinesAndPointsAreUsedInsteadOfNewGlobalPolicy() {
        var seller=user("SELLER");var buyer=user("BUYER");goodSales(seller,buyer,5);var o=paidOrder(seller,buyer);
        jdbc.update("UPDATE orders SET confirmation_deadline=?,confirmation_cancel_at=?,confirmation_late_penalty=7,confirmation_timeout_penalty=11 WHERE id=?",
            java.sql.Timestamp.from(NOW.plus(Duration.ofHours(4))),java.sql.Timestamp.from(NOW.plus(Duration.ofHours(8))),o.getId());
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(4)));assertTrue(confirmations.processIfDue(o.getId()));
        money("93.00",reputation.get(seller.getId()).score());
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(8)));assertTrue(confirmations.processIfDue(o.getId()));
        money("18.00",reputation.get(seller.getId()).confirmationPenaltyPoints());money("65.33",reputation.get(seller.getId()).score());
    }
    @Test void listingReportsByDifferentBuyersWarnButNeverCreateBadTransactions() {
        var seller=user("SELLER");var buyer=user("BUYER");var other=user("BUYER");var staff=user("STAFF");goodSales(seller,buyer,5);
        var o=paidOrder(seller,buyer);
        var first=reports.create(buyer.getId(),new com.secondlife.secondlife.dto.request.ReportRequests.Create(UUID.randomUUID(),o.getPost().getId(),null,
            com.secondlife.secondlife.dto.request.ReportRequests.ReasonCode.MISLEADING_DESCRIPTION,"Listing contains misleading details",List.of()));
        reports.startReview(staff.getId(),first.id());reports.resolve(staff.getId(),first.id(),sellerFault());
        var second=reports.create(other.getId(),new com.secondlife.secondlife.dto.request.ReportRequests.Create(UUID.randomUUID(),o.getPost().getId(),null,
            com.secondlife.secondlife.dto.request.ReportRequests.ReasonCode.OTHER,"Other confirmed listing violation",List.of()));
        reports.startReview(staff.getId(),second.id());reports.resolve(staff.getId(),second.id(),sellerFault());
        var r=reputation.get(seller.getId());assertEquals(5,r.goodTransactions());assertEquals(0,r.badTransactions());
        assertEquals(2,r.confirmedIncidents());assertEquals("CONFIRMED_VIOLATION_HISTORY",r.warningCode());money("100.00",r.score());
    }
    @Test void pendingOrderReportAndTimeoutStillCreateOnlyOneBadOutcome() {
        var seller=user("SELLER");var buyer=user("BUYER");var staff=user("STAFF");goodSales(seller,buyer,5);var o=paidOrder(seller,buyer);
        var report=reports.create(buyer.getId(),new com.secondlife.secondlife.dto.request.ReportRequests.Create(UUID.randomUUID(),null,o.getId(),
            com.secondlife.secondlife.dto.request.ReportRequests.ReasonCode.MISLEADING_DESCRIPTION,"Seller acknowledged misleading description",List.of()));
        reports.startReview(staff.getId(),report.id());reports.resolve(staff.getId(),report.id(),sellerFault());
        assertEquals(0,reputation.get(seller.getId()).badTransactions());money("100.00",reputation.get(seller.getId()).score());
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(48)));assertTrue(confirmations.processIfDue(o.getId()));
        reports.resolve(staff.getId(),report.id(),sellerFault());
        assertEquals(1,reputation.get(seller.getId()).badTransactions());money("78.33",reputation.get(seller.getId()).score());
    }
    private com.secondlife.secondlife.dto.request.ReportRequests.Resolve sellerFault() {
        return new com.secondlife.secondlife.dto.request.ReportRequests.Resolve(
            com.secondlife.secondlife.dto.request.ReportRequests.Decision.CONFIRMED,
            com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty.SELLER,"Staff confirmed seller responsibility");
    }

    @Test void auditEventsWithSameTimestampRemainInCausalOrderRatherThanRandomUuidOrder() {
        var seller=user("SELLER");String prefix=UUID.randomUUID().toString().substring(0,24);
        String[] types={"ORDER_CONFIRMATION_LATE","ORDER_CONFIRMATION_TIMEOUT","SELLER_CANCELLED_ORDER"};
        for(int i=0;i<3;i++) {
            UUID id=UUID.fromString(prefix+String.format("%012d",3-i));
            jdbc.update("""
                INSERT INTO seller_reputation_events(id,seller_id,event_type,reason,good_before,bad_before,good_after,bad_after,created_at,formula_version)
                VALUES (?,?,?,'Ordered audit fixture',0,0,0,0,?,'TRANSACTION_SUCCESS_RATE_V3')
                """,id,seller.getId(),types[i],java.sql.Timestamp.from(NOW));
        }
        var history=reputation.history(seller.getId(),org.springframework.data.domain.PageRequest.of(0,3)).items();
        assertEquals(List.of("SELLER_CANCELLED_ORDER","ORDER_CONFIRMATION_TIMEOUT","ORDER_CONFIRMATION_LATE"),
            history.stream().map(x->x.eventType()).toList());
    }

    private User user(String role) {
        var user=new User("confirmation-"+UUID.randomUUID()+"@example.test","test-hash",AccountStatus.ACTIVE);
        user.addRole(roles.findByCodeWithPermissions(role).orElseThrow()); user=users.saveAndFlush(user);
        var wallet=new UserWallet(); wallet.setUser(user); wallet.setBalance("BUYER".equals(role)?FUNDS:BigDecimal.ZERO);
        walletRows.saveAndFlush(wallet); return user;
    }
    private Order paidOrder(User seller,User buyer) {
        var post=new Post(); post.setUser(seller); post.setTitle("Confirmation product"); post.setStatus("SOLD"); post.setPrice(new BigDecimal("1000000"));
        post=posts.saveAndFlush(post);
        var order=new Order(); order.setPost(post); order.setSeller(seller); order.setBuyer(buyer);
        order.setFinalPrice(post.getPrice()); order.setShippingFee(new BigDecimal("27000"));
        order.setStatus(OrderStatus.PROCESSING); order.setEscrowStatus(EscrowStatus.HELD); order.setPaidAt(NOW);
        order.setConfirmationDeadline(NOW.plus(Duration.ofHours(24))); order.setConfirmationCancelAt(NOW.plus(Duration.ofHours(48)));
        order.setConfirmationLatePenalty(new BigDecimal("2"));order.setConfirmationTimeoutPenalty(new BigDecimal("3"));
        return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status-> {
            var saved=orders.saveAndFlush(order); systemWallet.hold(saved);
            wallets.processPayment(buyer.getId(),saved.getFinalPrice().add(saved.getShippingFee()),saved.getId()); return saved;
        });
    }
    private void goodSales(User seller,User buyer,int count) {
        for(int i=0;i<count;i++) {
            var post=new Post();post.setUser(seller);post.setTitle("Completed product");post.setStatus("SOLD");post=posts.saveAndFlush(post);
            var o=new Order();o.setPost(post);o.setSeller(seller);o.setBuyer(buyer);o.setFinalPrice(new BigDecimal("1000000"));
            o.setStatus(OrderStatus.COMPLETED);o.setEscrowStatus(EscrowStatus.RELEASED);o=orders.saveAndFlush(o);
            final var completed=o;
            new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{reputation.recordCompletedSale(completed);return null;});
        }
    }
    private int penalties(Order o) { return jdbc.queryForObject("SELECT COUNT(*) FROM order_confirmation_penalties WHERE order_id=?",Integer.class,o.getId()); }
    private int notifications(Order o,String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE reference_id=? AND type=?",Integer.class,o.getId(),type); }
    private int refunds(Order o) { return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE reference_id=? AND type='REFUND'",Integer.class,o.getId()); }
    private BigDecimal balance(User u) { return walletRows.findByUserId(u.getId()).orElseThrow().getBalance(); }
    private void money(String expected,BigDecimal actual) { assertNotNull(actual); assertEquals(0,new BigDecimal(expected).compareTo(actual)); }
    private String bearer(User user) { return "Bearer "+jwt.generateAccessToken(user); }
}
