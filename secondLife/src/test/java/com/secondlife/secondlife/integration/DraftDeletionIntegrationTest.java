package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.integration.ai.AiPriceProvider;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.CloudinaryService;
import com.secondlife.secondlife.service.ListingCreditService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class DraftDeletionIntegrationTest {
    private static final GhnTestDatabase database = new GhnTestDatabase("DRAFT_DELETE_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("DRAFT_DELETE_TEST_JDBC_URL"); }
    @AfterAll static void closeOwnDatabase() { database.close(); }
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PostRepository posts;
    @Autowired AiChatSessionRepository sessions;
    @Autowired AiChatMessageRepository messages;
    @Autowired AiPriceEstimateRepository estimates;
    @Autowired InspectionOrderRepository inspections;
    @Autowired CreditBalanceRepository balances;
    @Autowired ListingCreditService credits;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean AiPriceProvider priceProvider;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoBean(name = "ollamaChatModel") ChatModel ollama;
    @MockitoBean(name = "googleGenAiChatModel") ChatModel google;

    @Test void ownerDeletesDraftAndGalleryWhileOtherDraftAndChatAuditRemain() throws Exception {
        User seller = user("SELLER"); Post draft = draft(seller), other = draft(seller);
        AiChatSession session = session(seller, draft);
        AiChatMessage message = new AiChatMessage(); message.setSession(session); message.setRole("USER");
        message.setMessageContent("Product condition"); messages.saveAndFlush(message);
        mvc.perform(delete(url(draft)).header("Authorization", bearer(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertFalse(posts.existsById(draft.getId())); assertTrue(posts.existsById(other.getId()));
        assertEquals(0, imageCount(draft)); assertEquals(3, imageCount(other));
        assertTrue(sessions.findById(session.getId()).orElseThrow().isCompleted());
        assertEquals(1, messages.findBySessionIdOrderBySentAtAsc(session.getId()).size());
        mvc.perform(get("/api/v1/posts/" + draft.getId()).header("Authorization", bearer(seller)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/posts/my-posts?page=0&size=10").header("Authorization", bearer(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(other.getId().toString()));
        mvc.perform(delete(url(draft)).header("Authorization", bearer(seller))).andExpect(status().isNotFound());
        verifyNoInteractions(priceProvider, cloudinary);
    }

    @Test void deletionRetainsImmutableValuationAndDoesNotRefundSpentCredit() throws Exception {
        User seller = user("SELLER"); Post draft = draft(seller);
        balances.grant(seller.getId(), CreditType.VALUATION, 1);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var estimate = new AiPriceEstimate(); estimate.setListingId(draft.getId()); estimate.setRequestId(UUID.randomUUID());
            estimate.setInputFingerprint("a".repeat(64)); estimate.setInputSnapshot("{\"title\":\"Draft product\"}");
            estimate.setModelVersion("test-model"); estimate.setFairPriceMin(new BigDecimal("1000000"));
            estimate.setFairPriceMax(new BigDecimal("1500000")); estimate.setSuggestedPrice(new BigDecimal("1200000"));
            estimates.saveAndFlush(estimate);
            credits.consume(seller.getId(), CreditType.VALUATION, "DRAFT_DELETE_TEST:" + draft.getId());
        });
        long ledgerCount = ledgerCount(seller);
        mvc.perform(delete(url(draft)).header("Authorization", bearer(seller))).andExpect(status().isOk());
        assertFalse(posts.existsById(draft.getId()));
        assertEquals(1, estimates.findByListingIdOrderByCreatedAtDescIdDesc(draft.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
        assertEquals(0L, jdbc.queryForObject("SELECT quantity FROM credit_balances WHERE user_id = ? AND credit_type = 'VALUATION'", Long.class, seller.getId()));
        assertEquals(ledgerCount, ledgerCount(seller));
    }

    @Test void otherSellerAndBuyerCannotDeleteOwnersDraft() throws Exception {
        User seller = user("SELLER"); Post draft = draft(seller);
        mvc.perform(delete(url(draft)).header("Authorization", bearer(user("SELLER")))).andExpect(status().isForbidden());
        mvc.perform(delete(url(draft)).header("Authorization", bearer(user("BUYER")))).andExpect(status().isForbidden());
        mvc.perform(delete(url(draft))).andExpect(status().isUnauthorized());
        assertTrue(posts.existsById(draft.getId())); assertEquals(3, imageCount(draft));
    }

    @ParameterizedTest @ValueSource(strings = {"ACTIVE", "SOLD", "PENDING", "PENDING_INSPECTION", "RESERVED", "REJECTED"})
    void onlyDraftStatusCanBeDeleted(String state) throws Exception {
        User seller = user("SELLER"); Post draft = draft(seller); draft.setStatus(state); posts.saveAndFlush(draft);
        mvc.perform(delete(url(draft)).header("Authorization", bearer(seller))).andExpect(status().isConflict());
        assertEquals(state, posts.findById(draft.getId()).orElseThrow().getStatus()); assertEquals(3, imageCount(draft));
    }

    @Test void previouslyPublishedOrChargedDraftCannotBeDeleted() throws Exception {
        User seller = user("SELLER");
        for (boolean charged : new boolean[]{true, false}) {
            Post draft = draft(seller);
            if (charged) draft.setListingCreditCharged(true); else draft.setPublishedAt(Instant.now());
            posts.saveAndFlush(draft);
            mvc.perform(delete(url(draft)).header("Authorization", bearer(seller))).andExpect(status().isConflict());
            assertTrue(posts.existsById(draft.getId()));
        }
    }

    @Test void referenceFromInspectionRollsBackDeletionGalleryAndSessionClosure() throws Exception {
        User seller = user("SELLER"); Post draft = draft(seller); AiChatSession session = session(seller, draft);
        var inspection = new InspectionOrder(); inspection.setPost(draft); inspection.setStatus("PENDING");
        inspection.setInspectionFee(new BigDecimal("200000")); inspection.setShippingFee(new BigDecimal("50000"));
        inspections.saveAndFlush(inspection);
        mvc.perform(delete(url(draft)).header("Authorization", bearer(seller))).andExpect(status().isConflict());
        assertTrue(posts.existsById(draft.getId())); assertEquals(3, imageCount(draft));
        assertFalse(sessions.findById(session.getId()).orElseThrow().isCompleted());
        assertTrue(inspections.existsById(inspection.getId()));
    }

    private User user(String role) {
        var user = new User("draft-delete-" + UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        user.addRole(roles.findByCodeWithPermissions(role).orElseThrow()); return users.saveAndFlush(user);
    }
    private Post draft(User owner) {
        var post = new Post(); post.setUser(owner); post.setTitle("Draft " + UUID.randomUUID());
        post.setImageUrl("https://example.test/product.jpg");
        for (int i = 0; i < 3; i++) post.getImages().add(new PostImage("https://example.test/" + UUID.randomUUID() + ".jpg", "a".repeat(64), null));
        return posts.saveAndFlush(post);
    }
    private AiChatSession session(User owner, Post post) {
        var session = new AiChatSession(); session.setUser(owner); session.setPostId(post.getId()); session = sessions.saveAndFlush(session);
        post.setAiChatSession(session); posts.saveAndFlush(post); return session;
    }
    private int imageCount(Post post) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM post_images WHERE post_id = ?", Integer.class, post.getId());
    }
    private long ledgerCount(User owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM credit_ledger WHERE user_id = ?", Long.class, owner.getId());
    }
    private String url(Post post) { return "/api/v1/posts/" + post.getId() + "/draft"; }
    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
}
