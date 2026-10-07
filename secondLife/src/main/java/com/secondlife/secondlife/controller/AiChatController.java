package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.response.AiChatResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "AI Chatbot APIs")
public class AiChatController {

    private final AiChatService aiChatService;
    private final CurrentUserProvider currentUserProvider;

    @InitBinder
    public void bindOptionalImages(org.springframework.web.bind.WebDataBinder binder) {
        binder.registerCustomEditor(org.springframework.web.multipart.MultipartFile.class, new java.beans.PropertyEditorSupport() {
            @Override public void setAsText(String text) {
                if (text == null || text.isBlank()) setValue(null);
                else throw new IllegalArgumentException("images must be uploaded files, not text");
            }
        });
    }

    @PostMapping(consumes = {"multipart/form-data", "application/x-www-form-urlencoded"})
    @PreAuthorize("hasAuthority('AI_CHAT_SELF')")
    @Operation(summary = "Send a message to the AI Chatbot")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AiChatRequest request
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        AiChatResponse response = aiChatService.processChat(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Message processed successfully", response));
    }
}
