package com.secondlife.secondlife.notification.channel;

import com.secondlife.secondlife.notification.NotificationChannelType;
import com.secondlife.secondlife.notification.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PushNotificationChannel implements NotificationChannel {

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.PUSH;
    }

    @Override
    public void send(NotificationMessage message) {
        String deviceToken = message.recipient().deviceToken();
        if (deviceToken == null || deviceToken.isBlank()) {
            log.debug("Skip Push notification: deviceToken is not available for recipient {}", message.recipient().userId());
            return;
        }

        // Extensible integration point for Push providers (e.g. Firebase Cloud Messaging - FCM)
        log.info("Push notification [{}] dispatched to device: {} (Title: {})",
                message.type(), deviceToken, message.subject());
    }
}
