package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public record PostInitRequest(
    @NotNull
    UUID categoryId,
    UUID itemId,
    String brand,
    // Optional mode: "ai" (default) or "manual"
    String model,
    @NotNull
    List<MultipartFile> images,
    // Optional mode: "ai" (default) or "manual"
    String mode
) {

    // Optional: title, description if the user provides them early
}

