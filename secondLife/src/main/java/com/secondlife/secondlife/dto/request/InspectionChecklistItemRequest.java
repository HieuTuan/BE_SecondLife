package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record InspectionChecklistItemRequest(
    @NotBlank(message = "itemTitle is required") @Size(max = 255)
    String itemTitle,
    @Size(max = 4000) String criteriaDescription,
    boolean mandatory,
    @Min(0) Integer orderIndex
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
