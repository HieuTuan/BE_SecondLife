package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.NotificationActionResponseDTO;
import com.secondlife.secondlife.dto.response.NotificationResponseDTO;
import com.secondlife.secondlife.dto.response.UnreadCountResponseDTO;
import com.secondlife.secondlife.entity.Notification;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.notification.NotificationChannelType;
import com.secondlife.secondlife.notification.NotificationMessage;
import com.secondlife.secondlife.notification.channel.NotificationChannel;
import com.secondlife.secondlife.repository.NotificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.NotificationService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final List<NotificationChannel> channels;
    private final JavaMailSender mailSender;
    private final EmailTemplateBuilder templateBuilder;

    @Value("${app.mail.from:no-reply@secondlife.com}")
    private String fromEmail;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            List<NotificationChannel> channels,
            JavaMailSender mailSender,
            EmailTemplateBuilder templateBuilder
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.channels = channels;
        this.mailSender = mailSender;
        this.templateBuilder = templateBuilder;
    }

    @Override
    @Transactional
    public NotificationResponseDTO notify(UUID userId, String title, String message, String type, UUID referenceId, String referenceType, String actionUrl) {
        if (userId == null) {
            log.warn("Cannot create notification: userId is null");
            return null;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Cannot create notification: user not found with id {}", userId);
            return null;
        }

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type != null ? type : "SYSTEM")
                .referenceId(referenceId)
                .referenceType(referenceType)
                .actionUrl(actionUrl)
                .isRead(false)
                .isDeleted(false)
                .build();

        notification = notificationRepository.save(notification);
        NotificationResponseDTO dto = NotificationResponseDTO.from(notification);

        // Real-time WebSocket push to recipient's private destination /user/queue/notifications
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", dto);
            log.debug("Dispatched realtime notification [{}] to user {}", type, userId);
        } catch (Exception e) {
            log.warn("Failed to push realtime WebSocket notification to user {}: {}", userId, e.getMessage());
        }

        return dto;
    }

    @Override
    @Transactional
    public NotificationResponseDTO notifyWithEmail(UUID userId, String title, String message, String type, UUID referenceId, String referenceType, String actionUrl) {
        if (userId == null) {
            log.warn("Cannot notifyWithEmail: userId is null");
            return null;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Cannot notifyWithEmail: user {} not found", userId);
            return null;
        }
        return notifyWithEmail(user, title, message, type, referenceId, referenceType, actionUrl, title, message);
    }

    @Override
    @Transactional
    public NotificationResponseDTO notifyWithEmail(User user, String title, String message, String type, UUID referenceId, String referenceType, String actionUrl, String emailSubject, String emailBody) {
        if (user == null) {
            log.warn("Cannot notifyWithEmail: user is null");
            return null;
        }

        NotificationResponseDTO dto = notify(user.getId(), title, message, type, referenceId, referenceType, actionUrl);

        // Send high-priority financial/legal/certificate email asynchronously or protected with try-catch
        sendEmailSafely(user, emailSubject != null ? emailSubject : title, type, emailBody != null ? emailBody : message, actionUrl);

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponseDTO> getUserNotifications(UUID userId, Boolean unreadOnly, String type, Pageable pageable) {
        Page<Notification> page;
        boolean filterUnread = Boolean.TRUE.equals(unreadOnly);
        boolean hasType = type != null && !type.isBlank();

        if (filterUnread) {
            if (hasType) {
                page = notificationRepository.findByUserIdAndTypeAndIsReadFalseAndIsDeletedFalse(userId, type.trim().toUpperCase(), pageable);
            } else {
                page = notificationRepository.findByUserIdAndIsReadFalseAndIsDeletedFalse(userId, pageable);
            }
        } else {
            if (hasType) {
                page = notificationRepository.findByUserIdAndTypeAndIsDeletedFalse(userId, type.trim().toUpperCase(), pageable);
            } else {
                page = notificationRepository.findByUserIdAndIsDeletedFalse(userId, pageable);
            }
        }

        return page.map(NotificationResponseDTO::from);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponseDTO getUnreadCount(UUID userId) {
        long count = notificationRepository.countByUserIdAndIsReadFalseAndIsDeletedFalse(userId);
        return new UnreadCountResponseDTO(count);
    }

    @Override
    @Transactional
    public NotificationResponseDTO markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserIdAndIsDeletedFalse(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Thông báo không tồn tại hoặc đã bị xóa"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }

        return NotificationResponseDTO.from(notification);
    }

    @Override
    @Transactional
    public NotificationActionResponseDTO markAllAsRead(UUID userId) {
        int updatedCount = notificationRepository.markAllAsRead(userId, Instant.now());
        return NotificationActionResponseDTO.builder()
                .success(true)
                .affectedCount(updatedCount)
                .message("Tất cả thông báo đã được đánh dấu là đã đọc.")
                .build();
    }

    @Override
    @Transactional
    public void deleteNotification(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserIdAndIsDeletedFalse(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Thông báo không tồn tại hoặc đã bị xóa"));

        notification.setDeleted(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public NotificationActionResponseDTO clearReadNotifications(UUID userId) {
        int count = notificationRepository.softDeleteAllReadByUserId(userId);
        return NotificationActionResponseDTO.builder()
                .success(true)
                .affectedCount(count)
                .message("Đã xóa tất cả thông báo đã đọc.")
                .build();
    }

    @Override
    @Transactional
    public NotificationActionResponseDTO clearAllNotifications(UUID userId) {
        int count = notificationRepository.softDeleteAllByUserId(userId);
        return NotificationActionResponseDTO.builder()
                .success(true)
                .affectedCount(count)
                .message("Đã dọn sạch toàn bộ thông báo.")
                .build();
    }

    private void sendEmailSafely(User user, String subject, String type, String body, String actionUrl) {
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return;
        }

        String fullName = user.getProfile() != null ? user.getProfile().getFullName() : null;
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            try {
                helper.setFrom(fromEmail, "SecondLife");
            } catch (UnsupportedEncodingException e) {
                helper.setFrom(fromEmail);
            }

            helper.setTo(email);
            helper.setSubject(subject);

            String tag = switch (type) {
                case "ORDER" -> "CHỨNG TỪ ĐƠN HÀNG";
                case "INSPECTION" -> "BIÊN BẢN KIỂM ĐỊNH CHÍNH THỨC";
                case "WALLET" -> "BIÊN LAI GIAO DỊCH VÍ";
                case "EKYC" -> "KẾT QUẢ XÁC MINH DANH TÍNH";
                default -> "THÔNG BÁO QUAN TRỌNG";
            };

            String htmlContent = templateBuilder.buildGenericNoticeEmail(
                    fullName,
                    tag,
                    subject,
                    body,
                    "Xem chi tiết",
                    actionUrl
            );

            helper.setText(body, htmlContent);
            mailSender.send(mimeMessage);
            log.info("Financial/Certificate email notification [{}] sent successfully to: {}", type, email);
        } catch (Exception e) {
            log.error("Failed to deliver critical email [{}] to {}: {}", type, email, e.getMessage());
        }
    }

    // =========================================================================
    // Legacy / Auth Notification Methods
    // =========================================================================

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
        notify(user.getId(),
                "Chúc mừng! Bạn đã là Người bán chính thức",
                "Hồ sơ Người bán của bạn tại SecondLife đã được phê duyệt thành công. Bạn có thể bắt đầu đăng bán sản phẩm ngay bây giờ.",
                "EKYC",
                user.getId(),
                "USER",
                "/seller/dashboard");
    }

    @Override
    public void sendSellerVerificationRejected(User user, String reason) {
        send(NotificationMessage.forSellerRejected(user, reason));
        notify(user.getId(),
                "Hồ sơ Người bán bị từ chối phê duyệt",
                "Hồ sơ của bạn chưa được duyệt với lý do: " + (reason != null ? reason : "Thông tin chưa hợp lệ") + ". Vui lòng kiểm tra và cập nhật lại.",
                "EKYC",
                user.getId(),
                "USER",
                "/seller/onboarding");
    }

    @Override
    public void sendPasswordChangedAlert(User user) {
        send(NotificationMessage.forPasswordChanged(user));
        notify(user.getId(),
                "Cảnh báo bảo mật: Mật khẩu vừa thay đổi",
                "Mật khẩu tài khoản của bạn tại SecondLife vừa được cập nhật. Nếu không phải bạn thực hiện, vui lòng liên hệ bộ phận hỗ trợ ngay.",
                "SECURITY",
                user.getId(),
                "USER",
                "/profile/security");
    }

    @Override
    public void sendAccountStatusChanged(User user, AccountStatus status) {
        send(NotificationMessage.forAccountStatusChanged(user, status));
        notify(user.getId(),
                "Trạng thái tài khoản đã thay đổi",
                "Tài khoản của bạn tại SecondLife hiện chuyển sang trạng thái: " + (status != null ? status.name() : "UPDATED"),
                "SECURITY",
                user.getId(),
                "USER",
                "/");
    }

    @Override
    public void sendInspectionCenterCreated(User user, String temporaryPassword) {
        send(NotificationMessage.forInspectionCenterCreated(user, temporaryPassword));
    }
}
