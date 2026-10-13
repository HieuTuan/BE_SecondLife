package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.AccountStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    String fullName,
    String phone,
    String avatarUrl,
    AccountStatus accountStatus,
    boolean emailVerified,
    Set<String> roles,
    Set<String> permissions,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt,
    String province,
    String district,
    String ward,
    String streetAddress,
    Double latitude,
    Double longitude,
    com.secondlife.secondlife.enums.Gender gender
) {
    public UserProfileResponse(UUID id,String email,String fullName,String phone,String avatarUrl,AccountStatus accountStatus,
            boolean emailVerified,Set<String> roles,Set<String> permissions,Instant createdAt,Instant updatedAt,Instant lastLoginAt,
            String province,String district,String ward,String streetAddress,Double latitude,Double longitude) {
        this(id,email,fullName,phone,avatarUrl,accountStatus,emailVerified,roles,permissions,createdAt,updatedAt,lastLoginAt,
                province,district,ward,streetAddress,latitude,longitude,null);
    }
}
