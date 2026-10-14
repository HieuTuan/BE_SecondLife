package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.response.NotificationActionResponseDTO;
import com.secondlife.secondlife.dto.response.NotificationResponseDTO;
import com.secondlife.secondlife.dto.response.UnreadCountResponseDTO;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification Controller", description = "Quản lý thông báo in-app cho người dùng hệ thống")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    public NotificationController(NotificationService notificationService, CurrentUserProvider currentUserProvider) {
        this.notificationService = notificationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem danh sách thông báo của người dùng", description = "Hỗ trợ phân trang, lọc theo trạng thái chưa đọc (unreadOnly) và loại thông báo (type)")
    public ResponseEntity<com.secondlife.secondlife.common.PageResponse<NotificationResponseDTO>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) String type,
            @ParameterObject @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(com.secondlife.secondlife.common.PageResponse.from(
                notificationService.getUserNotifications(userId, unreadOnly, type, pageable)));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy số lượng thông báo chưa đọc", description = "Trả về số lượng thông báo chưa đọc để hiển thị badge quả chuông trên Header/Navbar")
    public ResponseEntity<UnreadCountResponseDTO> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu 1 thông báo là đã đọc")
    public ResponseEntity<NotificationResponseDTO> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(notificationService.markAsRead(userId, id));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu tất cả thông báo là đã đọc")
    public ResponseEntity<NotificationActionResponseDTO> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(notificationService.markAllAsRead(userId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa 1 thông báo cụ thể (soft delete)")
    public ResponseEntity<NotificationActionResponseDTO> deleteNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        notificationService.deleteNotification(userId, id);
        return ResponseEntity.ok(NotificationActionResponseDTO.builder()
                .success(true)
                .affectedCount(1)
                .message("Đã xóa thông báo thành công.")
                .build());
    }

    @DeleteMapping("/clear-read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa tất cả thông báo đã đọc")
    public ResponseEntity<NotificationActionResponseDTO> clearReadNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(notificationService.clearReadNotifications(userId));
    }

    @DeleteMapping("/clear-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa toàn bộ mọi thông báo (kể cả chưa đọc)")
    public ResponseEntity<NotificationActionResponseDTO> clearAllNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(notificationService.clearAllNotifications(userId));
    }
}
