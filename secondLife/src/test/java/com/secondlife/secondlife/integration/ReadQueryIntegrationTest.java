package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.SellerVerificationEvent;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.enums.VerificationEventType;
import com.secondlife.secondlife.enums.VerificationType;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.SellerVerificationEventRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.ChatService;
import com.secondlife.secondlife.service.CreditPurchaseService;
import com.secondlife.secondlife.service.InspectionChecklistService;
import com.secondlife.secondlife.service.InspectionService;
import com.secondlife.secondlife.service.MarketplaceListingService;
import com.secondlife.secondlife.service.OrderService;
import com.secondlife.secondlife.service.SellerReputationService;
import com.secondlife.secondlife.service.SellerVerificationService;
import com.secondlife.secondlife.service.UserService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"
})
class ReadQueryIntegrationTest {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ChatService chats;
    @Autowired CreditPurchaseService credits;
    @Autowired OrderService orders;
    @Autowired MarketplaceListingService marketplace;
    @Autowired InspectionService inspections;
    @Autowired InspectionChecklistService checklists;
    @Autowired UserService users;
    @Autowired SellerVerificationService verifications;
    @Autowired SellerReputationService reputation;
    @Autowired SellerVerificationRepository verificationRepository;
    @Autowired SellerVerificationEventRepository events;
    @Autowired UserRepository userRepository;

    private UUID buyer;
    private UUID category;
    private String prefix;
    private Statistics statistics;

    @BeforeEach void fixture() {
        prefix = "read-" + UUID.randomUUID();
        buyer = user("Buyer");
        category = UUID.randomUUID();
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", category, prefix);
        statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @AfterEach void reportQueries(TestInfo test) {
        System.out.println("READ_QUERY " + test.getTestMethod().orElseThrow().getName() + "=" + statistics.getPrepareStatementCount());
    }

    @Test void roomsKeepOrderLatestMessageAndEmptyRoomFallbackWithBoundedQueries() {
        List<UUID> roomIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID seller = user("Seller " + i);
            UUID post = post(seller, i);
            UUID room = UUID.randomUUID();
            roomIds.addFirst(room);
            jdbc.update("INSERT INTO chat_rooms(room_id,post_id,buyer_id,seller_id,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                    room, post, buyer, seller, time(0), time(i));
            if (i != 5) {
                message(room, seller, "old " + i, 1);
                message(room, seller, "latest " + i, 2);
            }
        }
        statistics.clear();
        var result = chats.getUserRooms(buyer);
        assertEquals(roomIds, result.stream().map(r -> r.id()).toList());
        assertEquals("New Conversation", result.getFirst().lastMessage());
        assertEquals("latest 4", result.get(1).lastMessage());
        assertEquals("Seller 4", result.get(1).sellerName());
        assertEquals("latest 0", result.getLast().lastMessage());
        boundedQueries(2);
    }

    @Test void creditHistoryKeepsMissingIntentAndPageOrderWithBoundedQueries() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(purchase(buyer, i, i != 4));
        purchase(user("Other"), 100, true);
        statistics.clear();
        var result = credits.getPurchases(buyer, PageRequest.of(0, 5));
        assertEquals(6, result.totalElements());
        assertEquals(List.of(ids.get(5), ids.get(4), ids.get(3), ids.get(2), ids.get(1)),
                result.items().stream().map(p -> p.id()).toList());
        assertEquals("SEPAY", result.items().getFirst().paymentProvider());
        assertNull(result.items().get(1).paymentIntentId());
        assertEquals(new BigDecimal("1000.00"), result.items().getFirst().snapshot().finalFee());
        boundedQueries(3);
    }

    @Test void buyerOrdersKeepWalletAndBankPaymentDetailsWithBoundedQueries() {
        UUID seller = user("Seller");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(order(seller, i, i != 4));
        jdbc.update("UPDATE deposit_requests SET received_amount=200 WHERE order_id=?", ids.get(3));
        jdbc.update("UPDATE deposit_requests SET expires_at=? WHERE order_id=?", time(0), ids.get(2));
        statistics.clear();
        var result = new TransactionTemplate(transactions).execute(status ->
                orders.getBuyerOrders(buyer, PageRequest.of(0, 5, Sort.by("createdAt").descending())));
        assertEquals(6, result.getTotalElements());
        assertEquals(List.of(ids.get(5), ids.get(4), ids.get(3), ids.get(2), ids.get(1)),
                result.map(o -> o.id()).getContent());
        assertNotNull(result.getContent().getFirst().payment());
        assertEquals("PENDING", result.getContent().getFirst().payment().status());
        assertNull(result.getContent().get(1).payment());
        assertEquals("PARTIALLY_PAID", result.getContent().get(2).payment().status());
        assertEquals(new BigDecimal("800.00"), result.getContent().get(2).payment().remainingAmount());
        assertEquals("EXPIRED", result.getContent().get(3).payment().status());
        assertEquals(BigDecimal.ZERO, result.getContent().get(3).payment().remainingAmount());
        assertEquals("Post 5", result.getContent().getFirst().postTitle());
        assertEquals(new BigDecimal("1000.00"), result.getContent().getFirst().totalAmount());
        boundedQueries(3);
    }

    @Test void sellerOrdersKeepPageAndBankDetailsWithBoundedQueries() {
        UUID seller = user("Seller");
        for (int i = 0; i < 6; i++) order(seller, i, true);
        statistics.clear();
        var result = new TransactionTemplate(transactions).execute(status ->
                orders.getSellerOrders(seller, PageRequest.of(1, 3, Sort.by("createdAt").descending())));
        assertEquals(6, result.getTotalElements());
        assertEquals(List.of("Post 2", "Post 1", "Post 0"), result.map(o -> o.postTitle()).getContent());
        assertTrue(result.stream().allMatch(o -> o.payment() != null));
        boundedQueries(3);
    }

    @Test void missingBankPaymentStillReturnsNotFound() {
        UUID order = order(user("Seller"), 0, true);
        jdbc.update("DELETE FROM deposit_requests WHERE order_id=?", order);
        assertThrows(NotFoundException.class,
                () -> orders.getBuyerOrders(buyer, PageRequest.of(0, 5)));
    }

    @Test void marketplaceKeepsDatabasePaginationImagesAndSellerProfilesWithBoundedQueries() {
        for (int i = 0; i < 6; i++) post(user("Seller " + i), i);
        jdbc.update("UPDATE posts SET status='DRAFT' WHERE category_id=? AND title='Post 0'", category);
        statistics.clear();
        var result = marketplace.list(category, null, PageRequest.of(0, 3, Sort.by("publishedAt").descending()));
        assertEquals(5, result.getTotalElements());
        assertEquals(List.of("Seller 5", "Seller 4", "Seller 3"), result.map(p -> p.sellerName()).getContent());
        assertEquals(List.of("https://image/5/0", "https://image/5/1"), result.getContent().getFirst().imageUrls());
        boundedQueries(3);
    }

    @Test void inspectionPagesKeepNamesImagesAndUnassignedInspectorWithBoundedQueries() {
        for (int i = 0; i < 6; i++) {
            UUID seller = user("Seller " + i);
            jdbc.update("INSERT INTO inspection_orders(id,post_id,buyer_id,seller_id,status,inspection_fee,shipping_fee,created_at) VALUES (?,?,?,?,'PENDING',0,0,?)",
                    UUID.randomUUID(), post(seller, i), buyer, seller, time(i));
        }
        statistics.clear();
        var result = inspections.getBuyerOrders(buyer, PageRequest.of(0, 3));
        assertEquals(6, result.getTotalElements());
        assertEquals(List.of("Seller 5", "Seller 4", "Seller 3"), result.map(o -> o.sellerName()).getContent());
        assertNull(result.getContent().getFirst().inspectorId());
        assertEquals("Buyer", result.getContent().getFirst().buyerName());
        assertEquals(List.of("https://image/5/0", "https://image/5/1"), result.getContent().getFirst().postImageUrls());
        boundedQueries(3);
    }

    @Test void adminUserPagesKeepRolesProfilesAndFilteringWithBoundedQueries() {
        for (int i = 0; i < 6; i++) {
            UUID user = user("Member " + i);
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='BUYER'", user);
        }
        statistics.clear();
        var result = users.getAdminUsers(prefix, AccountStatus.ACTIVE, "BUYER",
                PageRequest.of(0, 3, Sort.by("email")));
        assertEquals(6, result.totalElements());
        assertTrue(result.items().stream().allMatch(u -> u.fullName().startsWith("Member ")));
        assertTrue(result.items().stream().allMatch(u -> u.roles().equals(Set.of("BUYER"))));
        boundedQueries(3);
    }

    @Test void adminUsersWithoutProfilesAndRolesRemainVisible() {
        String email = prefix + "-no-profile@example.test";
        jdbc.update("INSERT INTO users(id,email,password_hash,account_status) VALUES (?,?,'hash','ACTIVE')",
                UUID.randomUUID(), email);
        statistics.clear();
        var result = users.getAdminUsers(email, null, null, PageRequest.of(0, 5));
        assertEquals(1, result.totalElements());
        assertNull(result.items().getFirst().fullName());
        assertTrue(result.items().getFirst().roles().isEmpty());
        boundedQueries(3);
    }

    @Test void checklistListKeepsItemOrderingAndEmptyTemplatesWithBoundedQueries() {
        for (int i = 0; i < 5; i++) {
            UUID template = UUID.randomUUID();
            jdbc.update("INSERT INTO inspection_checklist_templates(id,category_id,title,is_active) VALUES (?,?,?,true)",
                    template, category, prefix + i);
            if (i != 0) for (int j = 2; j >= 1; j--) {
                jdbc.update("INSERT INTO inspection_checklist_items(id,template_id,item_title,order_index,is_mandatory) VALUES (?,?,?,?,true)",
                        UUID.randomUUID(), template, "Item " + j, j);
            }
        }
        statistics.clear();
        var result = checklists.getAllTemplates().stream().filter(t -> t.title().startsWith(prefix)).toList();
        assertEquals(5, result.size());
        assertTrue(result.stream().allMatch(t -> prefix.equals(t.categoryName())));
        assertEquals(1, result.stream().filter(t -> t.items().isEmpty()).count());
        for (var template : result) if (!template.items().isEmpty()) {
            assertEquals(List.of("Item 1", "Item 2"), template.items().stream().map(i -> i.itemTitle()).toList());
        }
        boundedQueries(1);
    }

    @Test void verificationHistoryKeepsEventOrderAndDoesNotMixApplicationsWithBoundedQueries() {
        List<UUID> ids = new TransactionTemplate(transactions).execute(status -> {
            var user = userRepository.findById(buyer).orElseThrow();
            List<UUID> created = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                var application = new SellerVerification();
                application.setUser(user);
                application.setVerificationType(VerificationType.CITIZEN_ID);
                application.setDocumentFrontUrl("front");
                application.setDocumentBackUrl("back");
                application.setStatus(SellerVerificationStatus.REJECTED);
                verificationRepository.saveAndFlush(application);
                created.addFirst(application.getId());
                events.saveAndFlush(new SellerVerificationEvent(application, VerificationEventType.SUBMITTED,
                        null, SellerVerificationStatus.SUBMITTED, null, "USER", null, "submitted " + i));
                events.saveAndFlush(new SellerVerificationEvent(application, VerificationEventType.EKYC_STARTED,
                        SellerVerificationStatus.SUBMITTED, SellerVerificationStatus.EKYC_PENDING, null, "SYSTEM", null, "started " + i));
            }
            return created;
        });
        jdbc.update("""
                INSERT INTO seller_onboarding(user_id,shop_name,email,phone,pickup_address_json,created_at,updated_at)
                VALUES (?,'Read Shop','shop@example.test','0901234567',?,now(),now())
                """, buyer, "{\"name\":\"Shop\",\"phone\":\"0901234567\",\"address\":\"  12 Street  \" ,\"latitude\":10,\"longitude\":106}");
        statistics.clear();
        var result = verifications.getVerificationHistory(buyer);
        assertEquals(ids, result.stream().map(v -> v.id()).toList());
        assertEquals(List.of("submitted 4", "started 4"), result.getFirst().eventHistory().stream().map(e -> e.notes()).toList());
        assertEquals(List.of("submitted 0", "started 0"), result.getLast().eventHistory().stream().map(e -> e.notes()).toList());
        assertEquals("Buyer", result.getFirst().userFullName());
        assertEquals("Read Shop", result.getFirst().shopName());
        assertEquals("shop@example.test", result.getFirst().shopEmail());
        assertEquals("12 Street", result.getFirst().pickupAddress());
        boundedQueries(5);
    }

    @Test void reputationHistoryDoesNotCalculateUnusedOrderMetrics() {
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='SELLER'", buyer);
        statistics.clear();
        assertEquals(0, reputation.history(buyer, PageRequest.of(0, 5)).totalElements());
        boundedQueries(4);
    }

    @Test void reputationHistoryKeepsSellerEligibilityRules() {
        assertThrows(NotFoundException.class, () -> reputation.history(buyer, PageRequest.of(0, 5)));
        jdbc.update("INSERT INTO seller_reputations(seller_id,updated_at) VALUES (?,now())", buyer);
        assertEquals(0, reputation.history(buyer, PageRequest.of(0, 5)).totalElements());
        assertThrows(NotFoundException.class, () -> reputation.history(UUID.randomUUID(), PageRequest.of(0, 5)));
    }

    @Test void emptyListsKeepTheirExistingResponses() {
        assertTrue(chats.getUserRooms(buyer).isEmpty());
        assertTrue(credits.getPurchases(buyer, PageRequest.of(0, 5)).items().isEmpty());
        assertTrue(orders.getBuyerOrders(buyer, PageRequest.of(0, 5)).isEmpty());
        assertTrue(verifications.getVerificationHistory(buyer).isEmpty());
    }

    private UUID user(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,account_status) VALUES (?,?,'hash','ACTIVE')",
                id, prefix + id + "@example.test");
        jdbc.update("INSERT INTO user_profiles(id,user_id,full_name) VALUES (?,?,?)", UUID.randomUUID(), id, name);
        return id;
    }

    private UUID post(UUID seller, int number) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO posts(id,user_id,category_id,title,status,price,created_at,published_at) VALUES (?,?,?,?,'ACTIVE',1000,?,?)",
                id, seller, category, "Post " + number, time(number), time(number));
        for (int i = 0; i < 2; i++) jdbc.update("INSERT INTO post_images(post_id,image_position,image_url) VALUES (?,?,?)",
                id, i, "https://image/" + number + "/" + i);
        return id;
    }

    private void message(UUID room, UUID sender, String content, int second) {
        jdbc.update("INSERT INTO chat_messages(message_id,room_id,sender_id,message_content,sent_at) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), room, sender, content, time(second));
    }

    private UUID purchase(UUID user, int number, boolean withIntent) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO credit_purchases(id,user_id,listing_quantity,valuation_quantity,listing_unit_price,
                    valuation_unit_price,discount_min_quantity,discount_rate,subtotal,discount_amount,final_fee,currency,status,created_at)
                VALUES (?,?,1,0,1000,0,0,0,1000,0,1000,'VND','PAID',?)
                """, id, user, time(number));
        if (withIntent) intent(id);
        return id;
    }

    private void intent(UUID purchase) {
        jdbc.update("INSERT INTO payment_intents(id,purchase_id,provider,provider_intent_id,amount,currency,status) VALUES (?,?,'SEPAY',?,1000,'VND','SUCCEEDED')",
                UUID.randomUUID(), purchase, UUID.randomUUID().toString());
    }

    private UUID order(UUID seller, int number, boolean bank) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders(id,post_id,buyer_id,seller_id,final_price,shipping_fee,status,escrow_status,payment_method,created_at,updated_at)
                VALUES (?,?,?,?,1000,0,?,?,?, ?,?)
                """, id, post(seller, number), buyer, seller, bank ? "PENDING_PAYMENT" : "PROCESSING",
                bank ? "PENDING" : "HELD", bank ? "BANK_TRANSFER" : "WALLET", time(number), time(number));
        if (bank) jdbc.update("INSERT INTO deposit_requests(id,user_id,order_id,amount,code,status,created_at,expires_at) VALUES (?,?,?,1000,?,'PENDING',now(),now()+interval '1 day')",
                UUID.randomUUID(), buyer, id, "SCL" + UUID.randomUUID());
        return id;
    }

    private Timestamp time(int seconds) {
        return Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(seconds));
    }

    private void boundedQueries(long maximum) {
        assertTrue(statistics.getPrepareStatementCount() <= maximum,
                "Expected at most " + maximum + " statements, got " + statistics.getPrepareStatementCount());
    }
}
