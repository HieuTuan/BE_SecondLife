package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.ReputationResponse;
import com.secondlife.secondlife.dto.response.UserAdminResponse;
import com.secondlife.secondlife.dto.response.UserProfileResponse;
import com.secondlife.secondlife.dto.response.UserSummaryResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserMapper {
    private final Delegate delegate;

    public UserMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public UserSummaryResponse toSummaryResponse(User user) {
        return delegate.toSummaryResponse(user);
    }

    public UserProfileResponse toProfileResponse(User user, ReputationResponse sellerReputation) {
        return delegate.toProfileResponse(user, sellerReputation);
    }

    public UserAdminResponse toAdminResponse(User user) {
        return delegate.toAdminResponse(user);
    }

    public Set<String> extractRoleCodes(User user) {
        return delegate.extractRoleCodes(user);
    }

    public Set<String> extractPermissionCodes(User user) {
        return delegate.extractPermissionCodes(user);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "fullName", source = "profile.fullName")
        @Mapping(target = "phone", source = "profile.phone")
        @Mapping(target = "avatarUrl", source = "profile.avatarUrl")
        @Mapping(target = "gender", source = "profile.gender")
        UserSummaryResponse toSummaryResponse(User user);

        @Mapping(target = "fullName", source = "user.profile.fullName")
        @Mapping(target = "phone", source = "user.profile.phone")
        @Mapping(target = "avatarUrl", source = "user.profile.avatarUrl")
        @Mapping(target = "gender", source = "user.profile.gender")
        @Mapping(target = "province", source = "user.profile.province")
        @Mapping(target = "district", source = "user.profile.district")
        @Mapping(target = "ward", source = "user.profile.ward")
        @Mapping(target = "streetAddress", source = "user.profile.streetAddress")
        @Mapping(target = "latitude", source = "user.profile.latitude")
        @Mapping(target = "longitude", source = "user.profile.longitude")
        @Mapping(target = "roles", expression = "java(extractRoleCodes(user))")
        @Mapping(target = "permissions", expression = "java(extractPermissionCodes(user))")
        @Mapping(target = "updatedAt", source = "user.updatedAt")
        @Mapping(target = "sellerReputation", source = "sellerReputation")
        UserProfileResponse toProfileResponse(User user, ReputationResponse sellerReputation);

        @Mapping(target = "fullName", source = "profile.fullName")
        @Mapping(target = "phone", source = "profile.phone")
        @Mapping(target = "avatarUrl", source = "profile.avatarUrl")
        @Mapping(target = "roles", expression = "java(extractRoleCodes(user))")
        UserAdminResponse toAdminResponse(User user);

        default Set<String> extractRoleCodes(User user) {
            if (user == null || user.getRoles() == null) return Collections.emptySet();
            return user.getRoles().stream()
                    .map(Role::getCode)
                    .collect(Collectors.toSet());
        }

        default Set<String> extractPermissionCodes(User user) {
            if (user == null || user.getRoles() == null) return Collections.emptySet();
            return user.getRoles().stream()
                    .flatMap(role -> role.getPermissions().stream())
                    .map(permission -> permission.getCode())
                    .collect(Collectors.toSet());
        }
    }
}
