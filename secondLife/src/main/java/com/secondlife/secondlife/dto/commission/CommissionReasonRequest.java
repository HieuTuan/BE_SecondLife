package com.secondlife.secondlife.dto.commission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
public record CommissionReasonRequest(@NotBlank @Size(max = 1000)
        @Schema(example = "Disable policy for new orders") String reason) {}
