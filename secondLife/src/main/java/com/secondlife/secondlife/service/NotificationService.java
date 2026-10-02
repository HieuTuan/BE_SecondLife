package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.notification.NotificationMessage;

public interface NotificationService {

    /**
     * Dispatch a notification message to configured delivery channels.
     */
    void send(NotificationMessage message);

    /**
     * Send email verification OTP code to the recipient.
     */
    void sendVerificationOtp(String recipientEmail, String fullName, String otp);

    /**
     * Send password reset OTP code to the recipient.
     */
    void sendPasswordResetOtp(String recipientEmail, String fullName, String otp);

    /**
     * Send seller verification approval notification to user.
     */
    void sendSellerVerificationApproved(User user);

    /**
     * Send seller verification rejection notification with reason to user.
     */
    void sendSellerVerificationRejected(User user, String reason);

    /**
     * Send security alert when account password has been changed.
     */
    void sendPasswordChangedAlert(User user);

    /**
     * Send notification when account status is updated (e.g. LOCKED, DISABLED, ACTIVE).
     */
    void sendAccountStatusChanged(User user, AccountStatus status);

    /**
     * Send initial login credentials notification for newly provisioned Inspection Center account.
     */
    void sendInspectionCenterCreated(User user, String temporaryPassword);
}
