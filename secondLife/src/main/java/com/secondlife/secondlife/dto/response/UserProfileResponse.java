package com.secondlife.secondlife.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

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
    Gender gender,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Current seller reputation; omitted for users without the SELLER role")
    ReputationResponse sellerReputation,
    String badgeCode,
    UUID stationHubId,
    String stationHubName,
    String certificationTitle
) {
}
