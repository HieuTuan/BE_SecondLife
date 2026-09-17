package com.secondlife.secondlife.service;

public interface EmailService {
    void sendVerificationEmail(String toEmail, String fullName, String verificationToken);
    void sendPasswordResetEmail(String toEmail, String fullName, String resetToken);
}
