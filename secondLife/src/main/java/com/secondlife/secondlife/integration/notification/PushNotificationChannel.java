package com.secondlife.secondlife.integration.notification;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.secondlife.secondlife.entity.UserDeviceToken;
import com.secondlife.secondlife.enums.NotificationChannelType;
import com.secondlife.secondlife.repository.UserDeviceTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PushNotificationChannel implements NotificationChannel {

    private final UserDeviceTokenRepository deviceTokenRepository;

    @Autowired
    public PushNotificationChannel(UserDeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.PUSH;
    }

    @Override
    @Async
    public void send(NotificationMessage message) {
        if (message == null || message.recipient() == null) {
            return;
        }

        List<String> tokens = new ArrayList<>();
        if (message.recipient().userId() != null) {
            List<UserDeviceToken> userTokens = deviceTokenRepository.findByUserId(message.recipient().userId());
            tokens.addAll(userTokens.stream().map(UserDeviceToken::getToken).toList());
        }

        if (tokens.isEmpty() && message.recipient().deviceToken() != null && !message.recipient().deviceToken().isBlank()) {
            tokens.add(message.recipient().deviceToken());
        }

        if (tokens.isEmpty()) {
            log.debug("Skip Push notification: No registered device tokens for recipient {}", message.recipient().userId());
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.info("Push notification [{}] (FCM Mock): dispatched to {} device(s) of user {} (Title: {})",
                    message.type(), tokens.size(), message.recipient().userId(), message.subject());
            return;
        }

        try {
            String bodyText = message.payload().get("body") instanceof String s ? s : message.subject();
            Notification notification = Notification.builder()
                    .setTitle(message.subject())
                    .setBody(bodyText)
                    .build();

            MulticastMessage.Builder multicastBuilder = MulticastMessage.builder()
                    .setNotification(notification)
                    .addAllTokens(tokens);

            if (message.type() != null) multicastBuilder.putData("type", message.type().name());
            Object refId = message.payload().get("referenceId");
            if (refId != null) multicastBuilder.putData("referenceId", refId.toString());
            Object refType = message.payload().get("referenceType");
            if (refType != null) multicastBuilder.putData("referenceType", refType.toString());
            Object deepLink = message.payload().get("deepLink");
            if (deepLink != null) multicastBuilder.putData("deepLink", deepLink.toString());

            MulticastMessage multicastMessage = multicastBuilder.build();
            var batchResponse = FirebaseMessaging.getInstance().sendEachForMulticast(multicastMessage);

            log.info("FCM push sent: {} success, {} failure for user {}",
                    batchResponse.getSuccessCount(), batchResponse.getFailureCount(), message.recipient().userId());

            if (batchResponse.getFailureCount() > 0) {
                var responses = batchResponse.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        String failedToken = tokens.get(i);
                        String errorCode = responses.get(i).getException().getMessagingErrorCode() != null
                                ? responses.get(i).getException().getMessagingErrorCode().name() : "UNKNOWN";
                        log.warn("FCM push to token [{}] failed with code [{}]", failedToken, errorCode);
                        if ("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
                            deviceTokenRepository.deleteByToken(failedToken);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to send FCM push notification for user {}: {}", message.recipient().userId(), e.getMessage(), e);
        }
    }
}
