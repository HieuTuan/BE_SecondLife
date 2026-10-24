package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.response.NotificationActionResponseDTO;
import com.secondlife.secondlife.dto.response.NotificationResponseDTO;
import com.secondlife.secondlife.dto.response.UnreadCountResponseDTO;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.NotificationDeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private NotificationDeviceService deviceService;

    @InjectMocks
    private NotificationController notificationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/notifications: Lấy danh sách thông báo thành công")
    void testGetNotifications() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);

        NotificationResponseDTO item = NotificationResponseDTO.builder()
                .id(UUID.randomUUID())
                .title("Đơn hàng mới")
                .message("Bạn có đơn hàng mới")
                .type("ORDER")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(notificationService.getUserNotifications(eq(userId), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("unreadOnly", "false")
                        .param("type", "ORDER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Đơn hàng mới"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count: Trả về số lượng tin chưa đọc")
    void testGetUnreadCount() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);
        when(notificationService.getUnreadCount(userId)).thenReturn(new UnreadCountResponseDTO(5L));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(5));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read: Đánh dấu đã đọc thành công")
    void testMarkAsRead() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);

        NotificationResponseDTO response = NotificationResponseDTO.builder()
                .id(notificationId)
                .title("Đã đọc")
                .isRead(true)
                .readAt(Instant.now())
                .build();

        when(notificationService.markAsRead(userId, notificationId)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/read-all: Đánh dấu tất cả là đã đọc")
    void testMarkAllAsRead() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);

        NotificationActionResponseDTO actionResponse = NotificationActionResponseDTO.builder()
                .success(true)
                .affectedCount(4)
                .message("Tất cả thông báo đã được đánh dấu là đã đọc.")
                .build();

        when(notificationService.markAllAsRead(userId)).thenReturn(actionResponse);

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.affectedCount").value(4));
    }

    @Test
    @DisplayName("DELETE /api/v1/notifications/{id}: Xóa 1 thông báo cụ thể")
    void testDeleteNotification() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);

        mockMvc.perform(delete("/api/v1/notifications/{id}", notificationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/notifications/clear-read: Xóa các thông báo đã đọc")
    void testClearReadNotifications() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);

        NotificationActionResponseDTO actionResponse = NotificationActionResponseDTO.builder()
                .success(true)
                .affectedCount(7)
                .message("Đã xóa tất cả thông báo đã đọc.")
                .build();

        when(notificationService.clearReadNotifications(userId)).thenReturn(actionResponse);

        mockMvc.perform(delete("/api/v1/notifications/clear-read")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(7));
    }

    @Test
    @DisplayName("DELETE /api/v1/notifications/clear-all: Xóa toàn bộ mọi thông báo")
    void testClearAllNotifications() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);

        NotificationActionResponseDTO actionResponse = NotificationActionResponseDTO.builder()
                .success(true)
                .affectedCount(12)
                .message("Đã dọn sạch toàn bộ thông báo.")
                .build();

        when(notificationService.clearAllNotifications(userId)).thenReturn(actionResponse);

        mockMvc.perform(delete("/api/v1/notifications/clear-all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(12));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/devices: Đăng ký token thiết bị FCM thành công")
    void testRegisterDeviceToken() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);

        mockMvc.perform(post("/api/v1/notifications/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"test_fcm_token_123\",\"deviceType\":\"ANDROID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng ký token thiết bị thành công"));

        verify(deviceService).registerDevice(eq(userId), argThat(request ->
                "test_fcm_token_123".equals(request.token()) && "ANDROID".equals(request.deviceType())));
    }

    @Test
    @DisplayName("DELETE /api/v1/notifications/devices/{token}: Hủy đăng ký token thiết bị FCM khi đăng xuất")
    void testUnregisterDeviceToken() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/devices/test_fcm_token_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Hủy đăng ký token thiết bị thành công"));

        verify(deviceService).unregisterDevice("test_fcm_token_123");
    }
}
