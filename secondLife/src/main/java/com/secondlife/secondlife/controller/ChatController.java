package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.SendMessageRequest;
import com.secondlife.secondlife.dto.response.ChatMessageDto;
import com.secondlife.secondlife.dto.response.ChatRoomDto;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/rooms")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatRoomDto>> getOrCreateRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam UUID postId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(chatService.getOrCreateRoom(postId, userId)));
    }

    @GetMapping("/rooms")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatRoomDto>> getRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam UUID postId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        ChatRoomDto room = chatService.getRoom(postId, userId);
        if (room == null) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ChatRoomDto>>> getUserRooms(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(chatService.getUserRooms(userId)));
    }

    @GetMapping("/{roomId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID roomId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(chatService.getMessages(roomId, userId)));
    }

    @PostMapping("/{roomId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatMessageDto>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID roomId,
            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        ChatMessageDto message = chatService.sendMessage(roomId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(message));
    }
}
