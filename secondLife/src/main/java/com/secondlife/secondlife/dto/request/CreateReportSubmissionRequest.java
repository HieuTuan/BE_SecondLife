package com.secondlife.secondlife.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateReportSubmissionRequest(
        @NotNull UUID requestId,
        @NotBlank @Size(max = 2000) String description,
        @Size(max = 10)
        @Schema(description = "Private evidenceUrl values returned by the report evidence upload endpoints")
        List<@NotBlank @Size(max = 2048) String> evidenceUrls
) {
    public CreateReportSubmissionRequest {
        if (evidenceUrls == null) evidenceUrls = List.of();
    }
}
