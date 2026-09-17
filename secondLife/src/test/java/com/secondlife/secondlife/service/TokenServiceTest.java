package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.TokenResponse;
import com.secondlife.secondlife.entity.EmailVerificationToken;
import com.secondlife.secondlife.entity.PasswordResetToken;
import com.secondlife.secondlife.entity.RefreshToken;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.UnauthorizedException;
import com.secondlife.secondlife.repository.EmailVerificationTokenRepository;
import com.secondlife.secondlife.repository.PasswordResetTokenRepository;
import com.secondlife.secondlife.repository.RefreshTokenRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.impl.TokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private TokenServiceImpl tokenService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(tokenService, "refreshExpirationMs", 604800000L);
        ReflectionTestUtils.setField(tokenService, "emailVerificationExpirationMs", 86400000L);
        ReflectionTestUtils.setField(tokenService, "passwordResetExpirationMs", 3600000L);

        userId = UUID.randomUUID();
        user = new User("user@example.com", "hash", AccountStatus.ACTIVE);
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, userId);
    }

    @Test
    void hashToken_ShouldProduceConsistentSha256Hex() {
        String token = "my-secret-token";
        String hash1 = tokenService.hashToken(token);
        String hash2 = tokenService.hashToken(token);

        assertNotNull(hash1);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // 256 bits = 64 hex chars
    }

    @Test
    void generateTokenPair_ShouldSaveHashedTokenAndReturnRaw() {
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token-jwt");
        when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);

        TokenResponse response = tokenService.generateTokenPair(user);

        assertNotNull(response);
        assertEquals("access-token-jwt", response.accessToken());
        assertNotNull(response.refreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotateRefreshToken_WhenValid_ShouldRevokeOldAndGenerateNew() {
        String rawToken = "raw-refresh-token";
        String hash = tokenService.hashToken(rawToken);

        RefreshToken oldToken = new RefreshToken(user, hash, Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(oldToken));
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("new-access-jwt");
        when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);

        TokenResponse response = tokenService.rotateRefreshToken(rawToken);

        assertNotNull(response);
        assertTrue(oldToken.isRevoked());
        verify(refreshTokenRepository, atLeastOnce()).save(oldToken);
    }

    @Test
    void rotateRefreshToken_WhenAlreadyRevoked_ShouldTriggerReplayDetection() {
        String rawToken = "replayed-token";
        String hash = tokenService.hashToken(rawToken);

        RefreshToken revokedToken = new RefreshToken(user, hash, Instant.now().plusSeconds(3600));
        revokedToken.revoke();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(revokedToken));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                tokenService.rotateRefreshToken(rawToken)
        );

        assertTrue(ex.getMessage().contains("revoked"));
        verify(refreshTokenRepository).revokeAllActiveByUserId(userId);
    }

    @Test
    void rotateRefreshToken_WhenExpired_ShouldThrowUnauthorized() {
        String rawToken = "expired-token";
        String hash = tokenService.hashToken(rawToken);

        RefreshToken expiredToken = new RefreshToken(user, hash, Instant.now().minusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(expiredToken));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                tokenService.rotateRefreshToken(rawToken)
        );

        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void verifyEmailToken_WhenValid_ShouldMarkUsedAndSetEmailVerified() {
        String rawToken = "email-token-123";
        String hash = tokenService.hashToken(rawToken);

        EmailVerificationToken token = new EmailVerificationToken(user, hash, Instant.now().plusSeconds(3600));
        when(emailVerificationTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        User verifiedUser = tokenService.verifyEmailToken(rawToken);

        assertTrue(verifiedUser.isEmailVerified());
        assertTrue(token.isUsed());
        verify(emailVerificationTokenRepository).save(token);
    }

    @Test
    void verifyEmailToken_WhenAlreadyUsed_ShouldThrowBadRequest() {
        String rawToken = "used-email-token";
        String hash = tokenService.hashToken(rawToken);

        EmailVerificationToken token = new EmailVerificationToken(user, hash, Instant.now().plusSeconds(3600));
        token.markAsUsed();
        when(emailVerificationTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        assertThrows(BadRequestException.class, () -> tokenService.verifyEmailToken(rawToken));
    }

    @Test
    void verifyAndConsumePasswordResetToken_WhenValid_ShouldMarkUsedAndReturnUser() {
        String rawToken = "reset-token-xyz";
        String hash = tokenService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, hash, Instant.now().plusSeconds(3600));
        when(passwordResetTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        User resetUser = tokenService.verifyAndConsumePasswordResetToken(rawToken);

        assertEquals(user.getEmail(), resetUser.getEmail());
        assertTrue(token.isUsed());
        verify(passwordResetTokenRepository).save(token);
    }
}
