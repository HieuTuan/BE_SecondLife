package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.GoogleLoginRequest;
import com.secondlife.secondlife.dto.request.LoginRequest;
import com.secondlife.secondlife.dto.request.RefreshTokenRequest;
import com.secondlife.secondlife.dto.request.RegisterRequest;
import com.secondlife.secondlife.dto.request.ResetPasswordRequest;
import com.secondlife.secondlife.dto.request.VerifyEmailRequest;
import com.secondlife.secondlife.dto.response.AuthResponse;
import com.secondlife.secondlife.dto.response.TokenResponse;
import com.secondlife.secondlife.dto.response.UserSummaryResponse;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_WhenValid_ShouldReturn201WithApiResponse() throws Exception {
        UserSummaryResponse summary = new UserSummaryResponse(
                UUID.randomUUID(), "buyer@example.com", "John Doe", "1234567890", null, AccountStatus.ACTIVE, false
        );
        AuthResponse authResponse = new AuthResponse("jwt-access", "raw-refresh", 900000L, summary, Set.of("BUYER"), Set.of("PROFILE_READ_SELF"));

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        String jsonBody = """
                {
                    "email": "buyer@example.com",
                    "password": "Password@123",
                    "fullName": "John Doe",
                    "phone": "1234567890"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-access"))
                .andExpect(jsonPath("$.data.user.email").value("buyer@example.com"));
    }

    @Test
    void register_WhenWeakPassword_ShouldReturn400WithFieldErrors() throws Exception {
        String jsonBody = """
                {
                    "email": "buyer@example.com",
                    "password": "weak",
                    "fullName": "John Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void register_WhenEmailAlreadyExists_ShouldReturn409Conflict() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("Email already registered: buyer@example.com"));

        String jsonBody = """
                {
                    "email": "buyer@example.com",
                    "password": "Password@123",
                    "fullName": "John Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email already registered: buyer@example.com"));
    }

    @Test
    void login_WhenValid_ShouldReturn200WithTokens() throws Exception {
        UserSummaryResponse summary = new UserSummaryResponse(
                UUID.randomUUID(), "buyer@example.com", "John Doe", null, null, AccountStatus.ACTIVE, true
        );
        AuthResponse authResponse = new AuthResponse("access-token-jwt", "refresh-token-raw", 900000L, summary, Set.of("BUYER"), Set.of());

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        String jsonBody = """
                {
                    "email": "buyer@example.com",
                    "password": "Password@123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-jwt"));
    }

    @Test
    void refresh_WhenValid_ShouldReturn200WithNewTokens() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class)))
                .thenReturn(new TokenResponse("new-access-jwt", "new-refresh-raw", 900000L));

        String jsonBody = """
                {
                    "refreshToken": "some-refresh-token"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-jwt"));
    }

    @Test
    void googleLogin_WhenValid_ShouldReturn200WithAuthResponse() throws Exception {
        UserSummaryResponse summary = new UserSummaryResponse(
                UUID.randomUUID(), "google@example.com", "Google User", null, "http://avatar.jpg", AccountStatus.ACTIVE, true
        );
        AuthResponse authResponse = new AuthResponse("jwt-access", "raw-refresh", 900000L, summary, Set.of("BUYER"), Set.of("PROFILE_READ_SELF"));

        when(authService.loginWithGoogle(any(GoogleLoginRequest.class))).thenReturn(authResponse);

        String jsonBody = """
                {
                    "idToken": "valid-google-id-token"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value("google@example.com"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-access"));
    }

    @Test
    void verifyEmail_WhenValid_ShouldReturn200() throws Exception {
        doNothing().when(authService).verifyEmail(any(VerifyEmailRequest.class));

        String jsonBody = """
                {
                    "email": "user@example.com",
                    "otp": "123456"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void resetPassword_WhenValid_ShouldReturn200() throws Exception {
        doNothing().when(authService).resetPassword(any(ResetPasswordRequest.class));

        String jsonBody = """
                {
                    "email": "user@example.com",
                    "otp": "123456",
                    "newPassword": "NewPassword@123",
                    "confirmPassword": "NewPassword@123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
