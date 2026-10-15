package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
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
class OrderBankTransferIntegrationTest {
    private static final GhnTestDatabase database=new GhnTestDatabase("BANK_PAYMENT_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("BANK_PAYMENT_TEST_JDBC_URL"); }
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
    @Autowired ShippingQuoteRepository quotes;
    @Autowired UserWalletRepository wallets;
    @Autowired JwtTokenProvider jwt;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
    @MockitoBean org.springframework.mail.javamail.JavaMailSender mail;
    @MockitoBean org.springframework.messaging.simp.SimpMessagingTemplate messages;
    @MockitoBean AiPriceProvider priceProvider;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoBean(name="ollamaChatModel") ChatModel ollama;
    @MockitoBean(name="googleGenAiChatModel") ChatModel google;
    @Autowired com.secondlife.secondlife.service.payment.OrderBankPaymentService bankPayments;
    @Autowired com.secondlife.secondlife.service.payment.OrderBankPaymentScheduler bankScheduler;
    @Autowired OrderRepository orders;
    @MockitoBean(name="bankPaymentClock") Clock clock;
    private static final Instant NOW=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    private static final AtomicLong EVENT_ID=new AtomicLong(System.currentTimeMillis());
    @BeforeEach void policy() {
        when(clock.instant()).thenReturn(NOW);
        when(mail.createMimeMessage()).thenAnswer(i->new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
        jdbc.update("UPDATE commission_rules SET active=false WHERE active");
        var admin=user("ADMIN");
        jdbc.update("INSERT INTO commission_rules(id,name,type,rate,min_commission,active,revision,updated_by,created_at,updated_at) VALUES (?, 'Bank test', 'DEFAULT',0.05,0,true,1,?,now(),now())",UUID.randomUUID(),admin.getId());
    }
    @Test void emptyWalletBuyerCanCreatePendingTransferWithoutEscrowOrSellerDeadline() throws Exception {
        var f=fixture(); var order=checkout(f,UUID.randomUUID());
        assertEquals("PENDING_PAYMENT",order.path("status").asText());
        assertEquals("PENDING",order.path("escrowStatus").asText());
        assertEquals("BANK_TRANSFER",order.path("paymentMethod").asText());
        assertEquals(0,order.path("totalPaid").decimalValue().signum());
        assertFalse(order.path("payment").path("transferContent").asText().isBlank());
        assertEquals("PAYMENT_PENDING",posts.findById(f.post.getId()).orElseThrow().getStatus());
        UUID id=UUID.fromString(order.path("id").asText());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM system_order_funds WHERE order_id=?",Integer.class,id));
        assertNull(jdbc.queryForObject("SELECT confirmation_deadline FROM orders WHERE id=?",Object.class,id));
        assertEquals(0,balance(f.buyer).signum());
    }
    @Test void fullTransferFundsEscrowAndStartsSellerDeadlineWithoutDebitingBuyerWallet() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());var id=id(o);
        assertEquals(0,count("notifications","reference_id",id));
        event(o,1027000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());
        var paid=orders.findById(id).orElseThrow();assertEquals(OrderStatus.PROCESSING,paid.getStatus());
        assertEquals(EscrowStatus.HELD,paid.getEscrowStatus());assertNotNull(paid.getPaidAt());
        assertEquals(paid.getPaidAt().plus(Duration.ofHours(24)),paid.getConfirmationDeadline());
        assertEquals("SOLD",posts.findById(f.post.getId()).orElseThrow().getStatus());
        assertEquals(1,count("system_order_funds","order_id",id));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM system_wallet_transactions WHERE order_id=? AND type='PAYMENT_HOLD'",Integer.class,id));
        assertEquals(0,count("wallet_transactions","reference_id",id));money("0",balance(f.buyer));
        var details=payment(f.buyer,id);assertEquals("PAID",details.path("status").asText());
        assertTrue(count("notifications","reference_id",id)>=2);
    }
    @Test void partialTransfersAccumulateAndExcessAloneGoesToBuyerWallet() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());
        event(o,100000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());
        assertEquals("PARTIALLY_PAID",payment(f.buyer,id(o)).path("status").asText());
        money("927000",payment(f.buyer,id(o)).path("remainingAmount").decimalValue());
        money("0",balance(f.buyer));assertEquals(0,count("system_order_funds","order_id",id(o)));
        event(o,977000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());
        assertEquals("PAID",payment(f.buyer,id(o)).path("status").asText());money("50000",balance(f.buyer));
        money("50000",payment(f.buyer,id(o)).path("walletCreditedAmount").decimalValue());
    }
    @Test void sequentialAndConcurrentDuplicateWebhooksNeverDoubleFundEscrow() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());long event=EVENT_ID.incrementAndGet();
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var jobs=new ArrayList<Future<Integer>>();
            for(int i=0;i<2;i++) jobs.add(pool.submit(()->{gate.await();return event(o,1027000,event).andReturn().getResponse().getStatus();}));
            gate.countDown();for(var job:jobs) assertEquals(200,job.get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        event(o,1027000,event).andExpect(status().isOk());money("0",balance(f.buyer));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM sepay_transfer_receipts WHERE transaction_id=?",Integer.class,event));
        assertEquals(1,count("system_order_funds","order_id",id(o)));
    }
    @Test void twoConcurrentPartialTransfersDoNotLoseReceivedMoney() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{gate.await();return event(o,500000,EVENT_ID.incrementAndGet()).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{gate.await();return event(o,527000,EVENT_ID.incrementAndGet()).andReturn().getResponse().getStatus();});
            gate.countDown();assertEquals(200,a.get(20,TimeUnit.SECONDS));assertEquals(200,b.get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        var details=payment(f.buyer,id(o));assertEquals("PAID",details.path("status").asText());money("1027000",details.path("receivedAmount").decimalValue());
        money("0",balance(f.buyer));
    }
    @Test void cancellingUnpaidOrderCreditsPartialMoneyOnlyOnceWithoutSellerPenalty() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());event(o,100000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());
        for(int i=0;i<2;i++) mvc.perform(put("/api/v1/orders/"+id(o)+"/cancel").header("Authorization",bearer(f.buyer))).andExpect(status().isOk());
        assertEquals("CANCELLED",payment(f.buyer,id(o)).path("status").asText());money("100000",balance(f.buyer));
        assertEquals("ACTIVE",posts.findById(f.post.getId()).orElseThrow().getStatus());
        assertEquals(0,count("order_confirmation_penalties","order_id",id(o)));assertEquals(0,count("reputation_order_faults","order_id",id(o)));
        event(o,927000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());money("1027000",balance(f.buyer));
        assertEquals(0,count("system_order_funds","order_id",id(o)));
    }
    @Test void expiryUnreservesListingAndLateMoneyCannotReviveOrder() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());event(o,100000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(15)));
        bankScheduler.expireUnpaidOrders();bankScheduler.expireUnpaidOrders();
        assertEquals("EXPIRED",payment(f.buyer,id(o)).path("status").asText());money("100000",balance(f.buyer));
        assertEquals("ACTIVE",posts.findById(f.post.getId()).orElseThrow().getStatus());
        event(o,927000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());money("1027000",balance(f.buyer));
        assertEquals(OrderStatus.CANCELLED,orders.findById(id(o)).orElseThrow().getStatus());assertEquals(0,count("system_order_funds","order_id",id(o)));
    }
    @Test void latePaymentIsRejectedEvenBeforeExpiryJobHasRun() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(15)));
        event(o,1027000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());
        assertEquals("EXPIRED",payment(f.buyer,id(o)).path("status").asText());money("1027000",balance(f.buyer));
    }
    @Test void retriesReuseOrderButCannotChangePaymentMethod() throws Exception {
        var f=fixture();var requestId=UUID.randomUUID();var o=checkout(f,requestId);assertEquals(id(o),id(checkout(f,requestId)));
        mvc.perform(post("/api/v1/orders").header("Authorization",bearer(f.buyer)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("postId",f.post.getId(),"shippingQuoteId",f.quote.getId(),"requestId",requestId,"paymentMethod","WALLET"))))
            .andExpect(status().isConflict());
        assertEquals(1,count("deposit_requests","order_id",id(o)));
    }
    @Test void pendingBankOrderBlocksAnotherCheckoutAndSellerFulfillment() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());
        mvc.perform(post("/api/v1/orders").header("Authorization",bearer(f.buyer)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("postId",f.post.getId(),"shippingQuoteId",f.quote.getId(),"requestId",UUID.randomUUID(),"paymentMethod","BANK_TRANSFER"))))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/orders/"+id(o)+"/confirm").header("Authorization",bearer(f.seller))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/orders/"+id(o)+"/shipments").header("Authorization",bearer(f.seller)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("requestId",UUID.randomUUID(),"leg","SELLER_TO_BUYER")))).andExpect(status().isConflict());
    }
    @Test void paymentInstructionsAreVisibleOnlyToOwningBuyer() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());
        mvc.perform(get("/api/v1/orders/"+id(o)+"/payment")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/"+id(o)+"/payment").header("Authorization",bearer(f.seller))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders/"+id(o)+"/payment").header("Authorization",bearer(user("BUYER")))).andExpect(status().isForbidden());
        assertEquals("PENDING",payment(f.buyer,id(o)).path("status").asText());
    }
    @Test void unauthenticatedOrWrongAccountWebhookCannotConfirmPayment() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());var payload=payload(o,1027000,EVENT_ID.incrementAndGet());
        mvc.perform(post("/api/v1/webhooks/sepay").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload))).andExpect(status().isUnauthorized());
        payload.put("accountNumber","another-bank-account");
        mvc.perform(post("/api/v1/webhooks/sepay").header("Authorization","Apikey test-sepay-token").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload))).andExpect(status().isBadRequest());
        assertEquals("PENDING",payment(f.buyer,id(o)).path("status").asText());
    }
    @Test void paymentCodeCanBeReadFromContentAndNegativeAmountsAreRejected() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());var payload=payload(o,-1,EVENT_ID.incrementAndGet());
        mvc.perform(post("/api/v1/webhooks/sepay").header("Authorization","Apikey test-sepay-token").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload))).andExpect(status().isBadRequest());
        payload=payload(o,1027000,EVENT_ID.incrementAndGet());payload.remove("code");payload.put("content",o.path("payment").path("transferContent").asText()+" chuyen tien");
        mvc.perform(post("/api/v1/webhooks/sepay").header("Authorization","Apikey test-sepay-token").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload))).andExpect(status().isOk());
        assertEquals("PAID",payment(f.buyer,id(o)).path("status").asText());
    }
    @Test void paidBankOrderCancellationRefundsEscrowIntoBuyerWallet() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());event(o,1027000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());
        mvc.perform(put("/api/v1/orders/"+id(o)+"/cancel").header("Authorization",bearer(f.buyer))).andExpect(status().isOk());
        money("1027000",balance(f.buyer));assertEquals(EscrowStatus.REFUNDED,orders.findById(id(o)).orElseThrow().getEscrowStatus());
    }
    @Test void ordinaryWalletTopupsStillWorkAndShareWebhookDeduplication() throws Exception {
        var buyer=user("BUYER");
        var deposit=tree(mvc.perform(post("/api/v1/wallets/deposit-request").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":50000}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var event=new HashMap<String,Object>();long tx=EVENT_ID.incrementAndGet();event.put("id",tx);event.put("transferType","in");event.put("accountNumber","0000000000");event.put("code",deposit.path("code").asText());event.put("transferAmount",50000);
        for(int i=0;i<2;i++) mvc.perform(post("/api/v1/webhooks/sepay").header("Authorization","Apikey test-sepay-token").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(event))).andExpect(status().isOk());
        money("50000",balance(buyer));
    }
    @Autowired NegotiationRepository negotiations;
    @Autowired com.secondlife.secondlife.scheduler.NegotiationExpirationScheduler negotiationExpiry;
    @MockitoBean ChatService chat;
    @Test void expiryOfAnOldNegotiationMustNotReopenAPostAwaitingBankPayment() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());
        var n=new Negotiation();n.setPost(f.post);n.setBuyer(f.buyer);n.setOfferedPrice(new BigDecimal("900000"));
        n.setStatus(NegotiationStatus.ACCEPTED);n.setExpiredAt(Instant.now().minusSeconds(10));n=negotiations.saveAndFlush(n);
        negotiationExpiry.expireNegotiations();
        assertEquals("PAYMENT_PENDING",posts.findById(f.post.getId()).orElseThrow().getStatus());
        assertEquals(NegotiationStatus.ACCEPTED,negotiations.findById(n.getId()).orElseThrow().getStatus());
    }
    @Test void acceptingAnotherNegotiationCannotOverwriteBankReservation() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());
        var n=new Negotiation();n.setPost(f.post);n.setBuyer(user("BUYER"));n.setOfferedPrice(new BigDecimal("900000"));
        n.setStatus(NegotiationStatus.PENDING);n=negotiations.saveAndFlush(n);
        mvc.perform(put("/api/v1/negotiations/"+n.getId()+"/accept").header("Authorization",bearer(f.seller))).andExpect(status().isConflict());
        assertEquals("PAYMENT_PENDING",posts.findById(f.post.getId()).orElseThrow().getStatus());
    }
    @Test void oldWalletCheckoutStillDebitsBalanceImmediately() throws Exception {
        var f=fixture();jdbc.update("UPDATE user_wallets SET balance=2000000 WHERE user_id=?",f.buyer.getId());
        var o=tree(mvc.perform(post("/api/v1/orders").header("Authorization",bearer(f.buyer)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("postId",f.post.getId(),"shippingQuoteId",f.quote.getId(),"requestId",UUID.randomUUID()))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("PROCESSING",o.path("status").asText());assertEquals("WALLET",o.path("paymentMethod").asText());
        money("973000",balance(f.buyer));assertEquals(1,count("system_order_funds","order_id",id(o)));
    }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean WalletService walletService;
    @Test void failureAfterEscrowHoldRollsBackMoneyAndReceiptSoSameEventCanRetry() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());long event=EVENT_ID.incrementAndGet();
        doThrow(new IllegalStateException("Injected test failure after holding escrow")).doCallRealMethod()
            .when(walletService).deposit(eq(f.buyer.getId()),any(BigDecimal.class),any(UUID.class));
        event(o,1077000,event).andExpect(status().isBadRequest());
        assertEquals(0,count("system_order_funds","order_id",id(o)));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM sepay_transfer_receipts WHERE transaction_id=?",Integer.class,event));
        assertEquals("PENDING",payment(f.buyer,id(o)).path("status").asText());
        event(o,1077000,event).andExpect(status().isOk());
        assertEquals("PAID",payment(f.buyer,id(o)).path("status").asText());money("50000",balance(f.buyer));
    }
    @Test void extraTransferRacingPaidCancellationCannotDeadlockOrLoseMoney() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());event(o,1027000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var cancel=pool.submit(()->{gate.await();return mvc.perform(put("/api/v1/orders/"+id(o)+"/cancel").header("Authorization",bearer(f.buyer))).andReturn().getResponse().getStatus();});
            var extra=pool.submit(()->{gate.await();return event(o,50000,EVENT_ID.incrementAndGet()).andReturn().getResponse().getStatus();});
            gate.countDown();assertEquals(200,cancel.get(20,TimeUnit.SECONDS));assertEquals(200,extra.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertEquals(OrderStatus.CANCELLED,orders.findById(id(o)).orElseThrow().getStatus());money("1077000",balance(f.buyer));
    }
    @Test void bankPaidOrderStillSettlesNetSellerAmountAndCommissionExactlyOnce() throws Exception {
        var f=fixture();var o=checkout(f,UUID.randomUUID());event(o,1027000,EVENT_ID.incrementAndGet()).andExpect(status().isOk());
        jdbc.update("UPDATE orders SET status='DELIVERED',shipping_delivered_at=now() WHERE id=?",id(o));
        for(int i=0;i<2;i++) mvc.perform(put("/api/v1/orders/"+id(o)+"/delivered").header("Authorization",bearer(f.buyer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        money("950000",balance(f.seller));money("0",balance(f.buyer));
        assertEquals(EscrowStatus.RELEASED,orders.findById(id(o)).orElseThrow().getEscrowStatus());
        assertEquals(1,count("order_settlements","order_id",id(o)));
        money("50000",jdbc.queryForObject("SELECT platform_commission FROM order_settlements WHERE order_id=?",BigDecimal.class,id(o)));
    }
    private Map<String,Object> payload(JsonNode o,long amount,long eventId) {
        var p=new HashMap<String,Object>();p.put("id",eventId);p.put("transferType","in");p.put("accountNumber","0000000000");
        p.put("code",o.path("payment").path("transferContent").asText());p.put("transferAmount",amount);return p;
    }
    private org.springframework.test.web.servlet.ResultActions event(JsonNode o,long amount,long eventId) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/sepay").header("Authorization","Apikey test-sepay-token")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload(o,amount,eventId))));
    }
    private JsonNode payment(User buyer,UUID order) throws Exception {
        return tree(mvc.perform(get("/api/v1/orders/"+order+"/payment").header("Authorization",bearer(buyer)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private UUID id(JsonNode order) { return UUID.fromString(order.path("id").asText()); }
    private int count(String table,String field,UUID id) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE "+field+"=?",Integer.class,id); }
    private void money(String expected,BigDecimal actual) { assertEquals(0,new BigDecimal(expected).compareTo(actual)); }
    private JsonNode checkout(Fixture f,UUID requestId) throws Exception {
        return tree(mvc.perform(post("/api/v1/orders").header("Authorization",bearer(f.buyer))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("postId",f.post.getId(),
                "shippingQuoteId",f.quote.getId(),"requestId",requestId,"paymentMethod","BANK_TRANSFER"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private Fixture fixture() {
        var seller=user("SELLER");var buyer=user("BUYER");
        var p=new Post();p.setUser(seller);p.setTitle("Bank product");p.setStatus("ACTIVE");p.setPrice(new BigDecimal("1000000"));
        p.setShippingWeight(1000);p.setShippingLength(10);p.setShippingWidth(10);p.setShippingHeight(10);p=posts.saveAndFlush(p);
        var q=new ShippingQuote();q.setBuyerId(buyer.getId());q.setPostId(p.getId());q.setFee(new BigDecimal("27000"));
        q.setExpiresAt(Instant.now().plusSeconds(300));q.setDeliveryAddress("{}");q.setSellerPickupAddress("{}");
        q.setPayload("{\"order_value\":1000000,\"weight\":1000,\"length\":10,\"width\":10,\"height\":10}");q=quotes.saveAndFlush(q);
        return new Fixture(seller,buyer,p,q);
    }
    private User user(String role) {
        var u=new User("bank-"+UUID.randomUUID()+"@example.test","hash",AccountStatus.ACTIVE);
        u.addRole(roles.findByCodeWithPermissions(role).orElseThrow());u=users.saveAndFlush(u);
        var w=new UserWallet();w.setUser(u);w.setBalance(BigDecimal.ZERO);wallets.saveAndFlush(w);return u;
    }
    private BigDecimal balance(User u) { return wallets.findByUserId(u.getId()).orElseThrow().getBalance(); }
    private String bearer(User u) { return "Bearer "+jwt.generateAccessToken(u); }
    private JsonNode tree(String s) { return mapper.readTree(s); }
    private record Fixture(User seller,User buyer,Post post,ShippingQuote quote) {}
}
