package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;

public record PostSubmitResponse(
    String status, // ACTIVE, PENDING_INSPECTION hoặc REJECTED
    boolean inspectionRequired,
    BigDecimal inspectionFee,  // null nếu không cần kiểm định
    BigDecimal shippingFee,    // null nếu không cần kiểm định
    String message,
    BigDecimal creditShortfall // Legacy response field; always null in the listing-credit flow.
) {
}
