package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;

@Data
public class AiChatRequest {
    private UUID sessionId; // null to start a new chat
    private UUID postId; // required if starting a new chat
    
    @NotBlank
    private String message;

    private org.springframework.web.multipart.MultipartFile image; // Optional: for image analysis with llava
}
