package com.secondlife.secondlife.notification.channel;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.notification.NotificationChannelType;
import com.secondlife.secondlife.notification.NotificationMessage;
import com.secondlife.secondlife.service.impl.EmailTemplateBuilder;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationChannelTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailTemplateBuilder templateBuilder;

    @InjectMocks
    private EmailNotificationChannel emailNotificationChannel;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailNotificationChannel, "fromEmail", "noreply@secondlife.com");
        lenient().when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    @Test
    void getChannelType_ShouldReturnEmail() {
        assertEquals(NotificationChannelType.EMAIL, emailNotificationChannel.getChannelType());
        assertTrue(emailNotificationChannel.supports(NotificationChannelType.EMAIL));
    }

    @Test
    void send_VerificationOtp_ShouldRenderAndSend() {
        when(templateBuilder.buildVerificationOtpEmail("User A", "123456")).thenReturn("<html>OTP</html>");

        NotificationMessage message = NotificationMessage.forVerificationOtp("user@example.com", "User A", "123456");
        emailNotificationChannel.send(message);

        verify(templateBuilder).buildVerificationOtpEmail("User A", "123456");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_SellerApproved_ShouldRenderAndSend() {
        User user = new User("seller@example.com", "hash", AccountStatus.ACTIVE);
        user.setProfile(new UserProfile(user, "Seller Name", "0123456789", null));
        when(templateBuilder.buildSellerVerificationApprovedEmail("Seller Name")).thenReturn("<html>Approved</html>");

        NotificationMessage message = NotificationMessage.forSellerApproved(user);
        emailNotificationChannel.send(message);

        verify(templateBuilder).buildSellerVerificationApprovedEmail("Seller Name");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_SellerRejected_ShouldRenderAndSend() {
        User user = new User("seller@example.com", "hash", AccountStatus.ACTIVE);
        user.setProfile(new UserProfile(user, "Seller Name", "0123456789", null));
        when(templateBuilder.buildSellerVerificationRejectedEmail("Seller Name", "Blurry photo")).thenReturn("<html>Rejected</html>");

        NotificationMessage message = NotificationMessage.forSellerRejected(user, "Blurry photo");
        emailNotificationChannel.send(message);

        verify(templateBuilder).buildSellerVerificationRejectedEmail("Seller Name", "Blurry photo");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_PasswordChanged_ShouldRenderAndSend() {
        User user = new User("user@example.com", "hash", AccountStatus.ACTIVE);
        when(templateBuilder.buildPasswordChangedAlertEmail(null)).thenReturn("<html>Password changed</html>");

        NotificationMessage message = NotificationMessage.forPasswordChanged(user);
        emailNotificationChannel.send(message);

        verify(templateBuilder).buildPasswordChangedAlertEmail(null);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_AccountStatusChanged_ShouldRenderAndSend() {
        User user = new User("user@example.com", "hash", AccountStatus.LOCKED);
        when(templateBuilder.buildAccountStatusChangedEmail(null, "LOCKED")).thenReturn("<html>Locked</html>");

        NotificationMessage message = NotificationMessage.forAccountStatusChanged(user, AccountStatus.LOCKED);
        emailNotificationChannel.send(message);

        verify(templateBuilder).buildAccountStatusChangedEmail(null, "LOCKED");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_InspectionCenterCreated_ShouldRenderAndSend() {
        User user = new User("center@example.com", "hash", AccountStatus.ACTIVE);
        user.setProfile(new UserProfile(user, "Center One", null, null));
        when(templateBuilder.buildInspectionCenterWelcomeEmail("Center One", "center@example.com", "TempPass@123"))
                .thenReturn("<html>Welcome Center</html>");

        NotificationMessage message = NotificationMessage.forInspectionCenterCreated(user, "TempPass@123");
        emailNotificationChannel.send(message);

        verify(templateBuilder).buildInspectionCenterWelcomeEmail("Center One", "center@example.com", "TempPass@123");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_WhenMailSenderFails_ShouldCatchGracefullyWithoutThrowing() {
        when(templateBuilder.buildVerificationOtpEmail("User A", "123456")).thenReturn("<html>OTP</html>");
        doThrow(new RuntimeException("SMTP Connection failed")).when(mailSender).send(any(MimeMessage.class));

        NotificationMessage message = NotificationMessage.forVerificationOtp("user@example.com", "User A", "123456");
        emailNotificationChannel.send(message);

        verify(mailSender).send(any(MimeMessage.class));
    }
}
