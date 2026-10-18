package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record InspectionChecklistItemResponse(
    UUID id,
    String itemTitle,
    String criteriaDescription,
    boolean mandatory,
    Integer orderIndex
) {
}
