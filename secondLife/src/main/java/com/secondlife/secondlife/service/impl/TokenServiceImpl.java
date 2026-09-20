package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.TokenResponse;
import com.secondlife.secondlife.entity.EmailVerificationToken;
import com.secondlife.secondlife.entity.PasswordResetToken;
import com.secondlife.secondlife.entity.RefreshToken;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.UnauthorizedException;
import com.secondlife.secondlife.repository.EmailVerificationTokenRepository;
import com.secondlife.secondlife.repository.PasswordResetTokenRepository;
import com.secondlife.secondlife.repository.RefreshTokenRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.auth.email-verification-expiration-ms}")
    private long emailVerificationExpirationMs;

    @Value("${app.auth.password-reset-expiration-ms}")
    private long passwordResetExpirationMs;

    @Override
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    @Transactional
    public TokenResponse generateTokenPair(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        String rawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String hashedRefreshToken = hashToken(rawRefreshToken);
        Instant expiry = Instant.now().plusMillis(refreshExpirationMs);

        RefreshToken refreshToken = new RefreshToken(user, hashedRefreshToken, expiry);
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(accessToken, rawRefreshToken, jwtTokenProvider.getAccessExpirationMs());
    }

    @Override
    @Transactional
    public TokenResponse rotateRefreshToken(String rawRefreshToken) {
        String hashedToken = hashToken(rawRefreshToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // Replay detection: If token was already revoked, revoke all tokens for this user
        if (token.isRevoked()) {
            log.warn("Revoked refresh token reuse attempt detected for user: {}. Revoking all active tokens.",
                    token.getUser().getId());
            refreshTokenRepository.revokeAllActiveByUserId(token.getUser().getId());
            throw new UnauthorizedException("Refresh token was revoked. All active sessions have been invalidated. Please login again.");
        }

        if (token.isExpired()) {
            token.revoke();
            refreshTokenRepository.save(token);
            throw new UnauthorizedException("Refresh token has expired. Please login again.");
        }

        User user = token.getUser();
        if (user.getAccountStatus() == AccountStatus.LOCKED || user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new ForbiddenException("Account is " + user.getAccountStatus().name().toLowerCase() + ". Access denied.");
        }

        // Revoke old token
        token.revoke();
        refreshTokenRepository.save(token);

        // Issue new pair
        return generateTokenPair(user);
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        String hashedToken = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hashedToken).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
    }

    @Override
    @Transactional
    public void revokeAllUserRefreshTokens(UUID userId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId);
    }

    @Override
    @Transactional
    public String createEmailVerificationOtp(User user) {
        // Invalidate old active OTP
        emailVerificationTokenRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .ifPresent(EmailVerificationToken::markAsUsed);

        int code = 100000 + secureRandom.nextInt(900000);
        String rawOtp = String.valueOf(code);
        String hashedToken = hashToken(user.getId().toString() + ":" + rawOtp);
        Instant expiry = Instant.now().plusMillis(emailVerificationExpirationMs);

        EmailVerificationToken token = new EmailVerificationToken(user, hashedToken, expiry);
        emailVerificationTokenRepository.save(token);
        return rawOtp;
    }

    @Override
    @Transactional
    public User verifyEmailOtp(String email, String rawOtp) {
        if (email == null || email.isBlank() || rawOtp == null || rawOtp.isBlank()) {
            throw new BadRequestException("Email và mã OTP là bắt buộc");
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("Tài khoản không tồn tại hoặc mã OTP không chính xác"));

        String hashedToken = hashToken(user.getId().toString() + ":" + rawOtp.trim());
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new BadRequestException("Mã OTP không chính xác hoặc đã hết hạn"));

        if (!token.isValid()) {
            throw new BadRequestException("Mã OTP đã được sử dụng hoặc đã hết hiệu lực");
        }

        token.markAsUsed();
        emailVerificationTokenRepository.save(token);

        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public String createPasswordResetOtp(User user) {
        // Invalidate old active OTP
        passwordResetTokenRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .ifPresent(PasswordResetToken::markAsUsed);

        int code = 100000 + secureRandom.nextInt(900000);
        String rawOtp = String.valueOf(code);
        String hashedToken = hashToken(user.getId().toString() + ":" + rawOtp);
        Instant expiry = Instant.now().plusMillis(passwordResetExpirationMs);

        PasswordResetToken token = new PasswordResetToken(user, hashedToken, expiry);
        passwordResetTokenRepository.save(token);
        return rawOtp;
    }

    @Override
    @Transactional
    public User verifyAndConsumePasswordResetOtp(String email, String rawOtp) {
        if (email == null || email.isBlank() || rawOtp == null || rawOtp.isBlank()) {
            throw new BadRequestException("Email và mã OTP là bắt buộc");
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("Tài khoản không tồn tại hoặc mã OTP không chính xác"));

        String hashedToken = hashToken(user.getId().toString() + ":" + rawOtp.trim());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new BadRequestException("Mã OTP không chính xác hoặc đã hết hạn"));

        if (!token.isValid()) {
            throw new BadRequestException("Mã OTP đã được sử dụng hoặc đã hết hiệu lực");
        }

        token.markAsUsed();
        passwordResetTokenRepository.save(token);

        return user;
    }
}
