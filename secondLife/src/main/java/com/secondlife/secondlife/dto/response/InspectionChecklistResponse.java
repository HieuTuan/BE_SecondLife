package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionChecklistResponse {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private String title;
    private String description;
    private boolean active;
    private List<InspectionChecklistItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;
}
