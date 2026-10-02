package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.rbac.*;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.AdminUserRoleService;
import com.secondlife.secondlife.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserRoleServiceImpl implements AdminUserRoleService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAuditRepository auditRepository;
    private final SellerVerificationRepository verificationRepository;
    private final TokenService tokenService;

    @Override
    @Transactional(readOnly = true)
    public UserRolesResponse getUserRoles(UUID userId) {
        return response(userRepository.findByIdWithAuthorities(userId)
                .orElseThrow(() -> new NotFoundException("User not found")));
    }

    @Override
    @Transactional
    public UserRolesResponse replaceUserRoles(UUID adminId, UUID userId, ReplaceUserRolesRequest request) {
        if (request == null || request.expectedRoleCodes() == null || request.roleCodes() == null) {
            throw new BadRequestException("Expected and desired role codes are required");
        }
        Set<String> expected = validateCodes(request.expectedRoleCodes());
        Set<String> desired = validateCodes(request.roleCodes());
        // All ADMIN membership/status writers acquire this shared guard before the user row.
        // It serializes the count + mutation, including requests affecting different admins.
        roleRepository.findByCodeForUpdate(RoleCode.ADMIN.name())
                .orElseThrow(() -> new ConflictException("ADMIN role is not initialized"));
        User user = userRepository.findByIdForRoleUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Set<String> current = roleCodes(user);
        if (!current.equals(expected)) {
            throw new ConflictException("User roles changed; reload before saving");
        }
        boolean removingAdmin = current.contains("ADMIN") && !desired.contains("ADMIN");
        if (removingAdmin && adminId.equals(userId)) {
            throw new ConflictException("Administrators cannot remove their own ADMIN role");
        }
        if (removingAdmin && user.getAccountStatus() == AccountStatus.ACTIVE
                && userRepository.countActiveAdmins() <= 1) {
            throw new ConflictException("Cannot remove the last active ADMIN role");
        }
        if (desired.contains("SELLER") && !current.contains("SELLER")
                && !verificationRepository.existsByUserIdAndStatus(userId, SellerVerificationStatus.APPROVED)) {
            throw new ConflictException("SELLER requires an approved seller verification");
        }
        Map<String, Role> roles = new HashMap<>();
        for (String code : desired) {
            roles.put(code, roleRepository.findByCode(code)
                    .orElseThrow(() -> new BadRequestException("Role is not initialized: " + code)));
        }
        if (current.equals(desired)) return response(user);
        for (Role role : new HashSet<>(user.getRoles())) {
            if (!desired.contains(role.getCode())) {
                user.removeRole(role);
                audit(adminId, userId, role.getCode(), "REVOKE");
            }
        }
        for (String code : desired) {
            if (!current.contains(code)) {
                user.addRole(roles.get(code));
                audit(adminId, userId, code, "GRANT");
            }
        }
        user.bumpTokenVersion();
        userRepository.saveAndFlush(user);
        tokenService.revokeAllUserRefreshTokens(userId);
        return response(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserRoleAuditResponse> getUserRoleAudit(UUID userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) throw new NotFoundException("User not found");
        return PageResponse.from(auditRepository.findByUserIdOrderByChangedAtDesc(userId, pageable)
                .map(a -> new UserRoleAuditResponse(a.getId(), a.getRoleCode(), a.getAction(),
                        a.getChangedBy(), a.getChangedAt())));
    }

    private Set<String> validateCodes(Set<String> codes) {
        Set<String> result = new TreeSet<>();
        for (String code : codes) {
            try { result.add(RoleCode.valueOf(code).name()); }
            catch (IllegalArgumentException | NullPointerException ex) {
                throw new BadRequestException("Unknown role code: " + code);
            }
        }
        return result;
    }

    private Set<String> roleCodes(User user) {
        return user.getRoles().stream().map(Role::getCode).collect(Collectors.toCollection(TreeSet::new));
    }

    private UserRolesResponse response(User user) {
        Set<String> permissions = user.getRoles().stream().flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode).collect(Collectors.toCollection(TreeSet::new));
        return new UserRolesResponse(user.getId(), roleCodes(user), permissions);
    }

    private void audit(UUID actorId, UUID userId, String roleCode, String action) {
        UserRoleAudit audit = new UserRoleAudit();
        audit.setUserId(userId);
        audit.setRoleCode(roleCode);
        audit.setAction(action);
        audit.setChangedBy(actorId);
        audit.setChangedAt(Instant.now());
        auditRepository.save(audit);
    }
}
