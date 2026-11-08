package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.enums.InspectionComplexityLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record InspectionChecklistTemplateRequest(
    UUID categoryId,
    @NotBlank(message = "title is required") @Size(max = 200)
    String title,
    @Size(max = 10000) String description,
    @NotEmpty @Size(max = 100)
    List<@Valid @NotNull InspectionChecklistItemRequest> items,
    @NotNull(message = "itemId is required") UUID itemId,
    @NotNull(message = "complexityLevel is required (L1-L4)") InspectionComplexityLevel complexityLevel
) {
}
