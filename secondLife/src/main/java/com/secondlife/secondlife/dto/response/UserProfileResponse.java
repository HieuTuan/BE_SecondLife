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
    Instant lastLoginAt
) {}
