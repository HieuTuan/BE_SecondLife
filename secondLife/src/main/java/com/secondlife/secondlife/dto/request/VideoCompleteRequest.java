package com.secondlife.secondlife.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
public record VideoCompleteRequest(@NotNull UUID uploadId, @NotBlank @Size(max=200) String publicId,
        @Size(max=2048) String videoUrl, @Positive Long bytes, @Size(max=10) String format,
        @Positive Double duration) { }
