package com.secondlife.secondlife.dto.response;

public record GoogleUserInfo(
    String sub,
    String email,
    boolean emailVerified,
    String name,
    String pictureUrl
) {}
