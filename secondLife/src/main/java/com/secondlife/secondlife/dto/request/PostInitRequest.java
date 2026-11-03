package com.secondlife.secondlife.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "Product photos: 3–6 static JPEG/PNG/WebP files by default; <=10 MiB/file, short side >=600px. Limits are configurable.")
    List<MultipartFile> images,
    // Optional mode: "ai" (default) or "manual"
    String mode
) {

    // Optional: title, description if the user provides them early
}

