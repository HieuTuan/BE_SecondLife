package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRequest {

    @NotBlank(message = "FCM token không được để trống")
    private String token;

    private String deviceType; // ANDROID, IOS, WEB
}
