package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenRequest(
    @NotBlank(message = "FCM token không được để trống")
    String token,
    String deviceType // ANDROID, IOS, WEB
) {
}
