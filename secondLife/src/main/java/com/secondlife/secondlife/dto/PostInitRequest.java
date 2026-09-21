package com.secondlife.secondlife.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
public class PostInitRequest {
    private UUID categoryId;
    private String base64Image;
    // Optional: title, description if the user provides them early
}
