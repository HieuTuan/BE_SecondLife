package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.TokenResponse;
import com.secondlife.secondlife.entity.User;

import java.util.UUID;

public interface TokenService {

    String hashToken(String rawToken);

    TokenResponse generateTokenPair(User user);

    TokenResponse rotateRefreshToken(String rawRefreshToken);

    void revokeRefreshToken(String rawRefreshToken);

    void revokeAllUserRefreshTokens(UUID userId);

    String createEmailVerificationToken(User user);

    User verifyEmailToken(String rawToken);

    String createPasswordResetToken(User user);

    User verifyAndConsumePasswordResetToken(String rawToken);
}
