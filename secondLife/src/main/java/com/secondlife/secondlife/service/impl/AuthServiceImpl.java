package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.GoogleUserInfo;
import com.secondlife.secondlife.dto.request.*;
import com.secondlife.secondlife.dto.response.AuthResponse;
import com.secondlife.secondlife.dto.response.TokenResponse;
import com.secondlife.secondlife.dto.response.UserSummaryResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.AuthService;
import com.secondlife.secondlife.service.GoogleAuthService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final NotificationService notificationService;
    private final UserMapper userMapper;
    private final GoogleAuthService googleAuthService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Email already registered: " + request.email());
        }

        Role buyerRole = roleRepository.findByCodeWithPermissions(RoleCode.BUYER.name())
                .orElseThrow(() -> new IllegalStateException("BUYER role not initialized"));

        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()), AccountStatus.ACTIVE);
        user.setEmailVerified(false);

        UserProfile profile = new UserProfile(user, request.fullName().trim(),
                request.phone() != null ? request.phone().trim() : null, null);
        user.setProfile(profile);
        user.addRole(buyerRole);

        User savedUser = userRepository.save(user);

        // Create email verification OTP & send async/gracefully via NotificationService
        String verificationOtp = tokenService.createEmailVerificationOtp(savedUser);
        notificationService.sendVerificationOtp(savedUser.getEmail(), profile.getFullName(), verificationOtp);

        TokenResponse tokenPair = tokenService.generateTokenPair(savedUser);
        UserSummaryResponse summary = userMapper.toSummaryResponse(savedUser);
        Set<String> roles = userMapper.extractRoleCodes(savedUser);
        Set<String> permissions = userMapper.extractPermissionCodes(savedUser);

        log.info("Registered new user with BUYER role: {}", savedUser.getEmail());
        return new AuthResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.accessTokenExpiresInMs(),
                summary,
                roles,
                permissions
        );
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        User user = userRepository.findByEmailWithAuthoritiesIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new NotFoundException("User not found: " + request.email()));

        if (user.getAccountStatus() == AccountStatus.LOCKED) {
            throw new ForbiddenException("Account is locked. Please contact support.");
        }
        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new ForbiddenException("Account is disabled. Please contact support.");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        TokenResponse tokenPair = tokenService.generateTokenPair(user);
        UserSummaryResponse summary = userMapper.toSummaryResponse(user);
        Set<String> roles = userMapper.extractRoleCodes(user);
        Set<String> permissions = userMapper.extractPermissionCodes(user);

        log.info("User logged in successfully: {}", user.getEmail());
        return new AuthResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.accessTokenExpiresInMs(),
                summary,
                roles,
                permissions
        );
    }

    @Override
    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        return tokenService.rotateRefreshToken(request.refreshToken());
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        tokenService.revokeRefreshToken(request.refreshToken());
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        User user = tokenService.verifyEmailOtp(request.email(), request.otp());
        userRepository.save(user);
        log.info("Email verified successfully for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                String rawOtp = tokenService.createEmailVerificationOtp(user);
                String fullName = user.getProfile() != null ? user.getProfile().getFullName() : "User";
                notificationService.sendVerificationOtp(user.getEmail(), fullName, rawOtp);
            }
        });
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            String rawOtp = tokenService.createPasswordResetOtp(user);
            String fullName = user.getProfile() != null ? user.getProfile().getFullName() : "User";
            notificationService.sendPasswordResetOtp(user.getEmail(), fullName, rawOtp);
        });
        // Always return silently without revealing account existence
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation password do not match");
        }

        User user = tokenService.verifyAndConsumePasswordResetOtp(request.email(), request.otp());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Revoke all existing sessions/refresh tokens
        tokenService.revokeAllUserRefreshTokens(user.getId());
        log.info("Password successfully reset via OTP for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleUserInfo googleUser = googleAuthService.verifyToken(request.idToken());
        String normalizedEmail = googleUser.email().trim().toLowerCase();

        User user = userRepository.findByEmailWithAuthoritiesIgnoreCase(normalizedEmail).orElse(null);

        if (user == null) {
            // First time login with Google -> auto-register as BUYER
            Role buyerRole = roleRepository.findByCodeWithPermissions(RoleCode.BUYER.name())
                    .orElseThrow(() -> new IllegalStateException("BUYER role not initialized"));

            String randomPassword = UUID.randomUUID().toString();
            user = new User(normalizedEmail, passwordEncoder.encode(randomPassword), AccountStatus.ACTIVE);
            user.setEmailVerified(true);

            String fullName = (googleUser.name() != null && !googleUser.name().isBlank())
                    ? googleUser.name().trim()
                    : normalizedEmail.split("@")[0];

            UserProfile profile = new UserProfile(user, fullName, null, googleUser.pictureUrl());
            user.setProfile(profile);
            user.addRole(buyerRole);

            user = userRepository.save(user);
            log.info("Auto-registered new user via Google login with BUYER role: {}", user.getEmail());
        } else {
            // Existing user -> check status
            if (user.getAccountStatus() == AccountStatus.LOCKED) {
                throw new ForbiddenException("Account is locked. Please contact support.");
            }
            if (user.getAccountStatus() == AccountStatus.DISABLED) {
                throw new ForbiddenException("Account is disabled. Please contact support.");
            }

            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
            }

            if (user.getProfile() != null && (user.getProfile().getAvatarUrl() == null || user.getProfile().getAvatarUrl().isBlank())) {
                if (googleUser.pictureUrl() != null && !googleUser.pictureUrl().isBlank()) {
                    user.getProfile().setAvatarUrl(googleUser.pictureUrl());
                }
            }
        }

        user.setLastLoginAt(Instant.now());
        User savedUser = userRepository.save(user);

        TokenResponse tokenPair = tokenService.generateTokenPair(savedUser);
        UserSummaryResponse summary = userMapper.toSummaryResponse(savedUser);
        Set<String> roles = userMapper.extractRoleCodes(savedUser);
        Set<String> permissions = userMapper.extractPermissionCodes(savedUser);

        log.info("User authenticated via Google successfully: {}", savedUser.getEmail());
        return new AuthResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.accessTokenExpiresInMs(),
                summary,
                roles,
                permissions
        );
    }
}
