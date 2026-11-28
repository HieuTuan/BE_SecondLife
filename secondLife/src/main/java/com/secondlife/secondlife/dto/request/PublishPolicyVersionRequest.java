package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.enums.PolicyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PublishPolicyVersionRequest(
        @Schema(description = "Policy type (defaults to TERMS_OF_SERVICE)")
        PolicyType policyType,

        @NotBlank(message = "Policy title is required")
        @Size(max = 255, message = "Policy title must not exceed 255 characters")
        String title,

        @NotBlank(message = "Policy version is required")
        @Size(max = 50, message = "Policy version must not exceed 50 characters")
        String version,

        @NotBlank(message = "Policy markdown content is required")
        String contentMarkdown,

        @Schema(description = "Optional official effective timestamp; leave null if pending confirmation")
        Instant effectiveAt,

        @Schema(description = "Whether existing users must re-accept this version (defaults to true)")
        Boolean requiresReacceptance
) {}
