package com.secondlife.secondlife.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record InspectionChecklistTemplateRequest(
    UUID categoryId,
    @NotBlank(message = "title is required")
    String title,
    String description,
    @Valid
    List<InspectionChecklistItemRequest> items
) {
}
