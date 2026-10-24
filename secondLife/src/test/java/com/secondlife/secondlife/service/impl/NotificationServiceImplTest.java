package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.NotificationActionResponseDTO;
import com.secondlife.secondlife.dto.response.NotificationResponseDTO;
import com.secondlife.secondlife.dto.response.UnreadCountResponseDTO;
import com.secondlife.secondlife.entity.Notification;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.NotificationChannelType;
import com.secondlife.secondlife.integration.mail.CriticalNotificationMailer;
import com.secondlife.secondlife.integration.notification.NotificationChannel;
import com.secondlife.secondlife.integration.notification.NotificationMessage;
import com.secondlife.secondlife.mapper.NotificationMapper;
import com.secondlife.secondlife.repository.NotificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private NotificationChannel emailChannel;

    @Mock
    private NotificationChannel smsChannel;

    @Mock
    private CriticalNotificationMailer criticalMailer;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                userRepository,
                messagingTemplate,
                List.of(emailChannel, smsChannel),
                criticalMailer
        , MapperTestSupport.mapper(NotificationMapper.class));
    }

    @Test
    void notify_ShouldSaveNotificationAndPushRealtimeWebSocket() {
        UUID userId = UUID.randomUUID();
        User user = new User("buyer@example.com", "pass", AccountStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Notification saved = Notification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("Đơn hàng mới")
                .message("Bạn có đơn hàng mới")
                .type("ORDER")
                .isRead(false)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        NotificationResponseDTO response = notificationService.notify(
                userId, "Đơn hàng mới", "Bạn có đơn hàng mới", "ORDER", UUID.randomUUID(), "ORDER", "/orders"
        );

        assertNotNull(response);
        assertEquals("Đơn hàng mới", response.title());
        verify(notificationRepository).save(any(Notification.class));
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/notifications"), eq(response));
    }

    @Test
    void reportNotificationsDoNotDispatchExternalChannels() {
        UUID reporterId=UUID.randomUUID(),reportId=UUID.randomUUID();
        User user=new User("reporter@example.test","hash",AccountStatus.ACTIVE);
        when(userRepository.findById(reporterId)).thenReturn(Optional.of(user));
        var saved=Notification.builder().id(UUID.randomUUID()).user(user).title("Report result").message("Reviewed")
                .type("REPORT").referenceId(reportId).createdAt(Instant.now()).isRead(false).isDeleted(false).build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        var report=new com.secondlife.secondlife.dto.response.ReportResponse(reportId,UUID.randomUUID(),reporterId,
                UUID.randomUUID(),UUID.randomUUID(),null,
                com.secondlife.secondlife.dto.request.ReportRequests.ReasonCode.OTHER,"Report details",List.of(),
                com.secondlife.secondlife.dto.request.ReportRequests.Status.DISMISSED,UUID.randomUUID(),
                com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty.NONE,"No violation found",
                Instant.now(),Instant.now(),Instant.now(),null,
                com.secondlife.secondlife.dto.request.ReportRequests.TargetType.LISTING);
        new com.secondlife.secondlife.service.impl.ListingReportNotificationsImpl(
                userRepository,notificationService).resolved(report);
        verify(notificationRepository).save(any(Notification.class));
        verify(messagingTemplate).convertAndSendToUser(eq(reporterId.toString()),eq("/queue/notifications"),any(Object.class));
        verifyNoInteractions(emailChannel,smsChannel,criticalMailer);
    }

    @Test
    void getUnreadCount_ShouldReturnCountFromRepository() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.countByUserIdAndIsReadFalseAndIsDeletedFalse(userId)).thenReturn(5L);

        UnreadCountResponseDTO result = notificationService.getUnreadCount(userId);

        assertEquals(5L, result.unreadCount());
    }

    @Test
    void markAsRead_ShouldUpdateReadStatusAndTimestamp() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        User user = new User("user@example.com", "pass", AccountStatus.ACTIVE);

        Notification notification = Notification.builder()
                .id(notificationId)
                .user(user)
                .title("Thông báo")
                .message("Nội dung")
                .type("SYSTEM")
                .isRead(false)
                .isDeleted(false)
                .build();

        when(notificationRepository.findByIdAndUserIdAndIsDeletedFalse(notificationId, userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDTO result = notificationService.markAsRead(userId, notificationId);

        assertTrue(result.isRead());
        assertNotNull(result.readAt());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAllAsRead_ShouldCallRepositoryAndReturnUpdatedCount() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.markAllAsRead(eq(userId), any(Instant.class))).thenReturn(3);

        NotificationActionResponseDTO response = notificationService.markAllAsRead(userId);

        assertTrue(response.success());
        assertEquals(3, response.affectedCount());
    }

    @Test
    void deleteNotification_ShouldSetIsDeletedTrue() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .isDeleted(false)
                .build();

        when(notificationRepository.findByIdAndUserIdAndIsDeletedFalse(notificationId, userId))
                .thenReturn(Optional.of(notification));

        notificationService.deleteNotification(userId, notificationId);

        assertTrue(notification.isDeleted());
        verify(notificationRepository).save(notification);
    }

    @Test
    void clearReadNotifications_ShouldCallSoftDeleteRead() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.softDeleteAllReadByUserId(userId)).thenReturn(8);

        NotificationActionResponseDTO response = notificationService.clearReadNotifications(userId);

        assertTrue(response.success());
        assertEquals(8, response.affectedCount());
    }

    @Test
    void clearAllNotifications_ShouldCallSoftDeleteAll() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.softDeleteAllByUserId(userId)).thenReturn(15);

        NotificationActionResponseDTO response = notificationService.clearAllNotifications(userId);

        assertTrue(response.success());
        assertEquals(15, response.affectedCount());
    }

    @Test
    void getUserNotifications_ShouldQueryRepositoryWithPageable() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> emptyPage = new PageImpl<>(List.of());

        when(notificationRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(emptyPage);

        Page<NotificationResponseDTO> result = notificationService.getUserNotifications(userId, false, null, pageable);

        assertNotNull(result);
        verify(notificationRepository).findByUserIdAndIsDeletedFalse(userId, pageable);
    }

    @Test
    void sendVerificationOtp_ShouldDispatchToEmailChannel() {
        when(emailChannel.supports(NotificationChannelType.EMAIL)).thenReturn(true);

        notificationService.sendVerificationOtp("test@example.com", "Test User", "123456");

        verify(emailChannel, times(1)).send(any(NotificationMessage.class));
        verify(smsChannel, never()).send(any(NotificationMessage.class));
    }
}
