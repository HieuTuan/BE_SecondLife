package com.secondlife.secondlife.notification.channel;

import com.secondlife.secondlife.notification.NotificationChannelType;
import com.secondlife.secondlife.notification.NotificationMessage;
import com.secondlife.secondlife.service.impl.EmailTemplateBuilder;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final EmailTemplateBuilder templateBuilder;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public void send(NotificationMessage message) {
        String recipientEmail = message.recipient().email();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("Cannot send email notification: recipient email is missing for type {}", message.type());
            return;
        }

        String recipientName = message.recipient().fullName();
        String subject = message.subject();
        String htmlContent = renderHtml(message, recipientName);
        String textContent = renderFallbackText(message, recipientName);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            try {
                helper.setFrom(fromEmail, "SecondLife");
            } catch (UnsupportedEncodingException e) {
                helper.setFrom(fromEmail);
            }

            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(textContent, htmlContent);

            mailSender.send(mimeMessage);
            log.info("Email notification [{}] sent successfully to: {}", message.type(), recipientEmail);
        } catch (MessagingException e) {
            log.error("Failed to send email notification [{}] to: {}. Error: {}",
                    message.type(), recipientEmail, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending email notification [{}] to: {}. Error: {}",
                    message.type(), recipientEmail, e.getMessage(), e);
        }
    }

    private String renderHtml(NotificationMessage message, String recipientName) {
        return switch (message.type()) {
            case VERIFICATION_OTP -> {
                String otp = (String) message.payload().getOrDefault("otp", "");
                yield templateBuilder.buildVerificationOtpEmail(recipientName, otp);
            }
            case PASSWORD_RESET_OTP -> {
                String otp = (String) message.payload().getOrDefault("otp", "");
                yield templateBuilder.buildPasswordResetOtpEmail(recipientName, otp);
            }
            case SELLER_VERIFICATION_APPROVED ->
                    templateBuilder.buildSellerVerificationApprovedEmail(recipientName);
            case SELLER_VERIFICATION_REJECTED -> {
                String reason = (String) message.payload().getOrDefault("reason", "");
                yield templateBuilder.buildSellerVerificationRejectedEmail(recipientName, reason);
            }
            case PASSWORD_CHANGED ->
                    templateBuilder.buildPasswordChangedAlertEmail(recipientName);
            case ACCOUNT_STATUS_CHANGED -> {
                String status = (String) message.payload().getOrDefault("status", "");
                yield templateBuilder.buildAccountStatusChangedEmail(recipientName, status);
            }
            case INSPECTION_CENTER_CREATED -> {
                String tempPassword = (String) message.payload().getOrDefault("temporaryPassword", "");
                yield templateBuilder.buildInspectionCenterWelcomeEmail(recipientName, message.recipient().email(), tempPassword);
            }
        };
    }

    private String renderFallbackText(NotificationMessage message, String recipientName) {
        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName : "Quý khách";
        return switch (message.type()) {
            case VERIFICATION_OTP -> {
                String otp = (String) message.payload().getOrDefault("otp", "");
                yield String.format("Xin chào %s,\n\nMã xác thực OTP tài khoản SecondLife của bạn là: %s\nMã có hiệu lực trong 15 phút.\n\nTrân trọng,\nĐội ngũ SecondLife", name, otp);
            }
            case PASSWORD_RESET_OTP -> {
                String otp = (String) message.payload().getOrDefault("otp", "");
                yield String.format("Xin chào %s,\n\nMã OTP đặt lại mật khẩu của bạn là: %s\nMã có hiệu lực trong 15 phút.\n\nTrân trọng,\nĐội ngũ SecondLife", name, otp);
            }
            case SELLER_VERIFICATION_APPROVED ->
                    String.format("Xin chào %s,\n\nChúc mừng! Hồ sơ Người bán của bạn tại SecondLife đã được phê duyệt thành công.\n\nTrân trọng,\nĐội ngũ SecondLife", name);
            case SELLER_VERIFICATION_REJECTED -> {
                String reason = (String) message.payload().getOrDefault("reason", "");
                yield String.format("Xin chào %s,\n\nHồ sơ Người bán của bạn chưa được duyệt với lý do: %s\n\nTrân trọng,\nĐội ngũ SecondLife", name, reason);
            }
            case PASSWORD_CHANGED ->
                    String.format("Xin chào %s,\n\nMật khẩu tài khoản của bạn tại SecondLife vừa được thay đổi. Nếu bạn không thực hiện việc này, vui lòng liên hệ ngay với chúng tôi.\n\nTrân trọng,\nĐội ngũ SecondLife", name);
            case ACCOUNT_STATUS_CHANGED -> {
                String status = (String) message.payload().getOrDefault("status", "");
                yield String.format("Xin chào %s,\n\nTrạng thái tài khoản của bạn tại SecondLife hiện tại là: %s.\n\nTrân trọng,\nĐội ngũ SecondLife", name, status);
            }
            case INSPECTION_CENTER_CREATED -> {
                String tempPassword = (String) message.payload().getOrDefault("temporaryPassword", "");
                yield String.format("Xin chào %s,\n\nTài khoản Trung tâm Kiểm định đã được khởi tạo.\nTên đăng nhập: %s\nMật khẩu ban đầu: %s\n\nTrân trọng,\nĐội ngũ SecondLife", name, message.recipient().email(), tempPassword);
            }
        };
    }
}
