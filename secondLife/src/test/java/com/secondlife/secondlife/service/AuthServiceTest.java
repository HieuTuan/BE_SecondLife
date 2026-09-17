package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.LoginRequest;
import com.secondlife.secondlife.dto.request.RegisterRequest;
import com.secondlife.secondlife.dto.request.ResetPasswordRequest;
import com.secondlife.secondlife.dto.response.AuthResponse;
import com.secondlife.secondlife.dto.response.TokenResponse;
import com.secondlife.secondlife.dto.response.UserSummaryResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    private Role buyerRole;
    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        buyerRole = new Role(RoleCode.BUYER.name(), "Buyer", "Standard buyer");
        userId = UUID.randomUUID();
        user = new User("buyer@example.com", "encoded-pass", AccountStatus.ACTIVE);
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, userId);
    }

    @Test
    void register_WhenValid_ShouldAssignBuyerRoleAndReturnAuthResponse() {
        RegisterRequest request = new RegisterRequest("new@example.com", "Pass@1234", "John Doe", "1234567890");

        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(roleRepository.findByCodeWithPermissions(RoleCode.BUYER.name())).thenReturn(Optional.of(buyerRole));
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-pass");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(tokenService.createEmailVerificationToken(any(User.class))).thenReturn("verify-token");
        when(tokenService.generateTokenPair(any(User.class))).thenReturn(new TokenResponse("access-jwt", "refresh-raw", 900000L));

        UserSummaryResponse summary = new UserSummaryResponse(userId, "new@example.com", "John Doe", "1234567890", null, AccountStatus.ACTIVE, false);
        when(userMapper.toSummaryResponse(user)).thenReturn(summary);
        when(userMapper.extractRoleCodes(user)).thenReturn(Set.of("BUYER"));
        when(userMapper.extractPermissionCodes(user)).thenReturn(Set.of("PROFILE_READ_SELF"));

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("access-jwt", response.accessToken());
        assertEquals("refresh-raw", response.refreshToken());
        assertTrue(response.roles().contains("BUYER"));
        verify(userRepository).save(any(User.class));
        verify(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void register_WhenEmailAlreadyExists_ShouldThrowConflictException() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "Pass@1234", "John Doe", null);
        when(userRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_WhenValid_ShouldReturnAuthResponse() {
        LoginRequest request = new LoginRequest("buyer@example.com", "Pass@1234");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmailWithAuthoritiesIgnoreCase("buyer@example.com"))
                .thenReturn(Optional.of(user));
        when(tokenService.generateTokenPair(user))
                .thenReturn(new TokenResponse("access-jwt", "refresh-raw", 900000L));

        UserSummaryResponse summary = new UserSummaryResponse(userId, "buyer@example.com", "Buyer Name", null, null, AccountStatus.ACTIVE, true);
        when(userMapper.toSummaryResponse(user)).thenReturn(summary);
        when(userMapper.extractRoleCodes(user)).thenReturn(Set.of("BUYER"));
        when(userMapper.extractPermissionCodes(user)).thenReturn(Set.of("PROFILE_READ_SELF"));

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-jwt", response.accessToken());
        verify(userRepository).save(user);
    }

    @Test
    void login_WhenAccountLocked_ShouldThrowForbiddenException() {
        user.setAccountStatus(AccountStatus.LOCKED);
        LoginRequest request = new LoginRequest("buyer@example.com", "Pass@1234");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmailWithAuthoritiesIgnoreCase("buyer@example.com"))
                .thenReturn(Optional.of(user));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("locked"));
    }

    @Test
    void login_WhenAccountDisabled_ShouldThrowForbiddenException() {
        user.setAccountStatus(AccountStatus.DISABLED);
        LoginRequest request = new LoginRequest("buyer@example.com", "Pass@1234");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmailWithAuthoritiesIgnoreCase("buyer@example.com"))
                .thenReturn(Optional.of(user));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    @Test
    void login_WhenInvalidCredentials_ShouldThrowBadCredentialsException() {
        LoginRequest request = new LoginRequest("buyer@example.com", "WrongPass@123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void resetPassword_WhenPasswordsDoNotMatch_ShouldThrowBadRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest("token-123", "Pass@1234", "Pass@5678");

        assertThrows(BadRequestException.class, () -> authService.resetPassword(request));
    }

    @Test
    void resetPassword_WhenValid_ShouldUpdateHashAndRevokeAllSessions() {
        ResetPasswordRequest request = new ResetPasswordRequest("token-123", "Pass@1234", "Pass@1234");

        when(tokenService.verifyAndConsumePasswordResetToken("token-123")).thenReturn(user);
        when(passwordEncoder.encode("Pass@1234")).thenReturn("new-encoded-pass");

        authService.resetPassword(request);

        assertEquals("new-encoded-pass", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(tokenService).revokeAllUserRefreshTokens(userId);
    }
}
