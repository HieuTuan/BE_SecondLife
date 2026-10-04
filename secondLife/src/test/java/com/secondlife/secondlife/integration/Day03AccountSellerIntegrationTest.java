package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.entity.IdentityRestriction;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.IdentityRestrictionRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.RoleAssignmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class Day03AccountSellerIntegrationTest {

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
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private IdentityRestrictionRepository identityRestrictionRepository;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private com.secondlife.secondlife.service.SellerOnboardingService onboarding;

    @Test
    void profileRoutesUseOnlyAuthenticatedOwner() throws Exception {
        String firstEmail = randomEmail("profile-a");
        String secondEmail = randomEmail("profile-b");
        String firstToken = field(register(firstEmail), "accessToken");
        String secondToken = field(register(secondEmail), "accessToken");

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Owner Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(firstEmail))
                .andExpect(jsonPath("$.data.fullName").value("Owner Updated"));
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(secondEmail))
                .andExpect(jsonPath("$.data.fullName").value("Day Three Buyer"));
    }

    @Test
    void verificationResetAndChangePasswordRevokeRefreshTokens() throws Exception {
        String email = randomEmail("account");
        String registration = register(email);
        String initialRefresh = field(registration, "refreshToken");

        clearInvocations(notificationService);
        mockMvc.perform(post("/api/auth/email-verification/send")
                        .contentType(MediaType.APPLICATION_JSON).content(emailJson(email)))
                .andExpect(status().isOk());
        ArgumentCaptor<String> verificationOtp = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendVerificationOtp(eq(email), anyString(), verificationOtp.capture());
        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"otp\":\"%s\"}".formatted(email, verificationOtp.getValue())))
                .andExpect(status().isOk());
        assertTrue(userRepository.findByEmailIgnoreCase(email).orElseThrow().isEmailVerified());

        clearInvocations(notificationService);
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON).content(emailJson(email)))
                .andExpect(status().isOk());
        ArgumentCaptor<String> resetOtp = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendPasswordResetOtp(eq(email), anyString(), resetOtp.capture());
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s","newPassword":"Reset@12345","confirmPassword":"Reset@12345"}
                                """.formatted(email, resetOtp.getValue())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(initialRefresh)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", bearer(field(registration, "accessToken"))))
                .andExpect(status().isUnauthorized());

        String login = login(email, "Reset@12345");
        String accessToken = field(login, "accessToken");
        String refreshToken = field(login, "refreshToken");
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON).content(changeJson("Reset@12345", "Changed@12345")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON).content(changeJson("wrong", "Changed@12345")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON).content(changeJson("Reset@12345", "Changed@12345")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(refreshToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isUnauthorized());
        login(email, "Changed@12345");
    }

    @Test
    void sellerVerificationIsOwnedByApplicantAndRestrictionTableExists() throws Exception {
        assertEquals("identity_restrictions", jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.identity_restrictions')::text", String.class));
        IdentityRestriction restriction = new IdentityRestriction();
        restriction.setDocumentNumberHash("a".repeat(64));
        restriction.setReasonCode("MANUAL_REVIEW");
        UUID restrictionId = identityRestrictionRepository.saveAndFlush(restriction).getId();
        assertEquals("MANUAL_REVIEW", identityRestrictionRepository.findById(restrictionId)
                .orElseThrow().getReasonCode());
        String applicantEmail = randomEmail("applicant");
        String applicantToken = field(register(applicantEmail), "accessToken");
        String otherToken = field(register(randomEmail("other")), "accessToken");

        mockMvc.perform(post("/api/seller-verifications")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/seller-verifications/me").header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/seller-verifications")
                        .header("Authorization", bearer(applicantToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/seller-verifications")
                        .header("Authorization", bearer(applicantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"verificationType":"CITIZEN_ID","documentNumber":"012345677702",
                                 "documentFrontUrl":"https://example.test/front.jpg",
                                 "documentBackUrl":"https://example.test/back.jpg",
                                 "selfieUrl":"https://example.test/selfie.jpg"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("EKYC_PENDING"))
                .andExpect(jsonPath("$.data.ekycStatus").value("PROVIDER_ERROR"));
        mockMvc.perform(get("/api/seller-verifications/me").header("Authorization", bearer(applicantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EKYC_PENDING"));
        mockMvc.perform(get("/api/seller-verifications/me").header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        UUID applicantId = userRepository.findByEmailIgnoreCase(applicantEmail).orElseThrow().getId();
        assertFalse(userRepository.findByIdWithAuthorities(applicantId).orElseThrow().hasRole("SELLER"));
    }

    @Test
    void internalRoleAssignmentIsIdempotentAndRevokesRefreshOnChange() throws Exception {
        String email = randomEmail("role");
        String registration = register(email);
        String accessToken = field(registration, "accessToken");
        String refreshToken = field(registration, "refreshToken");
        UUID userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();

        roleAssignmentService.grantRole(userId, RoleCode.INSPECTOR);
        roleAssignmentService.grantRole(userId, RoleCode.INSPECTOR);
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isUnauthorized());
        assertTrue(userRepository.findByIdWithAuthorities(userId).orElseThrow().hasRole("INSPECTOR"));
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(refreshToken)))
                .andExpect(status().isUnauthorized());

        roleAssignmentService.removeRole(userId, RoleCode.INSPECTOR);
        roleAssignmentService.removeRole(userId, RoleCode.INSPECTOR);
        assertFalse(userRepository.findByIdWithAuthorities(userId).orElseThrow().hasRole("INSPECTOR"));

        roleAssignmentService.grantRole(userId, RoleCode.ADMIN);
        assertThrows(ConflictException.class,
                () -> roleAssignmentService.removeRole(userId, RoleCode.ADMIN));
    }

    private String register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Password@123\",\"fullName\":\"Day Three Buyer\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String field(String body, String fieldName) throws Exception {
        return objectMapper.readTree(body).path("data").path(fieldName).asText();
    }

    private String randomEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String emailJson(String email) {
        return "{\"email\":\"%s\"}".formatted(email);
    }

    private String refreshJson(String token) {
        return "{\"refreshToken\":\"%s\"}".formatted(token);
    }

    private String changeJson(String oldPassword, String newPassword) {
        return "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                .formatted(oldPassword, newPassword, newPassword);
    }
}
