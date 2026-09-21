package com.secondlife.secondlife.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;

@Data
public class AiChatRequest {
    private UUID sessionId; // null to start a new chat
    private UUID postId; // required if starting a new chat
    
    @NotBlank
    private String message;

    private String base64Image; // Optional: for image analysis with llava
}
