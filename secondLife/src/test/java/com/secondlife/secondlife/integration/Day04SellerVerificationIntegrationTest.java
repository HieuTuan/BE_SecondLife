package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.entity.IdentityRestriction;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.IdentityRestrictionRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.PostService;
import com.secondlife.secondlife.service.SellerVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.seeder.enabled=false"
})
class Day04SellerVerificationIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private IdentityRestrictionRepository restrictionRepository;
    @Autowired private SellerVerificationRepository sellerVerificationRepository;
    @Autowired private SellerVerificationService sellerVerificationService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private PostService postService;

    @Test
    void approvedBuyerRetainsBuyerGetsSellerAndCanUseSellerEndpoint() throws Exception {
        String email = randomEmail("seller");
        String buyerToken = field(register(email), "accessToken");
        mockMvc.perform(post("/api/v1/posts/init")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/seller-verifications")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationJson("012345678901")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.documentNumber").value("********8901"));
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isUnauthorized());
        UUID sellerId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        assertNull(sellerVerificationRepository.findTopByUserIdOrderBySubmittedAtDesc(sellerId)
                .orElseThrow().getDocumentNumber());

        String sellerToken = field(login(email), "accessToken");
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("BUYER")))
                .andExpect(jsonPath("$.data.roles", hasItem("SELLER")));
        when(postService.initPost(any(UUID.class), any(PostInitRequest.class)))
                .thenReturn(new PostInitResponse(UUID.randomUUID(), UUID.randomUUID(), "Ready"));
        mockMvc.perform(post("/api/v1/posts/init")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminCanDecideReviewAndDecisionIsAudited() throws Exception {
        String email = randomEmail("review");
        String buyerToken = field(register(email), "accessToken");
        String body = mockMvc.perform(post("/api/seller-verifications")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationJson("012345678803")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"))
                .andReturn().getResponse().getContentAsString();
        String id = field(body, "id");
        UUID buyerId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        assertFalse(userRepository.findByIdWithAuthorities(buyerId).orElseThrow().hasRole("SELLER"));

        String staffToken = tokenForRole("STAFF");
        String adminToken = tokenForRole("ADMIN");
        mockMvc.perform(get("/api/staff/seller-verifications/" + id)
                        .header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventHistory").isArray());
        mockMvc.perform(post("/api/admin/seller-verifications/" + id + "/approve")
                        .header("Authorization", bearer(staffToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/seller-verifications/" + id + "/approve")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(staffToken)))
                .andExpect(jsonPath("$.data.permissions", not(hasItem("ADMIN_PERMANENT_BAN"))));
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.data.permissions", hasItem("ADMIN_PERMANENT_BAN")));

        mockMvc.perform(post("/api/admin/seller-verifications/" + id + "/approve")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mockMvc.perform(get("/api/admin/seller-verifications/" + id)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventHistory[*].eventType", hasItem("ADMIN_APPROVED")));
        mockMvc.perform(post("/api/admin/seller-verifications/" + id + "/approve")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        User approved = userRepository.findByIdWithAuthorities(buyerId).orElseThrow();
        assertTrue(approved.hasRole("BUYER"));
        assertTrue(approved.hasRole("SELLER"));
        assertEquals(2, approved.getUserRoles().size());
    }

    @Test
    void permanentRestrictionBlocksNormalizedIdentityBeforeEkyc() throws Exception {
        String document = "012345678911";
        restrict(document, "PERMANENT_SELLER_BAN");
        String email = randomEmail("banned");
        String buyerToken = field(register(email), "accessToken");

        mockMvc.perform(post("/api/seller-verifications")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationJson("0123-4567 8911")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reasonCode").value("PERMANENT_SELLER_BAN"))
                .andExpect(jsonPath("$.data.ekycStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.documentNumber").value("********8911"));
        UUID buyerId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        assertFalse(userRepository.findByIdWithAuthorities(buyerId).orElseThrow().hasRole("SELLER"));
        assertNull(sellerVerificationRepository.findTopByUserIdOrderBySubmittedAtDesc(buyerId)
                .orElseThrow().getDocumentNumber());
    }

    @Test
    void reviewRestrictionMustBeRevokedBeforeAdminApproval() throws Exception {
        String document = "012345678912";
        IdentityRestriction restriction = restrict(document, "MANUAL_REVIEW_REQUIRED");
        String buyerToken = field(register(randomEmail("restricted")), "accessToken");
        String body = mockMvc.perform(post("/api/seller-verifications")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationJson(document)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.reasonCode").value("MANUAL_REVIEW_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        String id = field(body, "id");
        String adminToken = tokenForRole("ADMIN");

        mockMvc.perform(post("/api/admin/seller-verifications/" + id + "/approve")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        restriction.setRevokedAt(Instant.now());
        restrictionRepository.saveAndFlush(restriction);
        mockMvc.perform(post("/api/admin/seller-verifications/" + id + "/approve")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void providerFailureRemainsPendingAcrossAdminRetry() throws Exception {
        String email = randomEmail("provider");
        String buyerToken = field(register(email), "accessToken");
        String body = mockMvc.perform(post("/api/seller-verifications")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationJson("012345677702")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("EKYC_PENDING"))
                .andReturn().getResponse().getContentAsString();
        String id = field(body, "id");
        UUID verificationId = UUID.fromString(id);
        SellerVerification pending = sellerVerificationRepository.findById(verificationId).orElseThrow();
        assertEquals(0, pending.getRecoveryAttempts());
        assertTrue(pending.getNextRetryAt().isAfter(Instant.now()));
        pending.setNextRetryAt(Instant.now().minusSeconds(1));
        sellerVerificationRepository.saveAndFlush(pending);
        assertTrue(sellerVerificationRepository.findDueRecoveryIds(
                Instant.now(), 3, PageRequest.of(0, 10)).contains(verificationId));
        assertEquals("EKYC_PENDING", sellerVerificationService
                .retryPendingVerificationSystem(verificationId).status().name());
        SellerVerification retried = sellerVerificationRepository.findById(verificationId).orElseThrow();
        assertEquals(1, retried.getRecoveryAttempts());
        assertTrue(retried.getNextRetryAt().isAfter(Instant.now()));
        String adminToken = tokenForRole("ADMIN");

        mockMvc.perform(post("/api/admin/seller-verifications/" + id + "/retry-ekyc")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EKYC_PENDING"))
                .andExpect(jsonPath("$.data.ekycStatus").value("PROVIDER_ERROR"));
        mockMvc.perform(get("/api/admin/seller-verifications/" + id)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.data.eventHistory[*].eventType", hasItem("EKYC_RETRY_REQUESTED")));
    }

    @Test
    void onlyApplicantCanResubmitFixableDocuments() throws Exception {
        String applicantEmail = randomEmail("resubmit");
        String applicantToken = field(register(applicantEmail), "accessToken");
        String otherToken = field(register(randomEmail("other")), "accessToken");
        String body = mockMvc.perform(post("/api/seller-verifications")
                        .header("Authorization", bearer(applicantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationJson("012345678801")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("RESUBMIT_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        String id = field(body, "id");
        String newImages = """
                {"documentFrontUrl":"https://example.test/new-front.jpg",
                 "documentBackUrl":"https://example.test/new-back.jpg",
                 "selfieUrl":"https://example.test/new-selfie.jpg"}
                """;

        mockMvc.perform(post("/api/seller-verifications/" + id + "/resubmit")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(newImages))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/seller-verifications/" + id + "/resubmit")
                        .header("Authorization", bearer(applicantToken))
                        .contentType(MediaType.APPLICATION_JSON).content(newImages))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESUBMIT_REQUIRED"))
                .andExpect(jsonPath("$.data.resubmissionCount").value(1));
    }

    @Test
    void adminRejectionIsAuditedAndDoesNotGrantSeller() throws Exception {
        String email = randomEmail("rejected");
        String buyerToken = field(register(email), "accessToken");
        String body = mockMvc.perform(post("/api/seller-verifications")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationJson("012345678804")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"))
                .andReturn().getResponse().getContentAsString();
        String id = field(body, "id");
        String adminToken = tokenForRole("ADMIN");

        mockMvc.perform(post("/api/admin/seller-verifications/" + id + "/reject")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"Identity evidence is inconsistent\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewSource").value("ADMIN"));
        mockMvc.perform(get("/api/admin/seller-verifications/" + id)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.data.eventHistory[*].eventType", hasItem("ADMIN_REJECTED")));
        UUID buyerId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        assertFalse(userRepository.findByIdWithAuthorities(buyerId).orElseThrow().hasRole("SELLER"));
        assertNull(sellerVerificationRepository.findById(UUID.fromString(id))
                .orElseThrow().getDocumentNumber());
    }

    private IdentityRestriction restrict(String document, String reason) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(document.getBytes(StandardCharsets.UTF_8)));
        IdentityRestriction restriction = new IdentityRestriction();
        restriction.setDocumentNumberHash(hash);
        restriction.setReasonCode(reason);
        return restrictionRepository.saveAndFlush(restriction);
    }

    private String tokenForRole(String code) {
        Role role = roleRepository.findByCodeWithPermissions(code).orElseThrow();
        User user = new User(randomEmail(code.toLowerCase()),
                passwordEncoder.encode("Password@123"), AccountStatus.ACTIVE);
        user.addRole(role);
        return jwtTokenProvider.generateAccessToken(userRepository.saveAndFlush(user));
    }

    private String register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Password@123\",\"fullName\":\"Day Four Buyer\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Password@123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String verificationJson(String document) {
        return """
                {"verificationType":"CITIZEN_ID","documentNumber":"%s",
                 "documentFrontUrl":"https://example.test/front.jpg",
                 "documentBackUrl":"https://example.test/back.jpg",
                 "selfieUrl":"https://example.test/selfie.jpg"}
                """.formatted(document);
    }

    private String field(String body, String field) throws Exception {
        return objectMapper.readTree(body).path("data").path(field).asText();
    }

    private String randomEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
