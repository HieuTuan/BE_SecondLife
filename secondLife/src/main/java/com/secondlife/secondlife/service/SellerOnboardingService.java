package com.secondlife.secondlife.service;

import com.secondlife.secondlife.config.SellerOnboardingProperties;
import com.secondlife.secondlife.dto.request.SellerOnboardingRequest;
import com.secondlife.secondlife.dto.response.SellerOnboardingEmailCodeResponse;
import com.secondlife.secondlife.dto.response.SellerOnboardingResponse;
import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import com.secondlife.secondlife.entity.SellerOnboarding;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.shipping.ShippingQuoteService;
import com.secondlife.secondlife.service.shipping.GhnPickupAddressResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SellerOnboardingService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<SellerVerificationStatus> STARTED_VERIFICATION = Set.of(
            SellerVerificationStatus.SUBMITTED, SellerVerificationStatus.EKYC_PENDING,
            SellerVerificationStatus.RESUBMIT_REQUIRED, SellerVerificationStatus.NEEDS_REVIEW,
            SellerVerificationStatus.APPROVED);
    private final SellerOnboardingRepository onboarding;
    private final UserRepository users;
    private final SellerVerificationRepository verifications;
    private final ShippingQuoteService shipping;
    private final SellerOnboardingMailer mailer;
    private final SellerOnboardingProperties properties;
    private final PasswordEncoder passwords;
    private final ObjectMapper mapper;
    private final GhnPickupAddressResolver pickupAddresses;

    @Transactional(readOnly = true)
    public SellerOnboardingResponse get(UUID userId) {
        return onboarding.findById(userId).map(this::response)
                .orElseGet(() -> new SellerOnboardingResponse(null, null, null, null,
                        false, false, "SHOP_INFORMATION", null, null, null));
    }

    @Transactional
    public SellerOnboardingResponse save(UUID userId, SellerOnboardingRequest request) {
        User user = lockUser(userId);
        var row = onboarding.findById(userId).orElseGet(SellerOnboarding::new);
        String addressJson = mapper.writeValueAsString(pickupAddresses.resolve(request.pickupAddress()));
        boolean changed = !Objects.equals(row.getShopName(), request.shopName())
                || !Objects.equals(row.getEmail(), request.email())
                || !Objects.equals(row.getPhone(), request.phone())
                || !Objects.equals(row.getPickupAddressJson(), addressJson)
                || !Objects.equals(row.getPickupProvinceId(), request.pickupAddress().provinceId())
                || !Objects.equals(row.getPickupWardId(), request.pickupAddress().wardId());
        if (!changed) return response(row);
        if (user.hasRole("SELLER") || verifications.existsByUserIdAndStatusIn(userId, STARTED_VERIFICATION)) {
            throw new ConflictException("Shop information cannot be changed after seller verification has started");
        }
        row.setUserId(userId);
        row.setShopName(request.shopName());
        row.setEmail(request.email());
        row.setPhone(request.phone());
        row.setPickupAddressJson(addressJson);
        row.setPickupProvinceId(request.pickupAddress().provinceId());
        row.setPickupWardId(request.pickupAddress().wardId());
        row.setEmailVerifiedAt(null);
        clearCode(row);
        // Keep the last send time so changing email cannot bypass the resend cooldown.
        onboarding.save(row);
        return response(row);
    }

    @Transactional
    public SellerOnboardingEmailCodeResponse sendEmailCode(UUID userId) {
        lockUser(userId);
        var row = requiredProfile(userId);
        if (row.getEmailVerifiedAt() != null) throw new ConflictException("Shop email has already been verified");
        Instant now = Instant.now();
        if (row.getEmailOtpSentAt() != null
                && now.isBefore(row.getEmailOtpSentAt().plusSeconds(properties.getResendCooldownSeconds()))) {
            throw new ConflictException("Please wait before requesting another email verification code");
        }
        String otp = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        row.setEmailOtpHash(passwords.encode(otp));
        row.setEmailOtpExpiresAt(now.plusSeconds(properties.getOtpTtlSeconds()));
        row.setEmailOtpSentAt(now);
        row.setEmailOtpAttempts(0);
        onboarding.saveAndFlush(row);
        // Delivery failure rolls back the new code, rather than reporting success without sending it.
        mailer.sendCode(row.getEmail(), otp, row.getEmailOtpExpiresAt());
        return new SellerOnboardingEmailCodeResponse(row.getEmail(), row.getEmailOtpExpiresAt(),
                now.plusSeconds(properties.getResendCooldownSeconds()));
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public SellerOnboardingResponse verifyEmailCode(UUID userId, String otp) {
        lockUser(userId);
        var row = requiredProfile(userId);
        if (row.getEmailVerifiedAt() != null) throw new ConflictException("Shop email has already been verified");
        if (row.getEmailOtpHash() == null || row.getEmailOtpExpiresAt() == null
                || !Instant.now().isBefore(row.getEmailOtpExpiresAt())) {
            throw new BadRequestException("Email verification code is missing or expired");
        }
        if (row.getEmailOtpAttempts() >= properties.getMaxAttempts()) {
            throw new BadRequestException("Too many incorrect codes. Request a new email verification code");
        }
        if (otp == null || !otp.matches("[0-9]{6}") || !passwords.matches(otp, row.getEmailOtpHash())) {
            row.setEmailOtpAttempts(row.getEmailOtpAttempts() + 1);
            onboarding.saveAndFlush(row);
            throw new BadRequestException("Email verification code is incorrect");
        }
        row.setEmailVerifiedAt(Instant.now());
        clearCode(row);
        onboarding.save(row);
        shipping.savePickup(userId, mapper.readValue(row.getPickupAddressJson(), ShippingAddress.class));
        return response(row);
    }

    @Transactional
    public void requireCompleted(UUID userId) {
        lockUser(userId);
        var row = requiredProfile(userId);
        if (row.getEmailVerifiedAt() == null) {
            throw new ConflictException("Verify the shop email before starting eKYC");
        }
    }

    private User lockUser(UUID userId) {
        return users.findByIdForRoleUpdate(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private SellerOnboarding requiredProfile(UUID userId) {
        return onboarding.findById(userId).orElseThrow(() ->
                new ConflictException("Complete shop information and verify its email before starting eKYC"));
    }

    private void clearCode(SellerOnboarding row) {
        row.setEmailOtpHash(null);
        row.setEmailOtpExpiresAt(null);
        row.setEmailOtpAttempts(0);
    }

    private SellerOnboardingResponse response(SellerOnboarding row) {
        boolean verified = row.getEmailVerifiedAt() != null;
        return new SellerOnboardingResponse(row.getShopName(),
                mapper.readValue(row.getPickupAddressJson(), ShippingAddress.class), row.getEmail(), row.getPhone(),
                verified, verified, verified ? "EKYC" : "EMAIL_VERIFICATION", row.getEmailVerifiedAt(),
                row.getPickupProvinceId(), row.getPickupWardId());
    }

}
