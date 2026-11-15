package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.common.InspectionCenterIdDeserializer;
import com.secondlife.secondlife.enums.InspectionOrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record InspectionQuoteRequest(
    @NotNull(message = "postId is required")
    UUID postId,
    @Valid
    ShippingAddressRequest buyerAddress,
    BigDecimal buyerLatitude,
    BigDecimal buyerLongitude,
    @Schema(description = "UUID của trung tâm hoặc AUTO để chọn theo kho người bán", type = "string")
    @JsonDeserialize(using = InspectionCenterIdDeserializer.class)
    UUID inspectionCenterId,
    UUID expectedFeeProposalId,
    UUID expectedChecklistTemplateId,
    InspectionOrderType orderType,
    UUID inspectionShippingQuoteId
) {
}
