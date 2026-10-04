package com.secondlife.secondlife.service;

import com.secondlife.secondlife.exception.EmailDeliveryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class SellerOnboardingMailer {
    private final JavaMailSender sender;
    private final String from;

    public SellerOnboardingMailer(JavaMailSender sender, @Value("${app.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public void sendCode(String email, String otp, Instant expiresAt) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Mã xác nhận email Shop SecondLife");
        message.setText("Mã xác nhận email đăng ký người bán SecondLife của bạn: " + otp
                + "\nMã hết hạn lúc: " + expiresAt + " (UTC)."
                + "\nChỉ sử dụng mã này tại bước xác nhận thông tin shop trước eKYC.");
        try {
            sender.send(message);
        } catch (MailException failure) {
            // SMTP exceptions can contain credentials or the message body; do not expose them.
            throw new EmailDeliveryException();
        }
    }
}
