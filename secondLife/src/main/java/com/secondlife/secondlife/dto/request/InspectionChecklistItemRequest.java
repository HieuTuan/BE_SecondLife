package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record InspectionChecklistItemRequest(
    @NotBlank(message = "itemTitle is required")
    String itemTitle,
    String criteriaDescription,
    boolean mandatory,
    Integer orderIndex
) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static InspectionChecklistItemRequest fromJson(InspectionChecklistItemRequestBuilder builder) {
        return builder.build();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class InspectionChecklistItemRequestBuilder {
        private boolean mandatory = true;
        private Integer orderIndex = 0;
    }
}
