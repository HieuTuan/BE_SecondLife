package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.DeviceTokenRequest;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserDeviceToken;
import com.secondlife.secondlife.repository.UserDeviceTokenRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.NotificationDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationDeviceServiceImpl implements NotificationDeviceService {

    private final UserDeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void registerDevice(UUID userId, DeviceTokenRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        var existing = deviceTokenRepository.findByToken(request.token());
        if (existing.isPresent()) {
            UserDeviceToken token = existing.get();
            token.setUser(user);
            token.setDeviceType(request.deviceType() != null
                    ? request.deviceType().toUpperCase() : token.getDeviceType());
            token.setLastUsedAt(Instant.now());
            deviceTokenRepository.save(token);
        } else {
            deviceTokenRepository.save(new UserDeviceToken(user, request.token(), request.deviceType()));
        }
    }

    @Override
    @Transactional
    public void unregisterDevice(String token) {
        deviceTokenRepository.deleteByToken(token);
    }
}
