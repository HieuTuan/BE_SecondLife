package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.dto.response.UserAdminResponse;
import com.secondlife.secondlife.dto.response.UserProfileResponse;
import com.secondlife.secondlife.dto.response.UserSummaryResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserSummaryResponse toSummaryResponse(User user) {
        if (user == null) return null;
        String fullName = user.getProfile() != null ? user.getProfile().getFullName() : null;
        String phone = user.getProfile() != null ? user.getProfile().getPhone() : null;
        String avatarUrl = user.getProfile() != null ? user.getProfile().getAvatarUrl() : null;

        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                fullName,
                phone,
                avatarUrl,
                user.getAccountStatus(),
                user.isEmailVerified()
        );
    }

    public UserProfileResponse toProfileResponse(User user) {
        if (user == null) return null;
        String fullName = user.getProfile() != null ? user.getProfile().getFullName() : null;
        String phone = user.getProfile() != null ? user.getProfile().getPhone() : null;
        String avatarUrl = user.getProfile() != null ? user.getProfile().getAvatarUrl() : null;

        Set<String> roleCodes = extractRoleCodes(user);
        Set<String> permissionCodes = extractPermissionCodes(user);

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                fullName,
                phone,
                avatarUrl,
                user.getAccountStatus(),
                user.isEmailVerified(),
                roleCodes,
                permissionCodes,
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt()
        );
    }

    public UserAdminResponse toAdminResponse(User user) {
        if (user == null) return null;
        String fullName = user.getProfile() != null ? user.getProfile().getFullName() : null;
        String phone = user.getProfile() != null ? user.getProfile().getPhone() : null;
        String avatarUrl = user.getProfile() != null ? user.getProfile().getAvatarUrl() : null;

        return new UserAdminResponse(
                user.getId(),
                user.getEmail(),
                fullName,
                phone,
                avatarUrl,
                user.getAccountStatus(),
                user.isEmailVerified(),
                extractRoleCodes(user),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt()
        );
    }

    public Set<String> extractRoleCodes(User user) {
        if (user == null || user.getRoles() == null) return Collections.emptySet();
        return user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());
    }

    public Set<String> extractPermissionCodes(User user) {
        if (user == null || user.getRoles() == null) return Collections.emptySet();
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getCode())
                .collect(Collectors.toSet());
    }
}
