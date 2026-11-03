package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewInspectionAppealRequest(
    @NotBlank(message = "Quyết định xử lý là bắt buộc (UPHELD hoặc OVERTURNED)")
    @Pattern(regexp = "^(UPHELD|OVERTURNED)$", message = "Quyết định phải là UPHELD hoặc OVERTURNED")
    String decision,
    String resolutionNote
) {
}

