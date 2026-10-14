package com.secondlife.secondlife.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionChecklistTemplateRequest {

    private UUID categoryId;

    @NotBlank(message = "title is required")
    private String title;

    private String description;

    @Valid
    private List<InspectionChecklistItemRequest> items;
}
