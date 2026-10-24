package com.secondlife.secondlife.integration.mail;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.integration.mail.EmailTemplateBuilder;
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
public class CriticalNotificationMailer {
    private final JavaMailSender mailSender;
    private final EmailTemplateBuilder templateBuilder;

    @Value("${MAIL_FROM}")
    private String fromEmail;

    public void send(User user, String subject, String type, String body, String actionUrl) {
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
}
