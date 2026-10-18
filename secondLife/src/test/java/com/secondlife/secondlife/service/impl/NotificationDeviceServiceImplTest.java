package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.DeviceTokenRequest;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserDeviceToken;
import com.secondlife.secondlife.repository.UserDeviceTokenRepository;
import com.secondlife.secondlife.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeviceServiceImplTest {

    @Mock
    private UserDeviceTokenRepository deviceTokenRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private NotificationDeviceServiceImpl service;

    @Test
    void registersANewTokenWithTheExistingDefaultDeviceType() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(deviceTokenRepository.findByToken("new-token")).thenReturn(Optional.empty());

        service.registerDevice(userId, new DeviceTokenRequest("new-token", null));

        ArgumentCaptor<UserDeviceToken> saved = ArgumentCaptor.forClass(UserDeviceToken.class);
        verify(deviceTokenRepository).save(saved.capture());
        assertSame(user, saved.getValue().getUser());
        assertEquals("new-token", saved.getValue().getToken());
        assertEquals("ANDROID", saved.getValue().getDeviceType());
    }

    @Test
    void reassignsAnExistingTokenAndNormalizesAnExplicitDeviceType() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        UserDeviceToken existing = new UserDeviceToken(new User(), "shared-token", "ANDROID");
        Instant previousUse = Instant.parse("2020-01-01T00:00:00Z");
        existing.setLastUsedAt(previousUse);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(deviceTokenRepository.findByToken("shared-token")).thenReturn(Optional.of(existing));

        service.registerDevice(userId, new DeviceTokenRequest("shared-token", "ios"));

        verify(deviceTokenRepository).save(existing);
        assertSame(user, existing.getUser());
        assertEquals("IOS", existing.getDeviceType());
        assertTrue(existing.getLastUsedAt().isAfter(previousUse));
    }

    @Test
    void keepsTheExistingDeviceTypeWhenAnUpdateOmitsIt() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        UserDeviceToken existing = new UserDeviceToken(user, "existing-token", "WEB");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(deviceTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(existing));

        service.registerDevice(userId, new DeviceTokenRequest("existing-token", null));

        assertEquals("WEB", existing.getDeviceType());
        verify(deviceTokenRepository).save(existing);
    }

    @Test
    void unregistersTheProvidedToken() {
        service.unregisterDevice("logout-token");

        verify(deviceTokenRepository).deleteByToken("logout-token");
    }
}
