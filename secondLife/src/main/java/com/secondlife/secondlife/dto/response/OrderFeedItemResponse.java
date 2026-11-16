package com.secondlife.secondlife.dto.response;

import java.util.UUID;

public record OrderFeedItemResponse(
        OrderResponseDTO order,
        InspectionOrderResponse inspection,
        String buyerName,
        String sellerName,
        String postImageUrl,
        UUID categoryId,
        UUID itemId,
        String itemCondition
) {
}
