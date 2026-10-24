package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.NotificationActionResponseDTO;
import com.secondlife.secondlife.dto.response.NotificationResponseDTO;
import com.secondlife.secondlife.dto.response.UnreadCountResponseDTO;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.integration.notification.NotificationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    /**
     * Tạo thông báo In-App và đẩy real-time qua WebSocket STOMP tới người dùng.
     */
    NotificationResponseDTO notify(UUID userId, String title, String message, String type, UUID referenceId, String referenceType, String actionUrl);

    default NotificationResponseDTO notify(UUID userId, String title, String message, NotificationType type, UUID referenceId, String referenceType, String actionUrl) {
        return notify(userId, title, message, type != null ? type.name() : "SYSTEM", referenceId, referenceType, actionUrl);
    }

    /**
     * Lưu thông báo và đẩy WebSocket; không gửi qua email, SMS hoặc FCM.
     */
    NotificationResponseDTO notifyInApp(UUID userId, String title, String message, String type, UUID referenceId, String referenceType, String actionUrl);

    default NotificationResponseDTO notifyInApp(UUID userId, String title, String message, NotificationType type, UUID referenceId, String referenceType, String actionUrl) {
        return notifyInApp(userId, title, message, type != null ? type.name() : "SYSTEM", referenceId, referenceType, actionUrl);
    }

    /**
     * Tạo thông báo In-App, đẩy WebSocket và gửi kèm Email chứng từ quan trọng.
     */
    NotificationResponseDTO notifyWithEmail(User user, String title, String message, String type, UUID referenceId, String referenceType, String actionUrl, String emailSubject, String emailBody);

    NotificationResponseDTO notifyWithEmail(UUID userId, String title, String message, String type, UUID referenceId, String referenceType, String actionUrl);

    default NotificationResponseDTO notifyWithEmail(UUID userId, String title, String message, NotificationType type, UUID referenceId, String referenceType, String actionUrl) {
        return notifyWithEmail(userId, title, message, type != null ? type.name() : "SYSTEM", referenceId, referenceType, actionUrl);
    }

    /**
     * Lấy danh sách thông báo của người dùng với phân trang và bộ lọc (chưa đọc, loại thông báo).
     */
    Page<NotificationResponseDTO> getUserNotifications(UUID userId, Boolean unreadOnly, String type, Pageable pageable);

    /**
     * Lấy số lượng thông báo chưa đọc của người dùng để hiển thị badge quả chuông.
     */
    UnreadCountResponseDTO getUnreadCount(UUID userId);

    /**
     * Đánh dấu 1 thông báo cụ thể là đã đọc.
     */
    NotificationResponseDTO markAsRead(UUID userId, UUID notificationId);

    /**
     * Đánh dấu toàn bộ thông báo chưa đọc của người dùng thành đã đọc.
     */
    NotificationActionResponseDTO markAllAsRead(UUID userId);

    /**
     * Xóa mềm 1 thông báo cụ thể theo ID.
     */
    void deleteNotification(UUID userId, UUID notificationId);

    /**
     * Xóa mềm toàn bộ các thông báo đã đọc của người dùng.
     */
    NotificationActionResponseDTO clearReadNotifications(UUID userId);

    /**
     * Xóa mềm toàn bộ mọi thông báo của người dùng (kể cả chưa đọc).
     */
    NotificationActionResponseDTO clearAllNotifications(UUID userId);

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
