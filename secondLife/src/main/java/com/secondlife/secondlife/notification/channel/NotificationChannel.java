package com.secondlife.secondlife.notification.channel;

import com.secondlife.secondlife.notification.NotificationChannelType;
import com.secondlife.secondlife.notification.NotificationMessage;

public interface NotificationChannel {

    NotificationChannelType getChannelType();

    default boolean supports(NotificationChannelType channelType) {
        return getChannelType() == channelType;
    }

    void send(NotificationMessage message);
}
