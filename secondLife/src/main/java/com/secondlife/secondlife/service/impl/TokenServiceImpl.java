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
    private final JwtTokenProvider jwtTokenProvider;

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
    public String createEmailVerificationToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);
        Instant expiry = Instant.now().plusMillis(emailVerificationExpirationMs);

        EmailVerificationToken token = new EmailVerificationToken(user, hashedToken, expiry);
        emailVerificationTokenRepository.save(token);
        return rawToken;
    }

    @Override
    @Transactional
    public User verifyEmailToken(String rawToken) {
        String hashedToken = hashToken(rawToken);
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new BadRequestException("Invalid or expired email verification token"));

        if (!token.isValid()) {
            throw new BadRequestException("Email verification token is already used or has expired");
        }

        token.markAsUsed();
        emailVerificationTokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        return user;
    }

    @Override
    @Transactional
    public String createPasswordResetToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);
        Instant expiry = Instant.now().plusMillis(passwordResetExpirationMs);

        PasswordResetToken token = new PasswordResetToken(user, hashedToken, expiry);
        passwordResetTokenRepository.save(token);
        return rawToken;
    }

    @Override
    @Transactional
    public User verifyAndConsumePasswordResetToken(String rawToken) {
        String hashedToken = hashToken(rawToken);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token"));

        if (!token.isValid()) {
            throw new BadRequestException("Password reset token is already used or has expired");
        }

        token.markAsUsed();
        passwordResetTokenRepository.save(token);

        return token.getUser();
    }
}
