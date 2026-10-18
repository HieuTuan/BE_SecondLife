package com.secondlife.secondlife.integration.notification;

import com.secondlife.secondlife.enums.NotificationChannelType;

public interface NotificationChannel {

    NotificationChannelType getChannelType();

    default boolean supports(NotificationChannelType channelType) {
        return getChannelType() == channelType;
    }

    void send(NotificationMessage message);
}
