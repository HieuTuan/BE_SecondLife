package com.secondlife.secondlife.dto.response;

import java.util.List;
import java.util.Map;

public record OrderFeedResponse(
        List<OrderFeedItemResponse> items,
        OrderFeedItemResponse detail,
        Map<String, Long> counts,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
