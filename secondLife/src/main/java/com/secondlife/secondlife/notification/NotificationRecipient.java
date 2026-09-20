package com.secondlife.secondlife.notification;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;

import java.util.UUID;

public record NotificationRecipient(
        UUID userId,
        String email,
        String fullName,
        String phone,
        String deviceToken
) {
    public static NotificationRecipient from(User user) {
        if (user == null) {
            return new NotificationRecipient(null, null, null, null, null);
        }
        UserProfile profile = user.getProfile();
        String name = (profile != null && profile.getFullName() != null && !profile.getFullName().isBlank())
                ? profile.getFullName()
                : null;
        String phoneNumber = (profile != null) ? profile.getPhone() : null;

        return new NotificationRecipient(
                user.getId(),
                user.getEmail(),
                name,
                phoneNumber,
                null
        );
    }

    public static NotificationRecipient ofEmail(String email, String fullName) {
        return new NotificationRecipient(null, email, fullName, null, null);
    }
}
