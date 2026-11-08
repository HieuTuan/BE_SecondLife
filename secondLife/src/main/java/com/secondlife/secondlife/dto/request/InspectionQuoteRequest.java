package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.Builder;
import com.secondlife.secondlife.enums.InspectionOrderType;

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
    UUID inspectionCenterId,
    UUID expectedFeeProposalId,
    UUID expectedChecklistTemplateId,
    InspectionOrderType orderType,
    UUID inspectionShippingQuoteId
) {
}
