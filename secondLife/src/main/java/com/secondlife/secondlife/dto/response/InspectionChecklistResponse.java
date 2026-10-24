package com.secondlife.secondlife.dto.response;

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
    Instant updatedAt
) {
}
