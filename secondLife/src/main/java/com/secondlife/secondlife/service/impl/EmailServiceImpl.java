package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Override
    public void sendVerificationEmail(String toEmail, String fullName, String verificationToken) {
        log.info("Preparing verification email for: {}", toEmail);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(toEmail);
            message.setSubject("SecondLife - Verify Your Email Address");
            message.setText(String.format(
                    "Hello %s,\n\nWelcome to SecondLife! Please use the following token to verify your email address:\n\n%s\n\nBest regards,\nSecondLife Team",
                    fullName, verificationToken
            ));
            mailSender.send(message);
            log.info("Verification email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send email to {} (SMTP may be unreachable in dev/test): {}", toEmail, e.getMessage());
        }
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetToken) {
        log.info("Preparing password reset email for: {}", toEmail);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(toEmail);
            message.setSubject("SecondLife - Password Reset Request");
            message.setText(String.format(
                    "Hello %s,\n\nYou recently requested to reset your password. Please use the following token:\n\n%s\n\nIf you did not request this, please ignore this email.\n\nBest regards,\nSecondLife Team",
                    fullName, resetToken
            ));
            mailSender.send(message);
            log.info("Password reset email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }
}
