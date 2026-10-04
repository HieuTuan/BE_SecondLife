package com.secondlife.secondlife.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
public class PostInitRequest {
    @jakarta.validation.constraints.NotNull
    private UUID categoryId;
    private UUID itemId;
    @jakarta.validation.constraints.NotNull
    private java.util.List<MultipartFile> images;
    // Optional: title, description if the user provides them early
}
