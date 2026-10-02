package com.secondlife.secondlife.dto.response;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    long accessTokenExpiresInMs
) {}
