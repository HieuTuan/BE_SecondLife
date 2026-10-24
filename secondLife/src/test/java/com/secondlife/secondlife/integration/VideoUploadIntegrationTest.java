package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.integration.storage.CloudinaryVideoStorage;
import com.secondlife.secondlife.integration.storage.CloudinaryVideoStorage.VerifiedVideo;
import com.secondlife.secondlife.integration.notification.PushNotificationChannel;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.integration.ai.AiPriceProvider;
import com.secondlife.secondlife.integration.storage.ReportEvidenceStorage;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.CloudinaryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import com.secondlife.secondlife.service.VideoVerificationService;
import com.secondlife.secondlife.exception.VideoUploadException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false",
        "app.video-upload.upload-preset=test-private-evidence", "app.video-upload.max-sessions-per-hour=3"
})
class VideoUploadIntegrationTest {
    private static final GhnTestDatabase database = new GhnTestDatabase("VIDEO_UPLOAD_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("VIDEO_UPLOAD_TEST_JDBC_URL"); }
    @AfterAll static void stopDatabase() { database.close(); }
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired VideoVerificationService verification;
    @Autowired ObjectMapper mapper;
    @Autowired OrderRepository orders;
    @Autowired PostRepository posts;
    @MockitoSpyBean CloudinaryVideoStorage storage;
    @MockitoBean JavaMailSender mail;
    @MockitoBean SimpMessagingTemplate websocket;
    @MockitoBean PushNotificationChannel push;
    @MockitoBean AiPriceProvider priceProvider;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoBean ReportEvidenceStorage evidenceStorage;
    @MockitoBean(name="ollamaChatModel") ChatModel ollama;
    @MockitoBean(name="googleGenAiChatModel") ChatModel google;

    @BeforeEach void resetVerificationBudget() { jdbc.update("delete from video_verification_windows"); }

    @Test void buyerReceivesOwnedPrivateChunkedUploadSignature() throws Exception {
        var buyer = user();
        mvc.perform(post("/api/video/upload-signature").header("Authorization", bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.uploadId").isNotEmpty())
            .andExpect(jsonPath("$.data.signature").isNotEmpty())
            .andExpect(jsonPath("$.data.parameters.type").value("authenticated"))
            .andExpect(jsonPath("$.data.parameters.overwrite").value("false"))
            .andExpect(jsonPath("$.data.apiSecret").doesNotExist());
    }

    @Test void anonymousCannotObtainUploadSignature() throws Exception {
        mvc.perform(post("/api/video/upload-signature").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void signatureRetryReusesTheSameOwnedSessionAndQuotaIsEnforced() throws Exception {
        var buyer=user();
        UUID requestId=UUID.randomUUID();
        var first=signature(buyer,requestId);
        assertEquals(first,signature(buyer,requestId));
        assertEquals(1,jdbc.queryForObject("select count(*) from video_upload_sessions where owner_id=?",Integer.class,buyer.getId()));
        signature(buyer,UUID.randomUUID());
        signature(buyer,UUID.randomUUID());
        mvc.perform(post("/api/video/upload-signature").header("Authorization",bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("requestId",UUID.randomUUID()))))
            .andExpect(status().isTooManyRequests());
    }

    @Test void completionPersistsVerifiedVideoAsEvidenceAndRetryDoesNotInspectOrInsertTwice() throws Exception {
        var buyer=user(); var signed=signature(buyer,UUID.randomUUID());
        var video=verified(signed); doReturn(video).when(storage).inspect(video.publicId());
        var first=complete(buyer,signed,Map.of()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.evidenceId").value(signed.path("uploadId").asText()))
            .andExpect(jsonPath("$.data.bytes").value(video.bytes()))
            .andExpect(jsonPath("$.data.format").value("mp4")).andReturn();
        var retry=complete(buyer,signed,Map.of()).andExpect(status().isOk()).andReturn();
        assertEquals(mapper.readTree(first.getResponse().getContentAsString()).path("data"),
            mapper.readTree(retry.getResponse().getContentAsString()).path("data"));
        verify(storage,times(1)).inspect(video.publicId());
        assertEquals(1,jdbc.queryForObject("select count(*) from report_evidence_uploads where id=?",Integer.class,UUID.fromString(signed.path("uploadId").asText())));
    }

    @Test void anotherUserCannotCompleteOrReadTheVideo() throws Exception {
        var owner=user(); var stranger=user(); var signed=signature(owner,UUID.randomUUID());
        complete(stranger,signed,Map.of()).andExpect(status().isForbidden());
        verify(storage,never()).inspect(anyString());
        var video=verified(signed); doReturn(video).when(storage).inspect(video.publicId());
        complete(owner,signed,Map.of()).andExpect(status().isOk());
        mvc.perform(get("/api/video/"+signed.path("uploadId").asText()+"/playback").header("Authorization",bearer(stranger)))
            .andExpect(status().isForbidden());
    }

    @Test void verificationCooldownSurvivesFailedCompletionRollback() throws Exception {
        var owner=user(); var signed=signature(owner,UUID.randomUUID());
        doThrow(new IOException("not merged yet")).when(storage).inspect(signed.path("publicId").asText());
        complete(owner,signed,Map.of()).andExpect(status().isBadGateway());
        complete(owner,signed,Map.of()).andExpect(status().isTooManyRequests());
        verify(storage,times(1)).inspect(signed.path("publicId").asText());
        assertEquals(0,jdbc.queryForObject("select count(*) from report_evidence_uploads where id=?",Integer.class,UUID.fromString(signed.path("uploadId").asText())));
    }

    @Test void unmergedOrUnavailableProviderVideoDoesNotBecomeEvidence() throws Exception {
        var owner=user(); var signed=signature(owner,UUID.randomUUID());
        doThrow(new IOException("provider secret must not escape")).when(storage).inspect(signed.path("publicId").asText());
        complete(owner,signed,Map.of()).andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("Video is not available for verification; finish uploading all chunks and retry"));
        assertEquals(0,jdbc.queryForObject("select count(*) from report_evidence_uploads where id=?",Integer.class,UUID.fromString(signed.path("uploadId").asText())));
    }

    @Test void forgedMetadataAndPublicIdAreRejectedWithoutSavingEvidence() throws Exception {
        var owner=user(); var signed=signature(owner,UUID.randomUUID()); var video=verified(signed);
        complete(owner,signed,Map.of("publicId","report-evidence/videos/"+UUID.randomUUID())).andExpect(status().isConflict());
        verify(storage,never()).inspect(anyString());
        doReturn(video).when(storage).inspect(video.publicId());
        complete(owner,signed,Map.of("bytes",video.bytes()+1)).andExpect(status().isConflict());
        resetCooldown(signed);
        complete(owner,signed,Map.of("videoUrl","https://evil.example/forged.mp4")).andExpect(status().isConflict());
        resetCooldown(signed);
        assertEquals(0,jdbc.queryForObject("select count(*) from report_evidence_uploads where id=?",Integer.class,UUID.fromString(signed.path("uploadId").asText())));
        complete(owner,signed,Map.of("bytes",video.bytes(),"duration",video.duration(),"format",video.format(),"videoUrl",video.videoUrl()))
            .andExpect(status().isOk());
    }

    @Test void expiryAndOversizedVerifiedMetadataCannotBeCompleted() throws Exception {
        var owner=user(); UUID requestId=UUID.randomUUID(); var signed=signature(owner,requestId);
        UUID id=UUID.fromString(signed.path("uploadId").asText());
        jdbc.update("update video_upload_sessions set created_at=CURRENT_TIMESTAMP-INTERVAL '2 hours', expires_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' where id=?",id);
        complete(owner,signed,Map.of()).andExpect(status().isConflict());
        mvc.perform(post("/api/video/upload-signature").header("Authorization",bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("requestId",requestId)))).andExpect(status().isConflict());
        var fresh=signature(owner,UUID.randomUUID()); var v=verified(fresh);
        doReturn(new VerifiedVideo(v.publicId(),v.videoUrl(),262144001L,v.format(),v.duration(),v.assetId(),v.version()))
            .when(storage).inspect(v.publicId());
        complete(owner,fresh,Map.of()).andExpect(status().isBadRequest());
    }

    @Test void staffCanReadOnlySubmittedVideoAndPlaybackDoesNotProxyBytesThroughBackend() throws Exception {
        var buyer=user(); var staff=user("STAFF"); var signed=signature(buyer,UUID.randomUUID());
        var video=verified(signed); doReturn(video).when(storage).inspect(video.publicId());
        complete(buyer,signed,Map.of()).andExpect(status().isOk());
        String uploadId=signed.path("uploadId").asText();
        mvc.perform(get("/api/video/"+uploadId+"/playback").header("Authorization",bearer(staff))).andExpect(status().isForbidden());
        var seller=user("SELLER"); var listing=new Post(); listing.setUser(seller); listing.setTitle("Video evidence order"); listing.setStatus("ACTIVE"); posts.saveAndFlush(listing);
        var order=new Order(); order.setBuyer(buyer); order.setSeller(seller); order.setPost(listing); order.setFinalPrice(new BigDecimal("1000000"));
        order.setStatus(OrderStatus.DELIVERED); order.setEscrowStatus(EscrowStatus.HELD); order.setShippingDeliveredAt(Instant.now().minusSeconds(60)); orders.saveAndFlush(order);
        mvc.perform(post("/api/v1/reports").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("requestId",UUID.randomUUID(),"orderId",order.getId(),"reasonCode","MISLEADING_DESCRIPTION",
                        "description","Video of unboxing a damaged item", "evidenceUrls",List.of("/api/v1/reports/evidence/"+uploadId)))))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/reports/evidence/"+uploadId+"/metadata").header("Authorization",bearer(staff)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.mediaType").value("VIDEO"))
            .andExpect(jsonPath("$.data.contentType").value("video/mp4"));
        mvc.perform(get("/api/video/"+uploadId+"/playback").header("Authorization",bearer(staff)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").isNotEmpty())
            .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
        mvc.perform(get("/api/v1/reports/evidence/"+uploadId).header("Authorization",bearer(staff)))
            .andExpect(status().isBadRequest());
        verify(evidenceStorage,never()).read(video.publicId());
    }

    @Test void concurrentSignatureRequestsAndCompletionsRemainIdempotent() throws Exception {
        var owner=user(); UUID requestId=UUID.randomUUID();
        JsonNode signed;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(() -> { start.await(); return signature(owner,requestId); });
            var b=pool.submit(() -> { start.await(); return signature(owner,requestId); });
            start.countDown(); signed=a.get(15,TimeUnit.SECONDS);
            assertEquals(signed,b.get(15,TimeUnit.SECONDS));
            var video=verified(signed); doReturn(video).when(storage).inspect(video.publicId());
            final JsonNode ticket=signed;
            var ready=new CountDownLatch(1);
            var first=pool.submit(() -> { ready.await(); return complete(owner,ticket,Map.of()).andExpect(status().isOk()).andReturn(); });
            var second=pool.submit(() -> { ready.await(); return complete(owner,ticket,Map.of()).andExpect(status().isOk()).andReturn(); });
            ready.countDown();
            assertEquals(mapper.readTree(first.get(15,TimeUnit.SECONDS).getResponse().getContentAsString()).path("data"),
                mapper.readTree(second.get(15,TimeUnit.SECONDS).getResponse().getContentAsString()).path("data"));
            verify(storage,times(1)).inspect(video.publicId());
        }
    }

    @Test void verificationOwnerAndGlobalBudgetsAreDurable() {
        UUID owner=UUID.randomUUID();
        for(int i=0;i<20;i++) verification.acquire(owner,UUID.randomUUID());
        assertEquals(429,assertThrows(VideoUploadException.class,() -> verification.acquire(owner,UUID.randomUUID())).getStatus());
        assertEquals(20,jdbc.queryForObject("select calls from video_verification_windows where id='global'",Integer.class));
        jdbc.update("update video_verification_windows set calls=100 where id='global'");
        assertEquals(429,assertThrows(VideoUploadException.class,() -> verification.acquire(UUID.randomUUID(),UUID.randomUUID())).getStatus());
        assertEquals(100,jdbc.queryForObject("select calls from video_verification_windows where id='global'",Integer.class));
    }

    private void resetCooldown(JsonNode signed) {
        jdbc.update("update video_verification_windows set started_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' where id=?",
            "upload:"+signed.path("uploadId").asText());
    }

    private JsonNode signature(User owner,UUID requestId) throws Exception {
        var result=mvc.perform(post("/api/video/upload-signature").header("Authorization",bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("requestId",requestId))))
            .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
    private ResultActions complete(User owner,JsonNode signature,Map<String,Object> extra) throws Exception {
        var body=new HashMap<String,Object>(); body.put("uploadId",signature.path("uploadId").asText());
        body.put("publicId",signature.path("publicId").asText()); body.putAll(extra);
        return mvc.perform(post("/api/video/complete").header("Authorization",bearer(owner)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)));
    }
    private VerifiedVideo verified(JsonNode signed) {
        String publicId=signed.path("publicId").asText();
        return new VerifiedVideo(publicId,"https://res.cloudinary.com/test/video/authenticated/v42/"+publicId+".mp4",1048576,"mp4",12.5,UUID.randomUUID().toString(),42);
    }
    private User user() { return user("BUYER"); }
    private User user(String role) {
        var user = new User("video-" + UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        user.addRole(roles.findByCodeWithPermissions(role).orElseThrow());
        return users.saveAndFlush(user);
    }
    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
}
