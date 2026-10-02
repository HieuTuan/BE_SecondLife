package com.secondlife.secondlife.notification.channel;

import com.secondlife.secondlife.notification.NotificationChannelType;
import com.secondlife.secondlife.notification.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsNotificationChannel implements NotificationChannel {

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.SMS;
    }

    @Override
    public void send(NotificationMessage message) {
        String phone = message.recipient().phone();
        if (phone == null || phone.isBlank()) {
            log.debug("Skip SMS notification: recipient phone is not available for user {}", message.recipient().userId());
            return;
        }

        // Extensible integration point for SMS providers (e.g. Twilio, SpeedSMS, eSMS)
        log.info("SMS notification [{}] queued for phone: {} (Message: {})",
                message.type(), phone, message.subject());
    }
}
