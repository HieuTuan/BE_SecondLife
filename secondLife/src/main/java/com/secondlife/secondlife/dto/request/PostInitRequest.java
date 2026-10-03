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
    @jakarta.validation.constraints.Size(min = 3, max = 6, message = "Upload between 3 and 6 product images")
    private java.util.List<MultipartFile> images;
    // Optional: title, description if the user provides them early
}
