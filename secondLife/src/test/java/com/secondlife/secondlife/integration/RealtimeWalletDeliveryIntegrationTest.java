package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.WalletService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations="classpath:application-test.properties", properties={
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"})
@EnabledIf("available")
class RealtimeWalletDeliveryIntegrationTest {
    static final GhnTestDatabase database = new GhnTestDatabase("BANK_PAYMENT_TEST_JDBC_URL");
    static boolean available() { return GhnTestDatabase.available("BANK_PAYMENT_TEST_JDBC_URL"); }
    @AfterAll static void closeDatabase() { database.close(); }
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", database::getJdbcUrl);
        r.add("spring.datasource.username", database::getUsername);
        r.add("spring.datasource.password", database::getPassword);
    }

    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired UserWalletRepository wallets;
    @Autowired JwtTokenProvider jwt;
    @Autowired WalletService service;
    @Autowired PostRepository posts;
    @Autowired InspectionOrderRepository inspections;
    @Autowired com.secondlife.secondlife.service.DepositService deposits;
    @Autowired com.secondlife.secondlife.service.NotificationService notifications;
    @Autowired TransactionTemplate transactions;
    @Autowired org.springframework.messaging.simp.user.SimpUserRegistry registry;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.mail.javamail.JavaMailSender mail;
    final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    final List<StompSession> sessions = new ArrayList<>();

    @AfterEach void disconnect() {
        sessions.forEach(s -> { if (s.isConnected()) s.disconnect(); });
        client.stop();
    }

    private User createUser() {
        var user = users.saveAndFlush(new User(UUID.randomUUID()+"@example.test", "hash", AccountStatus.ACTIVE));
        var wallet = new UserWallet();
        wallet.setUser(user);
        wallets.saveAndFlush(wallet);
        return user;
    }

    private BlockingQueue<String> subscribe(User user, String queue) throws Exception {
        var headers = new StompHeaders();
        headers.set("Authorization", "Bearer " + jwt.generateAccessToken(user));
        var session = client.connectAsync("ws://127.0.0.1:"+port+"/ws/websocket",
                new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
        sessions.add(session);
        var received = new LinkedBlockingQueue<String>();
        session.subscribe("/user/queue/" + queue, new StompFrameHandler() {
            public Type getPayloadType(StompHeaders h) { return byte[].class; }
            public void handleFrame(StompHeaders h, Object payload) {
                received.add(new String((byte[])payload, StandardCharsets.UTF_8));
            }
        });
        return received;
    }

    @Test void committedDepositReachesBothOwnerSessionsAndRollbackDoesNotPublish() throws Exception {
        var owner = createUser();
        var other = createUser();
        var first = subscribe(owner, "wallet-updates");
        var second = subscribe(owner, "wallet-updates");
        var outsider = subscribe(other, "wallet-updates");
        awaitSubscriptions(3);
        transactions.executeWithoutResult(tx -> {
            service.deposit(owner.getId(), BigDecimal.TEN, UUID.randomUUID());
            tx.setRollbackOnly();
        });
        assertNull(first.poll(300, TimeUnit.MILLISECONDS));
        service.deposit(owner.getId(), BigDecimal.ONE, UUID.randomUUID());
        var event = first.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertTrue(event.contains("WALLET_CHANGED"));
        assertFalse(event.contains("balance"));
        assertEquals(event, second.poll(5, TimeUnit.SECONDS));
        assertNull(outsider.poll(300, TimeUnit.MILLISECONDS));
        assertEquals(0, BigDecimal.ONE.compareTo(wallets.findByUserId(owner.getId()).orElseThrow().getBalance()));
    }

    private void awaitSubscriptions(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (registry.findSubscriptions(s -> true).size() < count && System.nanoTime() < deadline) Thread.sleep(20);
        assertEquals(count, registry.findSubscriptions(s -> true).size());
    }

    @Test void assignmentIsRoutedToExpertAndSellerAndInboxReadIsPrivate() throws Exception {
        var seller = createUser();
        var expert = createUser();
        var unrelated = createUser();
        var post = new com.secondlife.secondlife.entity.Post();
        post.setUser(seller);
        post.setPrice(BigDecimal.TEN);
        post.setTitle("Test appliance");
        post.setStatus("ACTIVE");
        posts.saveAndFlush(post);
        var sellerEvents = subscribe(seller, "updates");
        var expertEvents = subscribe(expert, "updates");
        var others = subscribe(unrelated, "updates");
        awaitSubscriptions(3);
        var inspection = new com.secondlife.secondlife.entity.InspectionOrder();
        inspection.setPost(post);
        inspection.setSeller(seller);
        inspection.setInspector(expert);
        inspection.setStatus("SCHEDULED");
        inspections.saveAndFlush(inspection);
        var event = expertEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertTrue(event.contains(inspection.getId().toString()));
        assertEquals(event, sellerEvents.poll(5, TimeUnit.SECONDS));
        assertNull(others.poll(300, TimeUnit.MILLISECONDS));
        inspection.setInspector(null);
        inspections.saveAndFlush(inspection);
        var removal = expertEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(removal);
        assertTrue(removal.contains("INSPECTION_CHANGED"));
        assertFalse(removal.contains(inspection.getId().toString()));
        assertNotNull(sellerEvents.poll(5, TimeUnit.SECONDS));
        var notification = notifications.notifyInApp(expert.getId(), "Assignment", "Test", "SYSTEM", null, null, null);
        notifications.markAsRead(expert.getId(), notification.id());
        var read = expertEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(read);
        assertTrue(read.contains("NOTIFICATIONS_CHANGED"));
        assertNull(sellerEvents.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test void depositStatusIsOwnerScopedAndUnrelatedCreditsCannotConfirmIt() {
        var owner = createUser();
        var other = createUser();
        var pending = deposits.createDepositRequest(owner.getId(),
                new com.secondlife.secondlife.dto.request.DepositCreateRequestDTO(BigDecimal.valueOf(50000)));
        service.deposit(owner.getId(), BigDecimal.TEN, UUID.randomUUID());
        assertEquals("PENDING", deposits.getDepositRequest(owner.getId(), pending.id()).status().name());
        assertThrows(com.secondlife.secondlife.exception.NotFoundException.class,
                () -> deposits.getDepositRequest(other.getId(), pending.id()));
    }
}
