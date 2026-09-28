package com.secondlife.secondlife.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
public class PostInitRequest {
    private UUID categoryId;
    private UUID itemId;
    private MultipartFile image;
    // Optional: title, description if the user provides them early
}
