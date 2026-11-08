package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.InspectionChecklistStatus;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record InspectionChecklistResponse(
    UUID id,
    UUID categoryId,
    String categoryName,
    String title,
    String description,
    boolean active,
    List<InspectionChecklistItemResponse> items,
    Instant createdAt,
    Instant updatedAt,
    UUID itemId,
    String itemName,
    InspectionComplexityLevel complexityLevel,
    InspectionChecklistStatus approvalStatus,
    int revision,
    UUID supersedesId,
    UUID createdById,
    UUID reviewedById,
    Instant reviewedAt,
    String rejectReason,
    InspectionChecklistPricingResponse pricing
) {
}
