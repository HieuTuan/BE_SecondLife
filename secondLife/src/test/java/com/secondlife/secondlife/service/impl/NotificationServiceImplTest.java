package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.notification.NotificationChannelType;
import com.secondlife.secondlife.notification.NotificationMessage;
import com.secondlife.secondlife.notification.channel.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationChannel emailChannel;

    @Mock
    private NotificationChannel smsChannel;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        when(emailChannel.supports(NotificationChannelType.EMAIL)).thenReturn(true);
        notificationService = new NotificationServiceImpl(List.of(emailChannel, smsChannel));
    }

    @Test
    void sendVerificationOtp_ShouldDispatchToEmailChannel() {
        notificationService.sendVerificationOtp("test@example.com", "Test User", "123456");

        verify(emailChannel, times(1)).send(any(NotificationMessage.class));
        verify(smsChannel, never()).send(any(NotificationMessage.class));
    }

    @Test
    void sendPasswordResetOtp_ShouldDispatchToEmailChannel() {
        notificationService.sendPasswordResetOtp("reset@example.com", "Test User", "654321");

        verify(emailChannel, times(1)).send(any(NotificationMessage.class));
    }

    @Test
    void sendSellerVerificationApproved_ShouldDispatchToEmailChannel() {
        User user = new User("seller@example.com", "pass", AccountStatus.ACTIVE);
        notificationService.sendSellerVerificationApproved(user);

        verify(emailChannel, times(1)).send(any(NotificationMessage.class));
    }

    @Test
    void sendSellerVerificationRejected_ShouldDispatchToEmailChannel() {
        User user = new User("seller@example.com", "pass", AccountStatus.ACTIVE);
        notificationService.sendSellerVerificationRejected(user, "Invalid ID document");

        verify(emailChannel, times(1)).send(any(NotificationMessage.class));
    }

    @Test
    void sendPasswordChangedAlert_ShouldDispatchToEmailChannel() {
        User user = new User("buyer@example.com", "pass", AccountStatus.ACTIVE);
        notificationService.sendPasswordChangedAlert(user);

        verify(emailChannel, times(1)).send(any(NotificationMessage.class));
    }

    @Test
    void sendAccountStatusChanged_ShouldDispatchToEmailChannel() {
        User user = new User("buyer@example.com", "pass", AccountStatus.LOCKED);
        notificationService.sendAccountStatusChanged(user, AccountStatus.LOCKED);

        verify(emailChannel, times(1)).send(any(NotificationMessage.class));
    }

    @Test
    void sendInspectionCenterCreated_ShouldDispatchToEmailChannel() {
        User user = new User("center@example.com", "pass", AccountStatus.ACTIVE);
        notificationService.sendInspectionCenterCreated(user, "TempPass123");

        verify(emailChannel, times(1)).send(any(NotificationMessage.class));
    }

    @Test
    void send_WhenChannelThrowsException_ShouldCatchAndNotCrashCaller() {
        doThrow(new RuntimeException("Channel connection lost")).when(emailChannel).send(any(NotificationMessage.class));

        NotificationMessage message = NotificationMessage.forVerificationOtp("test@example.com", "User", "123456");
        notificationService.send(message);

        verify(emailChannel).send(message);
    }
}
