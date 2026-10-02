package com.secondlife.secondlife.dto.response;

import java.util.Set;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long accessTokenExpiresInMs,
    UserSummaryResponse user,
    Set<String> roles,
    Set<String> permissions
) {}
