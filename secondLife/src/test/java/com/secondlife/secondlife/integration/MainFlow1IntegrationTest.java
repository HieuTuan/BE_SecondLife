package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.*;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.junit.jupiter.api.condition.EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class MainFlow1IntegrationTest {
    private static final GhnTestDatabase database = new GhnTestDatabase("GHN_MAIN_FLOW_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("GHN_MAIN_FLOW_TEST_JDBC_URL"); }
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
    @Autowired SellerVerificationRepository verifications;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired AiPriceEstimateRepository estimates;
    @Autowired CreditBalanceRepository balances;
    @Autowired CreditBalanceGrantRepository grants;
    @Autowired CreditLedgerRepository ledger;
    @Autowired InspectionOrderRepository inspections;
    @Autowired AiChatSessionRepository sessions;
    @Autowired JwtTokenProvider jwt;
    @Autowired AiValuationService valuations;
    @Autowired PostService postService;
    @Autowired ListingDraftService drafts;
    @Autowired JdbcTemplate jdbc;
    @Autowired ListingReviewService reviews;
    @Autowired ListingImageSimilarity imageSimilarity;
    @Autowired InspectionService inspectionService;
    @MockitoBean AiPriceProvider priceProvider;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoBean(name = "ollamaChatModel") ChatModel ollama;
    @MockitoBean(name = "googleGenAiChatModel") ChatModel google;

    @BeforeEach void aiReplies() throws Exception {
        when(priceProvider.estimate(anyString(), anyList())).thenReturn(new AiPriceProvider.PriceSuggestion(
                new BigDecimal("1000000"), new BigDecimal("1500000"), new BigDecimal("1200000"), "1-2 tuần", "test-model"));
        when(ollama.getOptions()).thenReturn(ChatOptions.builder().build());
        when(google.getOptions()).thenReturn(ChatOptions.builder().build());
        when(ollama.call(any(Prompt.class))).thenReturn(reply("Mô tả sản phẩm đã sử dụng, còn hoạt động tốt."));
        when(google.call(any(Prompt.class))).thenReturn(reply("APPROVED"));
        when(cloudinary.uploadImage(any())).thenReturn("https://example.test/product.jpg");
    }

    @Test void draftChatAndFinalizeAreFreeThenValuationAndPublishChargeSeparately() throws Exception {
        var seller = user("SELLER", true);
        var category = category();
        var image = new MockMultipartFile("images", "product.jpg", "image/jpeg", new byte[]{1,2,3});
        String raw = mvc.perform(multipart("/api/v1/posts/init").file(image)
                        .file(new MockMultipartFile("images", "side.jpg", "image/jpeg", new byte[]{4,5,6}))
                        .file(new MockMultipartFile("images", "back.jpg", "image/jpeg", new byte[]{7,8,9}))
                        .param("categoryId", category.getId().toString()).header("Authorization", bearer(seller)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var init = mapper.readTree(raw);
        UUID postId = UUID.fromString(init.get("postId").asText());
        UUID sessionId = UUID.fromString(init.get("sessionId").asText());
        assertTrue(balances.findByUserId(seller.getId()).isEmpty());
        assertEquals(0, consumes(seller));
        mvc.perform(get("/api/v1/posts/" + postId).header("Authorization", bearer(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.imageUrls.length()").value(3));
        for (int i = 0; i < 6; i++) {
            mvc.perform(multipart("/api/v1/ai/chat").param("sessionId", sessionId.toString())
                            .param("message", "Sản phẩm sử dụng hai năm, bảo hành đã hết").header("Authorization", bearer(seller)))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/posts/finalize-chat/" + sessionId).header("Authorization", bearer(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").isNotEmpty());
        assertNull(posts.findById(postId).orElseThrow().getAiSuggestedPrice());
        assertEquals(0, consumes(seller));
        mvc.perform(put("/api/v1/posts/" + postId + "/draft").header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Tủ lạnh Panasonic","description":"Đã dùng 2 năm, còn tốt","itemCondition":"USED","price":1300000}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/posts/" + postId + "/accept-description").header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Đã dùng 2 năm, còn tốt\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.descriptionAccepted").value(true));
        grants.grant(seller.getId(), CreditType.VALUATION, 1);
        grants.grant(seller.getId(), CreditType.LISTING, 1);
        UUID request = UUID.randomUUID();
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/posts/" + postId + "/ai-price-estimation")
                        .header("Authorization", bearer(seller)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + request + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.suggestedPrice").value(1200000));
        verify(priceProvider, times(1)).estimate(anyString(), anyList());
        assertEquals(0, balance(seller, CreditType.VALUATION));
        assertEquals(1, balance(seller, CreditType.LISTING));
        String publish = "{\"title\":\"Tủ lạnh Panasonic\",\"description\":\"Đã dùng 2 năm, còn tốt\",\"price\":1300000}";
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/posts/submit/" + postId)
                        .header("Authorization", bearer(seller)).contentType(MediaType.APPLICATION_JSON).content(publish))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertEquals(0, balance(seller, CreditType.LISTING));
        assertEquals(2, consumes(seller));
        assertEquals(0, posts.findById(postId).orElseThrow().getPrice().compareTo(new BigDecimal("1300000")));
        mvc.perform(get("/api/v1/posts/" + postId + "/ai-price-estimation/history").header("Authorization", bearer(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "UPDATE ai_price_estimates SET suggested_price = 1 WHERE listing_id = ?", postId));
    }

    @Test void initRejectsWrongImageCountsAndInvalidFilesBeforeUploading() throws Exception {
        var seller = user("SELLER", true); var category = category();
        long before = posts.count();
        for (int count : new int[]{0, 1, 2, 7}) {
            var request = multipart("/api/v1/posts/init").param("categoryId", category.getId().toString())
                    .header("Authorization", bearer(seller));
            for (int i = 0; i < count; i++) request.file(new MockMultipartFile("images", "photo" + i + ".jpg", "image/jpeg", new byte[]{(byte)i, 42}));
            mvc.perform(request).andExpect(status().isBadRequest());
        }
        for (var invalid : java.util.List.of(
                new MockMultipartFile("images", "empty.jpg", "image/jpeg", new byte[0]),
                new MockMultipartFile("images", "file.txt", "text/plain", new byte[]{1}),
                new MockMultipartFile("images", "big.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]))) {
            mvc.perform(multipart("/api/v1/posts/init")
                            .file(new MockMultipartFile("images", "one.jpg", "image/jpeg", new byte[]{1}))
                            .file(new MockMultipartFile("images", "two.jpg", "image/jpeg", new byte[]{2})).file(invalid)
                            .param("categoryId", category.getId().toString()).header("Authorization", bearer(seller)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(before, posts.count());
        verifyNoInteractions(cloudinary);
    }

    @Test void sixImagesAreStoredInOrderAndAllSentToAi() throws Exception {
        var seller = user("SELLER", true); var category = category();
        when(cloudinary.uploadImage(any())).thenAnswer(invocation -> "https://example.test/" +
                ((org.springframework.web.multipart.MultipartFile) invocation.getArgument(0)).getOriginalFilename());
        var request = multipart("/api/v1/posts/init").param("categoryId", category.getId().toString())
                .header("Authorization", bearer(seller));
        for (int i = 0; i < 6; i++) request.file(new MockMultipartFile("images", "photo" + i + ".jpg", "image/jpeg", new byte[]{(byte)i, 42}));
        var raw = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(raw).get("postId").asText();
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM post_images WHERE post_id = ?", Integer.class, UUID.fromString(id)));
        mvc.perform(get("/api/v1/posts/" + id).header("Authorization", bearer(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.imageUrls.length()").value(6))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.test/photo0.jpg"))
                .andExpect(jsonPath("$.data.imageUrls[5]").value("https://example.test/photo5.jpg"));
        var captured = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(google, atLeastOnce()).call(captured.capture());
        assertTrue(captured.getAllValues().stream().flatMap(prompt -> prompt.getInstructions().stream())
                .filter(message -> message instanceof org.springframework.ai.chat.messages.UserMessage)
                .anyMatch(message -> ((org.springframework.ai.chat.messages.UserMessage) message).getMedia().size() == 6));
        String session = mapper.readTree(raw).get("sessionId").asText();
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("sessionId", session).param("message", "Tôi cần hỏi thêm được không?")
                        .header("Authorization", bearer(seller)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("sessionId", session).param("message", "Hỏi thêm").param("images", "string")
                        .header("Authorization", bearer(seller)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("images"));
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("sessionId", session).param("message", "Hỏi thêm").param("images", "")
                        .header("Authorization", bearer(seller)))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/ai/chat").param("sessionId", session).param("message", "Có nên đăng bán?")
                        .param("image", "").file(new MockMultipartFile("images", "chat.jpg", "image/jpeg", new byte[]{1,2,3}))
                        .header("Authorization", bearer(seller)))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/ai/chat").param("sessionId", session).param("message", "Chat chữ")
                        .file(new MockMultipartFile("images", "", "application/octet-stream", new byte[0]))
                        .header("Authorization", bearer(seller)))
                .andExpect(status().isOk());
    }

    @Test void chatSwaggerHasOnlyOneOptionalImagesField() throws Exception {
        var api = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var schema = api.get("paths").get("/api/v1/ai/chat").get("post").get("requestBody")
                .get("content").get("multipart/form-data").get("schema");
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            schema = api.get("components").get("schemas").get(ref.substring(ref.lastIndexOf('/') + 1));
        }
        assertFalse(schema.get("properties").has("image"));
        assertTrue(schema.get("properties").has("images"));
    }

    @Test void failedValuationRollsBackDebitLedgerAndResultAndCanRetry() {
        var seller = user("SELLER", true); var draft = draft(seller);
        grants.grant(seller.getId(), CreditType.VALUATION, 1);
        UUID request = UUID.randomUUID();
        when(priceProvider.estimate(anyString(), anyList())).thenThrow(new AiProviderException("provider failed"));
        assertThrows(AiProviderException.class, () -> valuations.create(seller.getId(), draft.getId(), request));
        assertEquals(1, balance(seller, CreditType.VALUATION));
        assertEquals(0, consumes(seller));
        assertTrue(estimates.findByListingIdAndRequestId(draft.getId(), request).isEmpty());
        doReturn(new AiPriceProvider.PriceSuggestion(
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("15"), null, "test-model"))
                .when(priceProvider).estimate(anyString(), anyList());
        valuations.create(seller.getId(), draft.getId(), request);
        assertEquals(0, balance(seller, CreditType.VALUATION));
        assertEquals(1, consumes(seller));
    }

    @Test void enforcesAuthorizationOwnershipValidationAndReplayInput() throws Exception {
        var seller = user("SELLER", true); var other = user("SELLER", true); var buyer = user("BUYER", false);
        var draft = draft(seller); String url = "/api/v1/posts/" + draft.getId() + "/ai-price-estimation";
        mvc.perform(post("/api/v1/posts/init").header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/v1/posts/not-a-uuid").header("Authorization", bearer(seller)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).header("Authorization", bearer(seller)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(url + "/history").header("Authorization", bearer(other))).andExpect(status().isForbidden());
        assertThrows(ForbiddenException.class, () -> valuations.create(other.getId(), draft.getId(), UUID.randomUUID()));
        assertThrows(ConflictException.class, () -> valuations.create(seller.getId(), draft.getId(), UUID.randomUUID()));
        verifyNoInteractions(priceProvider);
        grants.grant(seller.getId(), CreditType.VALUATION, 2);
        UUID request = UUID.randomUUID(); valuations.create(seller.getId(), draft.getId(), request);
        drafts.update(seller.getId(), draft.getId(), new ListingDraftRequest("Updated product", "Changed condition", "USED", null));
        assertThrows(ConflictException.class, () -> valuations.create(seller.getId(), draft.getId(), request));
        assertEquals(1, balance(seller, CreditType.VALUATION));
        assertEquals(1, consumes(seller));
        mvc.perform(post("/api/v1/posts/submit/" + draft.getId()).header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\",\"description\":\"D\",\"price\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test void twoDifferentValuationsCannotOverdrawLastCredit() throws Exception {
        var seller = user("SELLER", true); var draft = draft(seller);
        grants.grant(seller.getId(), CreditType.VALUATION, 1);
        var outcomes = parallel(() -> {
            try { valuations.create(seller.getId(), draft.getId(), UUID.randomUUID()); return "OK"; }
            catch (ConflictException ex) { return "NO_CREDIT"; }
        });
        assertEquals(1, Collections.frequency(outcomes, "OK"));
        assertEquals(1, Collections.frequency(outcomes, "NO_CREDIT"));
        assertEquals(0, balance(seller, CreditType.VALUATION));
        assertEquals(1, consumes(seller)); verify(priceProvider, times(1)).estimate(anyString(), anyList());
    }

    @Test void simultaneousRetryReturnsSameValuationAndOnlyChargesOnce() throws Exception {
        var seller = user("SELLER", true); var draft = draft(seller);
        grants.grant(seller.getId(), CreditType.VALUATION, 2); UUID request = UUID.randomUUID();
        var results = parallel(() -> valuations.create(seller.getId(), draft.getId(), request).estimateId());
        assertEquals(results.get(0), results.get(1));
        assertEquals(1, balance(seller, CreditType.VALUATION)); assertEquals(1, consumes(seller));
        verify(priceProvider, times(1)).estimate(anyString(), anyList());
    }

    @Test void moderationRejectionAndDuplicateDoNotConsumeListingCredit() {
        var seller = user("SELLER", true); var draft = draft(seller);
        grants.grant(seller.getId(), CreditType.LISTING, 2);
        when(google.call(any(Prompt.class))).thenReturn(reply("REJECTED:Nội dung không phù hợp"));
        assertEquals("REJECTED", postService.submitPost(seller.getId(), draft.getId(), submit(draft, "1000000")).getStatus());
        assertEquals("REJECTED", posts.findById(draft.getId()).orElseThrow().getStatus());
        assertEquals(2, balance(seller, CreditType.LISTING)); assertEquals(0, consumes(seller));
        when(google.call(any(Prompt.class))).thenReturn(reply("APPROVED"));
        assertEquals("ACTIVE", postService.submitPost(seller.getId(), draft.getId(), submit(draft, "1000000")).getStatus());
        var duplicate = draft(seller); duplicate.setTitle(draft.getTitle()); duplicate.setDescription(draft.getDescription());
        duplicate.setCategoryId(draft.getCategoryId()); posts.saveAndFlush(duplicate);
        assertEquals("PENDING", postService.submitPost(seller.getId(), duplicate.getId(), submit(duplicate, "1000000")).getStatus());
        assertEquals(1, balance(seller, CreditType.LISTING)); assertEquals(1, consumes(seller));
        assertThrows(ConflictException.class, () -> drafts.update(seller.getId(), draft.getId(),
                new ListingDraftRequest("New", "New", null, null)));
    }

    @Test void providerFailureAndNoCreditCannotPublishAndHighValueDoesNotUseCreditsAsCash() {
        var seller = user("SELLER", true); var draft = draft(seller);
        when(google.call(any(Prompt.class))).thenReturn(reply("invalid moderation response"));
        assertThrows(AiProviderException.class, () -> postService.submitPost(seller.getId(), draft.getId(), submit(draft, "1000000")));
        assertEquals("DRAFT", posts.findById(draft.getId()).orElseThrow().getStatus());
        when(google.call(any(Prompt.class))).thenReturn(reply("APPROVED"));
        assertThrows(ConflictException.class, () -> postService.submitPost(seller.getId(), draft.getId(), submit(draft, "1000000")));
        grants.grant(seller.getId(), CreditType.LISTING, 1);
        long before = inspections.count();
        assertEquals("PENDING_INSPECTION", postService.submitPost(seller.getId(), draft.getId(), submit(draft, "6000000")).getStatus());
        assertEquals(1, balance(seller, CreditType.LISTING)); assertEquals(before + 1, inspections.count());
        postService.submitPost(seller.getId(), draft.getId(), submit(draft, "6000000"));
        assertEquals(before + 1, inspections.count()); assertEquals(0, consumes(seller));
    }

    @Test void concurrentPublishRetryConsumesOneCreditAndUnapprovedSellerCannotPublish() throws Exception {
        var unapproved = user("SELLER", false); var blocked = draft(unapproved);
        grants.grant(unapproved.getId(), CreditType.LISTING, 1);
        assertThrows(ConflictException.class, () -> postService.submitPost(unapproved.getId(), blocked.getId(), submit(blocked, "1000000")));
        assertEquals(1, balance(unapproved, CreditType.LISTING));
        var seller = user("SELLER", true); var draft = draft(seller);
        grants.grant(seller.getId(), CreditType.LISTING, 1);
        var results = parallel(() -> postService.submitPost(seller.getId(), draft.getId(), submit(draft, "1000000")).getStatus());
        assertEquals(List.of("ACTIVE", "ACTIVE"), results);
        assertEquals(0, balance(seller, CreditType.LISTING)); assertEquals(1, consumes(seller));
        verify(google, times(1)).call(any(Prompt.class));
    }

    @Test void sessionAndPerUserRateLimitsStopFreeChatBeforeModelExecution() throws Exception {
        var seller = user("SELLER", true); var draft = draft(seller);
        AiChatSession session = new AiChatSession(); session.setUser(seller); session.setPostId(draft.getId());
        session.setMessageCount(30); session = sessions.saveAndFlush(session);
        mvc.perform(multipart("/api/v1/ai/chat").param("sessionId", session.getId().toString())
                        .param("message", "Tiếp tục mô tả").header("Authorization", bearer(seller)))
                .andExpect(status().isConflict());
        session.setMessageCount(0); sessions.saveAndFlush(session);
        jdbc.update("""
                INSERT INTO ai_chat_messages(message_id, session_id, role, message_content, sent_at)
                SELECT gen_random_uuid(), ?, 'USER', 'message', now() FROM generate_series(1,20)
                """, session.getId());
        mvc.perform(multipart("/api/v1/ai/chat").param("message", "Phiên mới").header("Authorization", bearer(seller)))
                .andExpect(status().isConflict());
        verify(ollama, never()).call(any(Prompt.class)); verify(google, never()).call(any(Prompt.class));
        assertEquals(0, consumes(seller));
    }

    @Test void identicalImageAndPublishRateAreBlockedWithoutAnotherCharge() {
        var seller = user("SELLER", true); var first = draft(seller); first.setImageFingerprint("abc"); posts.saveAndFlush(first);
        grants.grant(seller.getId(), CreditType.LISTING, 2);
        postService.submitPost(seller.getId(), first.getId(), submit(first, "1000000"));
        var reused = draft(seller); reused.setImageFingerprint("abc"); posts.saveAndFlush(reused);
        assertEquals("PENDING", postService.submitPost(seller.getId(), reused.getId(), submit(reused, "1000000")).getStatus());
        for (int i = 0; i < 4; i++) {
            var accepted = draft(seller); accepted.setStatus("ACTIVE"); accepted.setListingCreditCharged(true);
            accepted.setPublishedAt(java.time.Instant.now()); posts.saveAndFlush(accepted);
        }
        var another = draft(seller);
        assertThrows(ConflictException.class, () -> postService.submitPost(seller.getId(), another.getId(), submit(another, "1000000")));
        assertEquals(1, balance(seller, CreditType.LISTING)); assertEquals(1, consumes(seller));
        verify(google, times(1)).call(any(Prompt.class));
    }

    @Test void acceptingDescriptionEnforcesOwnershipAndNewEditsRequireConfirmation() throws Exception {
        var seller = user("SELLER", true); var other = user("SELLER", true); var p = draft(seller);
        p.setDescriptionAccepted(false); p.setAiDescription("Mô tả từ ảnh"); posts.saveAndFlush(p);
        grants.grant(seller.getId(), CreditType.VALUATION, 1);
        assertThrows(ConflictException.class, () -> valuations.create(seller.getId(), p.getId(), UUID.randomUUID()));
        assertThrows(ConflictException.class, () -> postService.submitPost(seller.getId(), p.getId(), submit(p, "1000000")));
        String url = "/api/v1/posts/" + p.getId() + "/accept-description";
        mvc.perform(post(url).header("Authorization", bearer(other))).andExpect(status().isForbidden());
        mvc.perform(post(url).header("Authorization", bearer(seller))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Mô tả từ ảnh"))
                .andExpect(jsonPath("$.data.descriptionAccepted").value(true));
        drafts.update(seller.getId(), p.getId(), new ListingDraftRequest(p.getTitle(), "Mô tả mới", "USED", null));
        assertFalse(drafts.get(seller.getId(), p.getId()).descriptionAccepted());
        mvc.perform(post(url).header("Authorization", bearer(seller)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Mô tả mới\"}")).andExpect(status().isOk());
        AiChatSession session = new AiChatSession(); session.setUser(seller); session.setPostId(p.getId());
        session = sessions.saveAndFlush(session);
        mvc.perform(multipart("/api/v1/ai/chat").param("sessionId", session.getId().toString())
                .param("message", "Bổ sung thông tin").header("Authorization", bearer(seller))).andExpect(status().isOk());
        assertFalse(posts.findById(p.getId()).orElseThrow().isDescriptionAccepted());
        assertEquals(1, balance(seller, CreditType.VALUATION)); assertEquals(0, consumes(seller));
    }

    @Test void crossSellerDuplicateQueuesForStaffAndConcurrentApprovalChargesOnce() throws Exception {
        var originalSeller = user("SELLER", true); var original = draft(originalSeller);
        String hash = UUID.randomUUID().toString(); original.setImageFingerprint(hash); posts.saveAndFlush(original);
        grants.grant(originalSeller.getId(), CreditType.LISTING, 1);
        postService.submitPost(originalSeller.getId(), original.getId(), submit(original, "1000000"));
        var seller = user("SELLER", true); var duplicate = draft(seller);
        duplicate.setImageFingerprint(hash); posts.saveAndFlush(duplicate);
        grants.grant(seller.getId(), CreditType.LISTING, 1);
        when(google.call(any(Prompt.class))).thenThrow(new RuntimeException("Moderation offline"));
        assertEquals("PENDING", postService.submitPost(seller.getId(), duplicate.getId(), submit(duplicate, "1000000")).getStatus());
        assertEquals(1, balance(seller, CreditType.LISTING)); assertEquals(0, consumes(seller));
        var staff = user("STAFF", false); String url = "/api/staff/listings/" + duplicate.getId();
        mvc.perform(get("/api/staff/listings").header("Authorization", bearer(staff))).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization", bearer(seller))).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", bearer(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicateMatches[0]").value(original.getId().toString()));
        var results = parallel(() -> reviews.approve(duplicate.getId(), staff.getId()).status());
        assertEquals(List.of("ACTIVE", "ACTIVE"), results); assertEquals(0, balance(seller, CreditType.LISTING));
        assertEquals(1, consumes(seller)); assertEquals(staff.getId(), posts.findById(duplicate.getId()).orElseThrow().getReviewedBy());
        mvc.perform(post(url + "/approve").header("Authorization", bearer(staff))).andExpect(status().isOk());
        assertEquals(1, consumes(seller));
    }

    @Test void staffApprovalWithoutCreditRollsBackAndRejectionRemainsFree() throws Exception {
        var seller = user("SELLER", true); var pending = draft(seller);
        pending.setStatus("PENDING"); pending.setPrice(new BigDecimal("1000000")); posts.saveAndFlush(pending);
        var staff = user("STAFF", false); String url = "/api/staff/listings/" + pending.getId();
        mvc.perform(post(url + "/approve").header("Authorization", bearer(staff))).andExpect(status().isConflict());
        assertEquals("PENDING", posts.findById(pending.getId()).orElseThrow().getStatus());
        mvc.perform(post(url + "/reject").header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ảnh thuộc seller khác\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertEquals(0, consumes(seller));
    }

    @Test void croppedPhotoWithDifferentShaQueuesAcrossSellers() throws Exception {
        var image = new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics(); var random = new Random(17);
        g.setColor(new java.awt.Color(185, 175, 160)); g.fillRect(0, 0, 400, 300);
        for (int i = 0; i < 45; i++) {
            g.setColor(new java.awt.Color(random.nextInt(220), random.nextInt(220), random.nextInt(220)));
            int x = random.nextInt(350), y = random.nextInt(260), w = 20 + random.nextInt(100), h = 20 + random.nextInt(90);
            if (i % 2 == 0) g.fillOval(x, y, w, h); else g.fillRect(x, y, w, h);
        }
        g.dispose();
        var full = new java.io.ByteArrayOutputStream(); var cropped = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", full);
        javax.imageio.ImageIO.write(image.getSubimage(40, 30, 320, 240), "jpeg", cropped);
        var firstSeller = user("SELLER", true); var first = draft(firstSeller);
        first.getImages().add(new PostImage(first.getImageUrl(), ListingFingerprint.sha256(full.toByteArray()),
                imageSimilarity.fingerprint(full.toByteArray()))); posts.saveAndFlush(first);
        grants.grant(firstSeller.getId(), CreditType.LISTING, 1);
        postService.submitPost(firstSeller.getId(), first.getId(), submit(first, "1000000"));
        var secondSeller = user("SELLER", true); var second = draft(secondSeller);
        second.getImages().add(new PostImage(second.getImageUrl(), ListingFingerprint.sha256(cropped.toByteArray()),
                imageSimilarity.fingerprint(cropped.toByteArray()))); posts.saveAndFlush(second);
        assertEquals("PENDING", postService.submitPost(secondSeller.getId(), second.getId(), submit(second, "1000000")).getStatus());
        assertTrue(reviews.detail(second.getId()).duplicateMatches().contains(first.getId()));
        assertEquals(0, consumes(secondSeller));
    }

    @Test void inspectionChargesOnlyWhenPassedAndNeverWhenFailed() {
        var inspector = user("INSPECTOR", false);
        for (String result : List.of("PASSED", "FAILED")) {
            var seller = user("SELLER", true); var p = draft(seller); grants.grant(seller.getId(), CreditType.LISTING, 1);
            postService.submitPost(seller.getId(), p.getId(), submit(p, "6000000"));
            UUID orderId = jdbc.queryForObject("SELECT id FROM inspection_orders WHERE post_id = ?", UUID.class, p.getId());
            inspectionService.assignInspector(orderId, inspector.getId());
            assertEquals(1, balance(seller, CreditType.LISTING));
            var request = new InspectionResultRequest(); request.setStatus(result); request.setNote("Kiểm tra thực tế");
            inspectionService.submitResult(orderId, inspector.getId(), request);
            assertEquals("PASSED".equals(result) ? "ACTIVE" : "REJECTED", posts.findById(p.getId()).orElseThrow().getStatus());
            assertEquals("PASSED".equals(result) ? 0 : 1, balance(seller, CreditType.LISTING));
            assertEquals("PASSED".equals(result) ? 1 : 0, consumes(seller));
        }
    }

    private User user(String role, boolean approved) {
        User u = new User("flow1-" + UUID.randomUUID() + "@example.test", "test-password-hash", AccountStatus.ACTIVE);
        u.addRole(roles.findByCodeWithPermissions(role).orElseThrow()); u = users.saveAndFlush(u);
        if (approved) {
            var v = new SellerVerification(u, VerificationType.CITIZEN_ID, null, "https://example.test/front", "https://example.test/back");
            v.setStatus(SellerVerificationStatus.APPROVED); verifications.saveAndFlush(v);
        }
        return u;
    }
    private Category category() {
        Category c = new Category(); c.setName("Tủ lạnh"); return categories.saveAndFlush(c);
    }
    private Post draft(User seller) {
        Post p = new Post(); p.setUser(seller); p.setCategoryId(category().getId());
        p.setDescriptionAccepted(true);
        p.setTitle("Sản phẩm " + UUID.randomUUID()); p.setDescription("Đã sử dụng 2 năm, còn hoạt động tốt.");
        p.setImageUrl("https://example.test/" + UUID.randomUUID() + ".jpg"); return posts.saveAndFlush(p);
    }
    private PostSubmitRequest submit(Post p, String price) {
        var request = new PostSubmitRequest(); request.setTitle(p.getTitle()); request.setDescription(p.getDescription());
        request.setPrice(new BigDecimal(price)); return request;
    }
    private long balance(User user, CreditType type) {
        return balances.findByUserId(user.getId()).stream().filter(b -> b.getCreditType() == type).mapToLong(CreditBalance::getQuantity).sum();
    }
    private long consumes(User user) {
        return ledger.findByUserIdOrderByCreatedAtDesc(user.getId(), org.springframework.data.domain.Pageable.unpaged())
                .stream().filter(e -> "CONSUME".equals(e.getEntryType())).count();
    }
    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
    private ChatResponse reply(String content) { return new ChatResponse(List.of(new Generation(new AssistantMessage(content)))); }
    private <T> List<T> parallel(Callable<T> action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            Callable<T> run = () -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return action.call(); };
            var first = executor.submit(run); var second = executor.submit(run);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            return List.of(first.get(45, TimeUnit.SECONDS), second.get(45, TimeUnit.SECONDS));
        }
    }
}
