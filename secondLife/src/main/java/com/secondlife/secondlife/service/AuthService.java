package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.*;
import com.secondlife.secondlife.dto.response.AuthResponse;
import com.secondlife.secondlife.dto.response.TokenResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    TokenResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    void verifyEmail(VerifyEmailRequest request);

    void resendVerification(ResendVerificationRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
