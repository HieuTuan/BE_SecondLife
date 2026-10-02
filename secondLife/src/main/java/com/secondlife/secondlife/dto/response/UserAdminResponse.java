package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.AccountStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserAdminResponse(
    UUID id,
    String email,
    String fullName,
    String phone,
    String avatarUrl,
    AccountStatus accountStatus,
    boolean emailVerified,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt,
    Instant lastLoginAt
) {}
