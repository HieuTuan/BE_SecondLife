package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.dto.response.UserAdminResponse;
import com.secondlife.secondlife.dto.response.UserProfileResponse;
import com.secondlife.secondlife.dto.response.UserSummaryResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
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
                user.isEmailVerified(),
                user.getProfile()!=null?user.getProfile().getGender():null
        );
    }

    public UserProfileResponse toProfileResponse(User user) {
        if (user == null) return null;
        String fullName = user.getProfile() != null ? user.getProfile().getFullName() : null;
        String phone = user.getProfile() != null ? user.getProfile().getPhone() : null;
        String avatarUrl = user.getProfile() != null ? user.getProfile().getAvatarUrl() : null;

        String province = user.getProfile() != null ? user.getProfile().getProvince() : null;
        String district = user.getProfile() != null ? user.getProfile().getDistrict() : null;
        String ward = user.getProfile() != null ? user.getProfile().getWard() : null;
        String streetAddress = user.getProfile() != null ? user.getProfile().getStreetAddress() : null;
        Double latitude = user.getProfile() != null ? user.getProfile().getLatitude() : null;
        Double longitude = user.getProfile() != null ? user.getProfile().getLongitude() : null;

        Set<String> roleCodes = extractRoleCodes(user);
        Set<String> permissionCodes = extractPermissionCodes(user);

        String badgeCode = null;
        UUID stationHubId = null;
        String stationHubName = null;
        String certificationTitle = null;

        if (roleCodes != null && roleCodes.stream().anyMatch(r -> r.equalsIgnoreCase("INSPECTOR"))) {
            if (user.getId() != null) {
                String rawId = user.getId().toString().replace("-", "");
                String shortCode = rawId.length() >= 4 ? rawId.substring(rawId.length() - 4).toUpperCase() : rawId.toUpperCase();
                badgeCode = "SL-TECH-" + shortCode;
            } else {
                badgeCode = "SL-TECH-0000";
            }
            certificationTitle = "Kỹ thuật viên Thẩm định Thiết bị Bậc 3";

            try {
                if (user.getManager() != null) {
                    stationHubId = user.getManager().getId();
                    if (user.getManager().getProfile() != null && user.getManager().getProfile().getFullName() != null) {
                        stationHubName = user.getManager().getProfile().getFullName();
                    } else {
                        stationHubName = "Trung Tâm Kiểm Định SecondLife";
                    }
                } else {
                    stationHubName = "Trung Tâm Kiểm Định SecondLife";
                }
            } catch (Exception ignored) {
                stationHubName = "Trung Tâm Kiểm Định SecondLife";
            }
        }

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
                user.getLastLoginAt(),
                province,
                district,
                ward,
                streetAddress,
                latitude,
                longitude,
                user.getProfile() != null ? user.getProfile().getGender() : null,
                badgeCode,
                stationHubId,
                stationHubName,
                certificationTitle
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
