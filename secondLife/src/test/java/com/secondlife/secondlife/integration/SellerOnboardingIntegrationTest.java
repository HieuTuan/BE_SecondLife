package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.EkycService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.ekyc.vnpt.VnptEkycOrchestrator;
import com.secondlife.secondlife.service.shipping.ShippingQuoteService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false",
        "app.ekyc.provider=VNPT", "app.vnpt.base-url=https://api.idg.vnpt.vn",
        "app.seller-onboarding.email.max-attempts=2"
})
class SellerOnboardingIntegrationTest {
    private static final GhnTestDatabase database = new GhnTestDatabase("SELLER_ONBOARDING_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("SELLER_ONBOARDING_TEST_JDBC_URL"); }
    @AfterAll static void stopOwnDatabase() { database.close(); }
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired SellerOnboardingRepository onboarding;
    @Autowired SellerVerificationRepository verifications;
    @Autowired JwtTokenProvider tokens;
    @Autowired PasswordEncoder passwords;
    @Autowired ShippingQuoteService shipping;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;
    @MockitoBean NotificationService notifications;
    @MockitoBean EkycService ekyc;
    @MockitoBean VnptEkycOrchestrator directEkyc;

    @Test void informationAndEmailAreRequiredForBothEkycEntryPoints() throws Exception {
        User buyer = buyer();
        mvc.perform(get("/api/seller-onboarding/me").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canStartEkyc").value(false));
        assertEkycBlocked(buyer);
        save(buyer, "Shop A", "shop@example.test");
        assertEkycBlocked(buyer);
        assertTrue(verifications.findTopByUserIdOrderBySubmittedAtDesc(buyer.getId()).isEmpty());
        verifyNoInteractions(ekyc, directEkyc);
    }

    @Test void successfulEmailConfirmationPersistsPickupAndUnlocksEkyc() throws Exception {
        User buyer = buyer();
        save(buyer, "Shop A", "shop@example.test");
        String otp = sendCode(buyer);
        assertTrue(passwords.matches(otp, onboarding.findById(buyer.getId()).orElseThrow().getEmailOtpHash()));
        mvc.perform(verifyRequest(buyer, otp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andExpect(jsonPath("$.data.canStartEkyc").value(true))
                .andExpect(jsonPath("$.data.nextStep").value("EKYC"))
                .andExpect(jsonPath("$.data.emailOtpHash").doesNotExist());
        assertNull(onboarding.findById(buyer.getId()).orElseThrow().getEmailOtpHash());
        assertEquals("Phường Ngã Năm", shipping.pickup(buyer.getId()).wardName());
        assertEquals("+84976404178", shipping.pickup(buyer.getId()).phone());
        when(directEkyc.verify(any(), any(), any(), anyString(), anyString(), anyInt()))
                .thenReturn(new VnptResults.Verification(UUID.randomUUID(), true, "PASSED", null, null, null, null, null));
        mvc.perform(directRequest().header("Authorization", bearer(buyer))).andExpect(status().isOk());
        when(ekyc.verifyIdentity(any())).thenReturn(EkycResult.providerError(ReasonCode.PROVIDER_UNAVAILABLE, "TEST", null, "Test provider unavailable"));
        mvc.perform(applicationRequest(buyer)).andExpect(status().isCreated());
        verify(ekyc).verifyIdentity(any());
        mvc.perform(put("/api/seller-onboarding/me").header("Authorization", bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON).content(profile("Changed shop", "shop@example.test")))
                .andExpect(status().isConflict());
    }

    @Test void wrongCodesCommitAttemptCounterAndLimitSurvivesSeparateRequests() throws Exception {
        User buyer = buyer(); save(buyer, "Shop A", "shop@example.test"); String otp = sendCode(buyer);
        String wrong = otp.equals("000000") ? "000001" : "000000";
        mvc.perform(verifyRequest(buyer, wrong)).andExpect(status().isBadRequest());
        assertEquals(1, onboarding.findById(buyer.getId()).orElseThrow().getEmailOtpAttempts());
        mvc.perform(verifyRequest(buyer, wrong)).andExpect(status().isBadRequest());
        assertEquals(2, onboarding.findById(buyer.getId()).orElseThrow().getEmailOtpAttempts());
        mvc.perform(verifyRequest(buyer, otp)).andExpect(status().isBadRequest());
        assertEkycBlocked(buyer);
    }

    @Test void expiresAndDoesNotAcceptCodeForAnotherUser() throws Exception {
        User owner = buyer(), other = buyer();
        save(owner, "Shop A", "shop@example.test"); save(other, "Shop B", "other@example.test");
        String otp = sendCode(owner);
        mvc.perform(verifyRequest(other, otp)).andExpect(status().isBadRequest());
        jdbc.update("update seller_onboarding set email_otp_expires_at = now() - interval '1 second' where user_id = ?", owner.getId());
        mvc.perform(verifyRequest(owner, otp)).andExpect(status().isBadRequest());
        assertNull(onboarding.findById(owner.getId()).orElseThrow().getEmailVerifiedAt());
    }

    @Test void replayAndEditingAfterVerificationRequireNewCode() throws Exception {
        User buyer = buyer(); save(buyer, "Shop A", "shop@example.test"); String otp = sendCode(buyer);
        mvc.perform(verifyRequest(buyer, otp)).andExpect(status().isOk());
        mvc.perform(verifyRequest(buyer, otp)).andExpect(status().isConflict());
        save(buyer, "Shop B", "new@example.test");
        mvc.perform(verifyRequest(buyer, otp)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/seller-onboarding/email/send-code").header("Authorization", bearer(buyer)))
                .andExpect(status().isConflict());
        assertEkycBlocked(buyer);
    }

    @Test void resendReplacesOldCodeAndUsesSavedEmail() throws Exception {
        User buyer = buyer(); save(buyer, "Shop A", "shop@example.test"); sendCode(buyer);
        String oldHash = onboarding.findById(buyer.getId()).orElseThrow().getEmailOtpHash();
        mvc.perform(post("/api/seller-onboarding/email/send-code").header("Authorization", bearer(buyer)))
                .andExpect(status().isConflict());
        jdbc.update("update seller_onboarding set email_otp_sent_at = now() - interval '61 seconds' where user_id = ?", buyer.getId());
        String fresh = sendCode(buyer);
        var row = onboarding.findById(buyer.getId()).orElseThrow();
        assertNotEquals(oldHash, row.getEmailOtpHash());
        assertTrue(passwords.matches(fresh, row.getEmailOtpHash()));
    }

    @Test void smtpFailureReturns502AndRollsBackCodeWithoutLeakingProviderError() throws Exception {
        User buyer = buyer(); save(buyer, "Shop A", "shop@example.test");
        doThrow(new MailSendException("private-smtp-error")).when(mail).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/seller-onboarding/email/send-code").header("Authorization", bearer(buyer)))
                .andExpect(status().isBadGateway()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-smtp-error"))));
        var row = onboarding.findById(buyer.getId()).orElseThrow();
        assertNull(row.getEmailOtpHash());
        assertNull(row.getEmailOtpSentAt());
        assertNull(row.getEmailVerifiedAt());
    }

    @Test void validatesInformationAndRequiresAuthenticationAndBuyerPermission() throws Exception {
        mvc.perform(get("/api/seller-onboarding/me")).andExpect(status().isUnauthorized());
        User buyer = buyer();
        mvc.perform(put("/api/seller-onboarding/me").header("Authorization", bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON).content(profile("a".repeat(31), "shop@example.test")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/seller-onboarding/me").header("Authorization", bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON).content(profile("Shop A", "invalid-email")))
                .andExpect(status().isBadRequest());
        User staff = user("STAFF");
        mvc.perform(put("/api/seller-onboarding/me").header("Authorization", bearer(staff))
                .contentType(MediaType.APPLICATION_JSON).content(profile("Shop A", "shop@example.test")))
                .andExpect(status().isForbidden());
    }

    private void assertEkycBlocked(User buyer) throws Exception {
        mvc.perform(applicationRequest(buyer)).andExpect(status().isConflict());
        mvc.perform(directRequest().header("Authorization", bearer(buyer))).andExpect(status().isConflict());
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder applicationRequest(User buyer) {
        return post("/api/seller-verifications").header("Authorization", bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"verificationType\":\"CITIZEN_ID\",\"documentNumber\":\"012345678901\",\"documentFrontUrl\":\"https://example.test/f.jpg\",\"documentBackUrl\":\"https://example.test/b.jpg\"}");
    }
    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder directRequest() {
        return multipart("/api/v1/ekyc/verify")
                .file(new MockMultipartFile("frontImage", "front.jpg", "image/jpeg", new byte[]{1}))
                .file(new MockMultipartFile("backImage", "back.jpg", "image/jpeg", new byte[]{1}))
                .file(new MockMultipartFile("selfieImage", "selfie.jpg", "image/jpeg", new byte[]{1}))
                .param("clientSession", "test-session").param("token", "test-request");
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder verifyRequest(User user, String otp) {
        return post("/api/seller-onboarding/email/verify").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"" + otp + "\"}");
    }
    private void save(User buyer, String name, String email) throws Exception {
        mvc.perform(put("/api/seller-onboarding/me").header("Authorization", bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON).content(profile(name, email)))
                .andExpect(status().isOk());
    }
    private String sendCode(User buyer) throws Exception {
        mvc.perform(post("/api/seller-onboarding/email/send-code").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.otp").doesNotExist());
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(messages.capture());
        var sent = messages.getAllValues().getLast();
        assertEquals(onboarding.findById(buyer.getId()).orElseThrow().getEmail(), sent.getTo()[0]);
        var matcher = Pattern.compile("[0-9]{6}").matcher(sent.getText());
        assertTrue(matcher.find()); return matcher.group();
    }
    private String profile(String name, String email) {
        return """
                {"shopName":"%s","email":"%s","phone":"+84976404178",
                 "pickupAddress":{"name":"Nguyễn A","phone":"+84976404178","address":"123, Ấp Vĩnh Thành",
                 "provinceName":"Cần Thơ","wardName":"Phường Ngã Năm","newAddress":true}}
                """.formatted(name, email);
    }
    private User buyer() { return user("BUYER"); }
    private User user(String role) {
        var user = new User("onboarding-" + UUID.randomUUID() + "@example.test", passwords.encode("Password@123"), AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        user.addRole(roles.findByCode(role).orElseThrow());
        return users.saveAndFlush(user);
    }
    private String bearer(User user) { return "Bearer " + tokens.generateAccessToken(user); }
}
