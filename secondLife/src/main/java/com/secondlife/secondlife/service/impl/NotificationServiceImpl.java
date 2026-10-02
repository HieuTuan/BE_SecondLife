package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.notification.NotificationChannelType;
import com.secondlife.secondlife.notification.NotificationMessage;
import com.secondlife.secondlife.notification.channel.NotificationChannel;
import com.secondlife.secondlife.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final List<NotificationChannel> channels;

    @Override
    public void send(NotificationMessage message) {
        if (message == null) {
            log.warn("Attempted to send null NotificationMessage");
            return;
        }

        log.debug("Dispatching notification [{}] to channels: {}", message.type(), message.channels());
        for (NotificationChannelType channelType : message.channels()) {
            boolean handled = false;
            for (NotificationChannel channel : channels) {
                if (channel.supports(channelType)) {
                    try {
                        channel.send(message);
                        handled = true;
                    } catch (Exception e) {
                        log.error("Channel {} failed to deliver notification [{}]: {}",
                                channelType, message.type(), e.getMessage(), e);
                    }
                }
            }
            if (!handled) {
                log.warn("No active NotificationChannel found to handle channel type: {}", channelType);
            }
        }
    }

    @Override
    public void sendVerificationOtp(String recipientEmail, String fullName, String otp) {
        send(NotificationMessage.forVerificationOtp(recipientEmail, fullName, otp));
    }

    @Override
    public void sendPasswordResetOtp(String recipientEmail, String fullName, String otp) {
        send(NotificationMessage.forPasswordResetOtp(recipientEmail, fullName, otp));
    }

    @Override
    public void sendSellerVerificationApproved(User user) {
        send(NotificationMessage.forSellerApproved(user));
    }

    @Override
    public void sendSellerVerificationRejected(User user, String reason) {
        send(NotificationMessage.forSellerRejected(user, reason));
    }

    @Override
    public void sendPasswordChangedAlert(User user) {
        send(NotificationMessage.forPasswordChanged(user));
    }

    @Override
    public void sendAccountStatusChanged(User user, AccountStatus status) {
        send(NotificationMessage.forAccountStatusChanged(user, status));
    }

    @Override
    public void sendInspectionCenterCreated(User user, String temporaryPassword) {
        send(NotificationMessage.forInspectionCenterCreated(user, temporaryPassword));
    }
}
