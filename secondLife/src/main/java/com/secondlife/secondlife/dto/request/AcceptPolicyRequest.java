package com.secondlife.secondlife.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptPolicyRequest(
        @NotNull(message = "Policy version ID is required")
        @Schema(description = "ID of the active policy version being accepted")
        UUID policyVersionId,

        @NotNull(message = "Explicit acceptance confirmation is required")
        @Schema(description = "Must be true to confirm explicit user consent", example = "true")
        Boolean accepted,

        @Schema(description = "Client source: WEB, MOBILE_BUYER, MOBILE_SELLER, MOBILE_INSPECTOR, MOBILE, API", example = "WEB")
        String source,

        @Schema(description = "Acceptance context: POLICY_UPDATE, LOGIN, REGISTER, GOOGLE_LOGIN", example = "POLICY_UPDATE")
        String context
) {}
