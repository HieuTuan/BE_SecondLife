package com.secondlife.secondlife.service;

import com.secondlife.secondlife.config.SellerOnboardingProperties;
import com.secondlife.secondlife.dto.request.SellerOnboardingRequest;
import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import com.secondlife.secondlife.entity.SellerOnboarding;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.shipping.ShippingQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellerOnboardingServiceTest {
    @Mock SellerOnboardingRepository onboarding;
    @Mock UserRepository users;
    @Mock SellerVerificationRepository verifications;
    @Mock ShippingQuoteService shipping;
    @Mock SellerOnboardingMailer mailer;
    final UUID userId = UUID.randomUUID();
    final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    SellerOnboardingService service;
    SellerOnboarding row;
    ShippingAddress address;

    @BeforeEach
    void setup() {
        var properties = new SellerOnboardingProperties();
        properties.setOtpTtlSeconds(600);
        properties.setResendCooldownSeconds(60);
        properties.setMaxAttempts(2);
        service = new SellerOnboardingService(onboarding, users, verifications, shipping, mailer, properties, passwords, mapper);
        address = new ShippingAddress("Nguyễn A", "+84976404178", "123, Ấp Vĩnh Thành",
                "Cần Thơ", null, "Phường Ngã Năm", null, null, true);
        row = new SellerOnboarding();
        row.setUserId(userId); row.setShopName("Shop A"); row.setEmail("shop@example.test");
        row.setPhone("+84976404178"); row.setPickupAddressJson(mapper.writeValueAsString(address));
        lenient().when(users.findByIdForRoleUpdate(userId)).thenReturn(Optional.of(new User()));
        lenient().when(onboarding.findById(userId)).thenReturn(Optional.of(row));
    }

    @Test void noProfileOrUnverifiedEmailCannotStartEkyc() {
        when(onboarding.findById(userId)).thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> service.requireCompleted(userId));
        when(onboarding.findById(userId)).thenReturn(Optional.of(row));
        assertThrows(ConflictException.class, () -> service.requireCompleted(userId));
        row.setEmailVerifiedAt(Instant.now());
        assertDoesNotThrow(() -> service.requireCompleted(userId));
    }

    @Test void savesNormalizedInformationAndInvalidatesConfirmationWhenChanged() {
        row.setEmailVerifiedAt(Instant.now());
        row.setEmailOtpHash(passwords.encode("123456"));
        row.setEmailOtpSentAt(Instant.now());
        var result = service.save(userId, new SellerOnboardingRequest("  Shop B  ", address, " NEW@example.test ", "+84976404178"));
        assertEquals("Shop B", result.shopName());
        assertEquals("new@example.test", result.email());
        assertFalse(result.canStartEkyc());
        assertNull(row.getEmailOtpHash());
        assertNotNull(row.getEmailOtpSentAt());
        verify(onboarding).save(row);
    }

    @Test void identicalInformationKeepsEmailConfirmation() {
        row.setEmailVerifiedAt(Instant.now());
        assertTrue(service.save(userId, new SellerOnboardingRequest("Shop A", address, row.getEmail(), row.getPhone())).canStartEkyc());
        verify(onboarding, never()).save(any());
    }

    @Test void cannotChangeShopDuringAnActiveVerification() {
        when(verifications.existsByUserIdAndStatusIn(eq(userId), anyCollection())).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.save(userId,
                new SellerOnboardingRequest("Changed shop", address, row.getEmail(), row.getPhone())));
        verify(onboarding, never()).save(any());
    }

    @Test void codeIsHashedAndOnlyDeliveredToSavedEmail() {
        var result = service.sendEmailCode(userId);
        var otp = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailer).sendCode(eq("shop@example.test"), otp.capture(), eq(result.expiresAt()));
        assertTrue(otp.getValue().matches("[0-9]{6}"));
        assertNotEquals(otp.getValue(), row.getEmailOtpHash());
        assertTrue(passwords.matches(otp.getValue(), row.getEmailOtpHash()));
        assertEquals(600, result.expiresAt().getEpochSecond() - row.getEmailOtpSentAt().getEpochSecond());
        assertEquals(60, result.resendAvailableAt().getEpochSecond() - row.getEmailOtpSentAt().getEpochSecond());
    }

    @Test void cooldownStopsRepeatedSendsIncludingAfterEmailChange() {
        row.setEmailOtpSentAt(Instant.now());
        service.save(userId, new SellerOnboardingRequest("Shop A", address, "other@example.test", row.getPhone()));
        assertThrows(ConflictException.class, () -> service.sendEmailCode(userId));
        verifyNoInteractions(mailer);
    }

    @Test void correctCodeEnablesEkycAndSavesPickupAddress() {
        pendingCode();
        var result = service.verifyEmailCode(userId, "123456");
        assertTrue(result.emailVerified());
        assertTrue(result.canStartEkyc());
        assertEquals("EKYC", result.nextStep());
        assertNull(row.getEmailOtpHash());
        verify(shipping).savePickup(userId, address);
        assertThrows(ConflictException.class, () -> service.verifyEmailCode(userId, "123456"));
    }

    @Test void incorrectCodesPersistAttemptsAndLockOutCorrectCodeAtLimit() {
        pendingCode();
        assertThrows(BadRequestException.class, () -> service.verifyEmailCode(userId, "000000"));
        assertThrows(BadRequestException.class, () -> service.verifyEmailCode(userId, "000000"));
        assertEquals(2, row.getEmailOtpAttempts());
        verify(onboarding, times(2)).saveAndFlush(row);
        assertThrows(BadRequestException.class, () -> service.verifyEmailCode(userId, "123456"));
        assertNull(row.getEmailVerifiedAt());
        verifyNoInteractions(shipping);
    }

    @Test void expiredCodeCannotVerify() {
        pendingCode(); row.setEmailOtpExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(BadRequestException.class, () -> service.verifyEmailCode(userId, "123456"));
        verifyNoInteractions(shipping);
    }

    @Test void changedInformationInvalidatesPreviouslySentCode() {
        pendingCode();
        service.save(userId, new SellerOnboardingRequest("Changed shop", address, row.getEmail(), row.getPhone()));
        assertThrows(BadRequestException.class, () -> service.verifyEmailCode(userId, "123456"));
        verifyNoInteractions(shipping);
    }

    private void pendingCode() {
        row.setEmailOtpHash(passwords.encode("123456"));
        row.setEmailOtpExpiresAt(Instant.now().plusSeconds(600));
    }
}
