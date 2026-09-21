package com.secondlife.secondlife.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;

@Data
public class AiChatRequest {
    private UUID sessionId; // null if it's a new session
    
    @NotBlank
    private String message;

    private String base64Image; // Optional image for Llava vision processing
}
