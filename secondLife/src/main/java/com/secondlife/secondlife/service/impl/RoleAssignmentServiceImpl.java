package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.RoleAssignmentService;
import com.secondlife.secondlife.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleAssignmentServiceImpl implements RoleAssignmentService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;

    @Override
    @Transactional
    public void grantRole(UUID userId, RoleCode roleCode) {
        if (roleCode == RoleCode.ADMIN) lockRole(roleCode);
        User user = lockUser(userId);
        Role role = getRole(roleCode);
        if (!user.hasRole(roleCode.name())) {
            user.addRole(role);
            user.bumpTokenVersion();
            userRepository.save(user);
            tokenService.revokeAllUserRefreshTokens(userId);
        }
    }

    @Override
    @Transactional
    public void removeRole(UUID userId, RoleCode roleCode) {
        if (roleCode == RoleCode.ADMIN) lockRole(roleCode);
        User user = lockUser(userId);
        if (!user.hasRole(roleCode.name())) {
            return;
        }
        Role role = getRole(roleCode);
        if (roleCode == RoleCode.ADMIN && user.getAccountStatus() == AccountStatus.ACTIVE
                && userRepository.countActiveAdmins() <= 1) {
            throw new ConflictException("Cannot remove the last active ADMIN role");
        }
        user.removeRole(role);
        user.bumpTokenVersion();
        userRepository.save(user);
        tokenService.revokeAllUserRefreshTokens(userId);
    }

    private User lockUser(UUID userId) {
        return userRepository.findByIdForRoleUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }

    private Role getRole(RoleCode roleCode) {
        return roleRepository.findByCode(roleCode.name())
                .orElseThrow(() -> new IllegalStateException("Role not initialized: " + roleCode));
    }

    private Role lockRole(RoleCode roleCode) {
        return roleRepository.findByCodeForUpdate(roleCode.name())
                .orElseThrow(() -> new IllegalStateException("Role not initialized: " + roleCode));
    }
}
