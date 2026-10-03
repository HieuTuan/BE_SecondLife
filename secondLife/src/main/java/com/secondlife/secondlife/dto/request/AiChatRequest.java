package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;

@Data
public class AiChatRequest {
    private UUID sessionId; // null to start a new chat
    private UUID postId; // required if starting a new chat
    
    @NotBlank
    @jakarta.validation.constraints.Size(max = 4000)
    private String message;

    @jakarta.validation.constraints.Size(max = 6)
    private java.util.List<org.springframework.web.multipart.MultipartFile> images;
}
