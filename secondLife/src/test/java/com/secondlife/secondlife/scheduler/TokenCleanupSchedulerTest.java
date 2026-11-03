package com.secondlife.secondlife.scheduler;

import com.secondlife.secondlife.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private TokenCleanupScheduler scheduler;

    @Test
    void cleanupExpiredTokens_ShouldInvokeTokenServiceCleanup() {
        when(tokenService.cleanupExpiredAndRevokedTokens()).thenReturn(10);

        scheduler.cleanupExpiredTokens();

        verify(tokenService).cleanupExpiredAndRevokedTokens();
    }

    @Test
    void cleanupExpiredTokens_WhenServiceThrowsException_ShouldHandleGracefully() {
        when(tokenService.cleanupExpiredAndRevokedTokens()).thenThrow(new RuntimeException("DB error"));

        // Should not throw out of the scheduled method
        scheduler.cleanupExpiredTokens();

        verify(tokenService).cleanupExpiredAndRevokedTokens();
    }
}
