package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.common.InspectionCenterIdDeserializer;
import com.secondlife.secondlife.enums.InspectionOrderType;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.util.UUID;

public record PreListingInspectionQuoteRequest(
        @NotNull InspectionOrderType orderType,
        @JsonDeserialize(using = InspectionCenterIdDeserializer.class) UUID inspectionCenterId) {
}
