package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.AdminStatusUpdateRequest;
import com.secondlife.secondlife.dto.request.ChangePasswordRequest;
import com.secondlife.secondlife.dto.request.CreateInspectionCenterRequest;
import com.secondlife.secondlife.dto.request.UpdateProfileRequest;
import com.secondlife.secondlife.dto.response.UserAdminResponse;
import com.secondlife.secondlife.dto.response.UserProfileResponse;
import com.secondlife.secondlife.dto.response.UserSummaryResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.TokenService;
import com.secondlife.secondlife.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findByIdWithAuthorities(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findByIdWithAuthorities(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        UserProfile profile = user.getProfile();
        if (profile == null) {
            profile = new UserProfile(user, "", null, null);
            user.setProfile(profile);
        }

        if (request.fullName() != null && !request.fullName().isBlank()) {
            profile.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            profile.setPhone(request.phone().trim());
        }
        if (request.avatarUrl() != null) {
            profile.setAvatarUrl(request.avatarUrl().trim());
        }

        User savedUser = userRepository.save(user);
        log.info("Profile updated for user: {}", savedUser.getEmail());
        return userMapper.toProfileResponse(savedUser);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation password do not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Revoke all active sessions upon password change
        tokenService.revokeAllUserRefreshTokens(userId);
        log.info("Password changed and active sessions revoked for user: {}", user.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserAdminResponse> getAdminUsers(String email, AccountStatus status, String role, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<User> spec = (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                query.distinct(true);
            }
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            if (email != null && !email.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + email.trim().toLowerCase() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("accountStatus"), status));
            }
            if (role != null && !role.trim().isEmpty()) {
                jakarta.persistence.criteria.Join<Object, Object> userRoles = root.join("userRoles", jakarta.persistence.criteria.JoinType.LEFT);
                jakarta.persistence.criteria.Join<Object, Object> r = userRoles.join("role", jakarta.persistence.criteria.JoinType.LEFT);
                predicates.add(cb.equal(cb.upper(r.get("code")), role.trim().toUpperCase()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<User> page = userRepository.findAll(spec, pageable);
        Page<UserAdminResponse> dtoPage = page.map(userMapper::toAdminResponse);
        return PageResponse.from(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAdminResponse getAdminUserById(UUID userId) {
        User user = userRepository.findByIdWithAuthorities(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        return userMapper.toAdminResponse(user);
    }

    @Override
    @Transactional
    public UserAdminResponse updateAdminUserStatus(UUID currentAdminId, UUID targetUserId, AdminStatusUpdateRequest request) {
        User targetUser = userRepository.findByIdWithAuthorities(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + targetUserId));

        if (request.status() == AccountStatus.LOCKED || request.status() == AccountStatus.DISABLED) {
            if (currentAdminId.equals(targetUserId)) {
                throw new BadRequestException("Administrators cannot lock or disable their own account");
            }

            if (targetUser.hasRole(RoleCode.ADMIN.name())) {
                long activeAdminCount = userRepository.countActiveAdmins();
                if (activeAdminCount <= 1) {
                    throw new BadRequestException("Cannot disable or lock the last remaining active Administrator account");
                }
            }

            // Revoke active sessions when locked or disabled
            tokenService.revokeAllUserRefreshTokens(targetUserId);
        }

        targetUser.setAccountStatus(request.status());
        User savedUser = userRepository.save(targetUser);
        log.info("Admin {} updated status of user {} to {}", currentAdminId, savedUser.getEmail(), request.status());
        return userMapper.toAdminResponse(savedUser);
    }

    @Override
    @Transactional
    public UserSummaryResponse createInspectionCenterAccount(CreateInspectionCenterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Email already registered: " + request.email());
        }

        Role inspectionCenterRole = roleRepository.findByCodeWithPermissions(RoleCode.INSPECTION_CENTER.name())
                .orElseThrow(() -> new IllegalStateException("INSPECTION_CENTER role not initialized"));

        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()), AccountStatus.ACTIVE);
        user.setEmailVerified(true);

        UserProfile profile = new UserProfile(user, request.fullName().trim(),
                request.phone() != null ? request.phone().trim() : null, null);
        user.setProfile(profile);
        user.addRole(inspectionCenterRole);

        User savedUser = userRepository.save(user);
        log.info("Admin created Inspection Center account: {}", savedUser.getEmail());
        return userMapper.toSummaryResponse(savedUser);
    }
}
