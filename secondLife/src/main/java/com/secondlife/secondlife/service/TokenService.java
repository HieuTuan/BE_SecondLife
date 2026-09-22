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

    String createEmailVerificationOtp(User user);

    User verifyEmailOtp(String email, String rawOtp);

    String createPasswordResetOtp(User user);

    User verifyAndConsumePasswordResetOtp(String email, String rawOtp);
}
