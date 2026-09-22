package com.secondlife.secondlife.dto;

public record GoogleUserInfo(
    String sub,
    String email,
    boolean emailVerified,
    String name,
    String pictureUrl
) {}
