package com.secondlife.secondlife.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

public record OrderFeedItemResponse(
        OrderResponseDTO order,
        InspectionOrderResponse inspection,
        String buyerName,
        String sellerName,
        String postImageUrl,
        UUID categoryId,
        UUID itemId,
        String itemCondition,
        @JsonInclude(JsonInclude.Include.NON_NULL) SellerCommissionPreviewResponse sellerCommissionPreview,
        @JsonInclude(JsonInclude.Include.NON_NULL) OrderReturnResponse orderReturn,
        boolean hasActiveDispute
) {
}
