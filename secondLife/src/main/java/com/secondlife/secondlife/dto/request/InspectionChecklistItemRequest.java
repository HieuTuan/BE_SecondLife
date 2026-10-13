package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionChecklistItemRequest {
    @NotBlank(message = "itemTitle is required")
    private String itemTitle;

    private String criteriaDescription;
    private boolean mandatory = true;
    private Integer orderIndex = 0;
}
