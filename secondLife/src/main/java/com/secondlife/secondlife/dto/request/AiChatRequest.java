package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public record AiChatRequest(
    UUID sessionId, // null to start a new chat
    UUID postId, // required if starting a new chat
    @NotBlank
    @Size(max = 4000)
    String message,
    List<MultipartFile> images
) {
}
