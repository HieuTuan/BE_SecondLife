package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionChecklistItemResponse {
    private UUID id;
    private String itemTitle;
    private String criteriaDescription;
    private boolean mandatory;
    private Integer orderIndex;
}
