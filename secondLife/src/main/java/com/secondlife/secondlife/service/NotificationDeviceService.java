package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.DeviceTokenRequest;
import java.util.UUID;

public interface NotificationDeviceService {
    void registerDevice(UUID userId, DeviceTokenRequest request);
    void unregisterDevice(String token);
}
