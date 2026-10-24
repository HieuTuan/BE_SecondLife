package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Permission;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.integration.ai.AiPriceProvider;
import com.secondlife.secondlife.integration.notification.PushNotificationChannel;
import com.secondlife.secondlife.integration.storage.ReportEvidenceStorage;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.CloudinaryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class ReportSubmissionIntegrationTest {
    private static final GhnTestDatabase DATABASE = new GhnTestDatabase("REPORT_SUBMISSION_TEST_JDBC_URL");

    static boolean databaseAvailable() { return GhnTestDatabase.available("REPORT_SUBMISSION_TEST_JDBC_URL"); }
    @AfterAll static void closeOwnDatabase() { DATABASE.close(); }
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PostRepository posts;
    @Autowired OrderRepository orders;
    @Autowired JwtTokenProvider jwt;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean AiPriceProvider priceProvider;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoBean ReportEvidenceStorage evidenceStorage;
    @MockitoBean(name = "ollamaChatModel") ChatModel ollama;
    @MockitoBean(name = "googleGenAiChatModel") ChatModel google;
    @MockitoBean JavaMailSender mail;
    @MockitoBean SimpMessagingTemplate messages;
    @MockitoBean PushNotificationChannel pushes;

    @Test
    void sellerCanAppendPackagingProofAndBuyerCanAppendIndependentEvidence() throws Exception {
        var buyer = actor("BUYER");
        var seller = actor("SELLER");
        var order = order(seller, buyer);
        var reportId = report(order, buyer, seller);
        var buyerEvidence = evidence(buyer);
        var sellerEvidence = evidence(seller);
        var original = jdbc.queryForMap("SELECT description,evidence_urls,status,updated_at FROM user_reports WHERE id=?", reportId);

        var sellerResponse = data(submit(seller, reportId, body("Packed the item before dispatch", List.of(sellerEvidence)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorId").value(seller.getId().toString()))
                .andExpect(jsonPath("$.data.authorRole").value("SELLER"))
                .andExpect(jsonPath("$.data.evidenceUrls[0]").value(sellerEvidence)));
        var buyerResponse = data(submit(buyer, reportId, body("The received item has a crack", List.of(buyerEvidence)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorRole").value("BUYER")));

        assertNotEquals(sellerResponse.path("id").asText(), buyerResponse.path("id").asText());
        assertEquals(original, jdbc.queryForMap("SELECT description,evidence_urls,status,updated_at FROM user_reports WHERE id=?", reportId));
        assertEquals(reportId, jdbc.queryForObject("SELECT report_id FROM report_evidence_uploads WHERE id=?", UUID.class, evidenceId(sellerEvidence)));
        list(seller, reportId).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(sellerResponse.path("id").asText()));
        list(buyer, reportId).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(buyerResponse.path("id").asText()));
        mvc.perform(get("/api/v1/reports/" + reportId).header("Authorization", bearer(seller))).andExpect(status().isForbidden());
        mvc.perform(get(buyerEvidence).header("Authorization", bearer(seller))).andExpect(status().isForbidden());
        mvc.perform(get(sellerEvidence).header("Authorization", bearer(buyer))).andExpect(status().isForbidden());
    }

    @Test
    void identicalRetryAfterResolutionReturnsOriginalAndChangedPayloadConflicts() throws Exception {
        var buyer = actor("BUYER");
        var seller = actor("SELLER");
        var reportId = report(order(seller, buyer), buyer, seller);
        var request = body("Supplemental purchase evidence", List.of(evidence(buyer)));
        var original = data(submit(buyer, reportId, request).andExpect(status().isCreated()));
        jdbc.update("UPDATE user_reports SET status='DISMISSED',reviewer_id=?,responsible_party='NONE',decision_reason='Reviewed',resolved_at=now() WHERE id=?", actor("STAFF").getId(), reportId);

        submit(buyer, reportId, request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(original.path("id").asText()))
                .andExpect(jsonPath("$.data.createdAt").value(original.path("createdAt").asText()));
        request.put("description", "Different evidence claim");
        submit(buyer, reportId, request).andExpect(status().isConflict());
        request.put("requestId", UUID.randomUUID());
        submit(buyer, reportId, request).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM report_submissions WHERE report_id=?", Integer.class, reportId));
    }

    @Test
    void authorizedReviewersSeeBothSidesWhileUnrelatedUsersAreDenied() throws Exception {
        var buyer = actor("BUYER");
        var seller = actor("SELLER");
        var staff = actor("STAFF");
        var admin = actor("ADMIN");
        var stranger = actor("BUYER");
        var reportId = report(order(seller, buyer), buyer, seller);
        submit(buyer, reportId, body("Buyer evidence", List.of())).andExpect(status().isCreated());
        submit(seller, reportId, body("Seller evidence", List.of())).andExpect(status().isCreated());

        list(staff, reportId).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        list(admin, reportId).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        list(stranger, reportId).andExpect(status().isForbidden());
        submit(stranger, reportId, body("Unrelated claim", List.of())).andExpect(status().isForbidden());
        submit(staff, reportId, body("Reviewer cannot submit", List.of())).andExpect(status().isForbidden());
        mvc.perform(get(path(reportId))).andExpect(status().isUnauthorized());
        mvc.perform(post(path(reportId)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body("Anonymous", List.of())))).andExpect(status().isUnauthorized());
        var limitedStaff = new User("limited@example.test", "test-hash", AccountStatus.ACTIVE);
        limitedStaff.setId(stranger.getId());
        limitedStaff.addRole(new Role("STAFF", "Staff without permission", null));
        mvc.perform(get(path(reportId)).with(user(new CustomUserDetails(limitedStaff))))
                .andExpect(status().isForbidden());
        var permissionOnly = new User("permission@example.test", "test-hash", AccountStatus.ACTIVE);
        permissionOnly.setId(stranger.getId());
        var role = new Role("BUYER", "Buyer with review permission", null);
        role.addPermission(new Permission("STAFF_FRAUD_REVIEW", "Review", null));
        permissionOnly.addRole(role);
        mvc.perform(get(path(reportId)).with(user(new CustomUserDetails(permissionOnly))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listingReportsCannotAcceptOrderResponsesAndReviewStateRemainsOpenForBothParties() throws Exception {
        var buyer = actor("BUYER");
        var seller = actor("SELLER");
        var order = order(seller, buyer);
        var reportId = report(order, buyer, seller);
        jdbc.update("UPDATE user_reports SET status='IN_REVIEW',reviewer_id=? WHERE id=?", actor("STAFF").getId(), reportId);
        submit(seller, reportId, body("Packaging proof during review", List.of())).andExpect(status().isCreated());
        jdbc.update("UPDATE user_reports SET order_id=NULL WHERE id=?", reportId);
        submit(seller, reportId, body("A listing has no order response", List.of())).andExpect(status().isBadRequest());
        list(buyer, reportId).andExpect(status().isBadRequest());
    }

    @Test
    void evidenceOwnershipPrivateReferencesAndLimitsAreValidatedWithoutPartialBinds() throws Exception {
        var buyer = actor("BUYER");
        var seller = actor("SELLER");
        var reportId = report(order(seller, buyer), buyer, seller);
        var sellerEvidence = evidence(seller);
        var buyerEvidence = evidence(buyer);
        submit(seller, reportId, body("Wrong owner", List.of(buyerEvidence))).andExpect(status().isForbidden());
        submit(seller, reportId, body("Public evidence", List.of("https://example.test/proof.mp4"))).andExpect(status().isBadRequest());
        submit(seller, reportId, body("Duplicate evidence", List.of(sellerEvidence, sellerEvidence))).andExpect(status().isBadRequest());
        submit(seller, reportId, body("Too many", java.util.Collections.nCopies(11, sellerEvidence))).andExpect(status().isBadRequest());
        submit(seller, reportId, body(" ", List.of())).andExpect(status().isBadRequest());
        submit(seller, reportId, body("x".repeat(2001), List.of())).andExpect(status().isBadRequest());
        submit(seller, reportId, body("Missing upload", List.of(sellerEvidence, "/api/v1/reports/evidence/" + UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        assertNull(jdbc.queryForObject("SELECT report_id FROM report_evidence_uploads WHERE id=?", UUID.class, evidenceId(sellerEvidence)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM report_submissions WHERE report_id=?", Integer.class, reportId));
    }

    @Test
    void alreadyBoundEvidenceCannotBeReusedAndFailedBindRollsBackOtherAttachments() throws Exception {
        var buyer = actor("BUYER");
        var seller = actor("SELLER");
        var reportId = report(order(seller, buyer), buyer, seller);
        var otherReportId = report(order(seller, buyer), buyer, seller);
        var fresh = evidence(seller);
        var bound = evidence(seller);
        jdbc.update("UPDATE report_evidence_uploads SET report_id=? WHERE id=?", otherReportId, evidenceId(bound));

        submit(seller, reportId, body("Cannot rebind", List.of(fresh, bound))).andExpect(status().isConflict());
        assertNull(jdbc.queryForObject("SELECT report_id FROM report_evidence_uploads WHERE id=?", UUID.class, evidenceId(fresh)));
        assertEquals(otherReportId, jdbc.queryForObject("SELECT report_id FROM report_evidence_uploads WHERE id=?", UUID.class, evidenceId(bound)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM report_submissions WHERE report_id=?", Integer.class, reportId));
    }

    @Test
    void concurrentIdenticalSubmissionsCreateOneRecordAndBindEvidenceOnce() throws Exception {
        var buyer = actor("BUYER");
        var seller = actor("SELLER");
        var reportId = report(order(seller, buyer), buyer, seller);
        var request = body("Same concurrent proof", List.of(evidence(seller)));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return data(submit(seller, reportId, request).andExpect(status().isCreated())); });
            var second = pool.submit(() -> { start.await(); return data(submit(seller, reportId, request).andExpect(status().isCreated())); });
            start.countDown();
            assertEquals(first.get(20, TimeUnit.SECONDS).path("id").asText(), second.get(20, TimeUnit.SECONDS).path("id").asText());
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM report_submissions WHERE report_id=?", Integer.class, reportId));
    }

    @Test
    void responsePaginationIsBoundedAndStable() throws Exception {
        var buyer = actor("BUYER");
        var seller = actor("SELLER");
        var reportId = report(order(seller, buyer), buyer, seller);
        var first = data(submit(buyer, reportId, body("First addition", List.of())).andExpect(status().isCreated()));
        var second = data(submit(buyer, reportId, body("Second addition", List.of())).andExpect(status().isCreated()));
        mvc.perform(get(path(reportId) + "?page=0&size=1&sort=description,desc").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(first.path("id").asText()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get(path(reportId) + "?page=1&size=1").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(second.path("id").asText()));
        mvc.perform(get(path(reportId) + "?size=1000").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    void responseWaitsForOrderLockAndRejectsAfterConcurrentResolutionCommits() throws Exception {
        var buyer = actor("BUYER");
        var seller = actor("SELLER");
        var staff = actor("STAFF");
        var order = order(seller, buyer);
        var reportId = report(order, buyer, seller);
        var evidenceUrl = evidence(seller);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var resolution = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                orders.findByIdForUpdate(order.getId()).orElseThrow();
                jdbc.update("UPDATE user_reports SET status='DISMISSED',reviewer_id=?,responsible_party='NONE',decision_reason='Reviewed',resolved_at=now() WHERE id=?",
                        staff.getId(), reportId);
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Release timeout");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            try {
                var submission = pool.submit(() -> submit(seller, reportId, body("Proof while resolving", List.of(evidenceUrl))));
                assertThrows(TimeoutException.class, () -> submission.get(300, TimeUnit.MILLISECONDS));
                release.countDown();
                resolution.get(10, TimeUnit.SECONDS);
                submission.get(10, TimeUnit.SECONDS).andExpect(status().isConflict());
            } finally {
                release.countDown();
            }
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM report_submissions WHERE report_id=?", Integer.class, reportId));
        assertNull(jdbc.queryForObject("SELECT report_id FROM report_evidence_uploads WHERE id=?", UUID.class, evidenceId(evidenceUrl)));
    }
    @Test
    void submittedProofCannotBeUpdatedOrDeletedFromTheDatabase() throws Exception {
        var buyer = actor("BUYER");
        var seller = actor("SELLER");
        var reportId = report(order(seller, buyer), buyer, seller);
        var submitted = data(submit(seller, reportId, body("Original packaging record", List.of()))
                .andExpect(status().isCreated()));
        var id = UUID.fromString(submitted.path("id").asText());

        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE report_submissions SET description='Replaced' WHERE id=?", id));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM report_submissions WHERE id=?", id));
        assertEquals("Original packaging record", jdbc.queryForObject("SELECT description FROM report_submissions WHERE id=?", String.class, id));
    }
    private Map<String, Object> body(String description, List<String> evidenceUrls) {
        var body = new HashMap<String, Object>();
        body.put("requestId", UUID.randomUUID());
        body.put("description", description);
        body.put("evidenceUrls", evidenceUrls);
        return body;
    }

    private ResultActions submit(User actor, UUID reportId, Map<String, Object> body) throws Exception {
        return mvc.perform(post(path(reportId)).header("Authorization", bearer(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions list(User actor, UUID reportId) throws Exception {
        return mvc.perform(get(path(reportId)).header("Authorization", bearer(actor)));
    }

    private JsonNode data(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString()).path("data");
    }

    private String path(UUID reportId) { return "/api/v1/reports/" + reportId + "/responses"; }
    private String bearer(User actor) { return "Bearer " + jwt.generateAccessToken(actor); }
    private UUID evidenceId(String url) { return UUID.fromString(url.substring(url.lastIndexOf('/') + 1)); }

    private String evidence(User owner) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO report_evidence_uploads(id,owner_id,storage_id,content_type,size_bytes) VALUES (?,?,?,'image/png',3)",
                id, owner.getId(), "submission-proof-" + id);
        return "/api/v1/reports/evidence/" + id;
    }

    private UUID report(Order order, User buyer, User seller) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_reports(id,reporter_id,request_id,seller_id,post_id,order_id,reason_code,description,evidence_urls,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'OTHER','Original buyer report','[]'::jsonb,'OPEN',now(),now())
                """, id, buyer.getId(), UUID.randomUUID(), seller.getId(), order.getPost().getId(), order.getId());
        return id;
    }

    private Order order(User seller, User buyer) {
        var post = new Post();
        post.setUser(seller);
        post.setTitle("Order dispute product");
        post.setStatus("ACTIVE");
        posts.saveAndFlush(post);
        var order = new Order();
        order.setPost(post);
        order.setSeller(seller);
        order.setBuyer(buyer);
        order.setFinalPrice(new BigDecimal("1000000"));
        order.setStatus(OrderStatus.DELIVERED);
        order.setEscrowStatus(EscrowStatus.FROZEN);
        order.setShippingDeliveredAt(Instant.now().minusSeconds(60));
        return orders.saveAndFlush(order);
    }

    private User actor(String role) {
        var actor = new User("report-submission-" + UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        actor.addRole(roles.findByCodeWithPermissions(role).orElseThrow());
        return users.saveAndFlush(actor);
    }
}



