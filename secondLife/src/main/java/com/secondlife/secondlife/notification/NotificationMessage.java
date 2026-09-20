package com.secondlife.secondlife.notification;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public record NotificationMessage(
        NotificationType type,
        NotificationRecipient recipient,
        String subject,
        Set<NotificationChannelType> channels,
        Map<String, Object> payload
) {
    public NotificationMessage {
        channels = (channels == null || channels.isEmpty())
                ? Set.of(NotificationChannelType.EMAIL)
                : Collections.unmodifiableSet(channels);
        payload = (payload == null)
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(payload));
    }

    public static NotificationMessage forVerificationOtp(String email, String fullName, String otp) {
        Map<String, Object> payload = Map.of("otp", otp);
        return new NotificationMessage(
                NotificationType.VERIFICATION_OTP,
                NotificationRecipient.ofEmail(email, fullName),
                "Mã xác thực tài khoản SecondLife",
                Set.of(NotificationChannelType.EMAIL),
                payload
        );
    }

    public static NotificationMessage forPasswordResetOtp(String email, String fullName, String otp) {
        Map<String, Object> payload = Map.of("otp", otp);
        return new NotificationMessage(
                NotificationType.PASSWORD_RESET_OTP,
                NotificationRecipient.ofEmail(email, fullName),
                "Yêu cầu đặt lại mật khẩu - SecondLife",
                Set.of(NotificationChannelType.EMAIL),
                payload
        );
    }

    public static NotificationMessage forSellerApproved(User user) {
        return new NotificationMessage(
                NotificationType.SELLER_VERIFICATION_APPROVED,
                NotificationRecipient.from(user),
                "Chúc mừng! Hồ sơ Người bán của bạn đã được phê duyệt - SecondLife",
                Set.of(NotificationChannelType.EMAIL),
                Collections.emptyMap()
        );
    }

    public static NotificationMessage forSellerRejected(User user, String reason) {
        Map<String, Object> payload = Map.of("reason", reason != null ? reason : "");
        return new NotificationMessage(
                NotificationType.SELLER_VERIFICATION_REJECTED,
                NotificationRecipient.from(user),
                "Thông báo kết quả xét duyệt hồ sơ Người bán - SecondLife",
                Set.of(NotificationChannelType.EMAIL),
                payload
        );
    }

    public static NotificationMessage forPasswordChanged(User user) {
        return new NotificationMessage(
                NotificationType.PASSWORD_CHANGED,
                NotificationRecipient.from(user),
                "Cảnh báo bảo mật: Mật khẩu tài khoản vừa thay đổi - SecondLife",
                Set.of(NotificationChannelType.EMAIL),
                Collections.emptyMap()
        );
    }

    public static NotificationMessage forAccountStatusChanged(User user, AccountStatus status) {
        Map<String, Object> payload = Map.of("status", status != null ? status.name() : "");
        String subject = (status == AccountStatus.ACTIVE)
                ? "Tài khoản của bạn đã được kích hoạt lại - SecondLife"
                : "Thông báo cập nhật trạng thái tài khoản - SecondLife";
        return new NotificationMessage(
                NotificationType.ACCOUNT_STATUS_CHANGED,
                NotificationRecipient.from(user),
                subject,
                Set.of(NotificationChannelType.EMAIL),
                payload
        );
    }

    public static NotificationMessage forInspectionCenterCreated(User user, String temporaryPassword) {
        Map<String, Object> payload = Map.of("temporaryPassword", temporaryPassword != null ? temporaryPassword : "");
        return new NotificationMessage(
                NotificationType.INSPECTION_CENTER_CREATED,
                NotificationRecipient.from(user),
                "Thông tin tài khoản Trung tâm Kiểm định - SecondLife",
                Set.of(NotificationChannelType.EMAIL),
                payload
        );
    }
}
