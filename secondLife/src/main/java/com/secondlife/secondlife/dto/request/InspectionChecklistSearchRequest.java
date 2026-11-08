package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.enums.InspectionChecklistStatus;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InspectionChecklistSearchRequest(
        @Size(max = 120, message = "Từ khóa tìm kiếm tối đa 120 ký tự") String keyword,
        InspectionChecklistStatus status,
        UUID categoryId,
        UUID itemId,
        InspectionComplexityLevel complexityLevel,
        UUID inspectionCenterId,
        Boolean active
) {
}
