package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.AccountStatus;

import java.util.UUID;

public record UserSummaryResponse(
    UUID id,
    String email,
    String fullName,
    String phone,
    String avatarUrl,
    AccountStatus accountStatus,
    boolean emailVerified,
    com.secondlife.secondlife.enums.Gender gender
) {
    public UserSummaryResponse(UUID id,String email,String fullName,String phone,String avatarUrl,AccountStatus accountStatus,boolean emailVerified) {
        this(id,email,fullName,phone,avatarUrl,accountStatus,emailVerified,null);
    }
}
