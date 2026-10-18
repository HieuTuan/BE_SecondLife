package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
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

    public UserProfileResponse toProfileResponse(User user) {
        return delegate.toProfileResponse(user);
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

        @Mapping(target = "fullName", source = "profile.fullName")
        @Mapping(target = "phone", source = "profile.phone")
        @Mapping(target = "avatarUrl", source = "profile.avatarUrl")
        @Mapping(target = "gender", source = "profile.gender")
        @Mapping(target = "province", source = "profile.province")
        @Mapping(target = "district", source = "profile.district")
        @Mapping(target = "ward", source = "profile.ward")
        @Mapping(target = "streetAddress", source = "profile.streetAddress")
        @Mapping(target = "latitude", source = "profile.latitude")
        @Mapping(target = "longitude", source = "profile.longitude")
        @Mapping(target = "roles", expression = "java(extractRoleCodes(user))")
        @Mapping(target = "permissions", expression = "java(extractPermissionCodes(user))")
        UserProfileResponse toProfileResponse(User user);

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
