package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PostSubmitResponse(
    String status, // ACTIVE, PENDING_INSPECTION hoặc REJECTED
    boolean inspectionRequired,
    BigDecimal inspectionFee,  // null nếu không cần kiểm định
    BigDecimal shippingFee,    // null nếu không cần kiểm định
    String message,
    BigDecimal creditShortfall, // Legacy response field; always null in the listing-credit flow.
    UUID inspectionOrderId,
    BigDecimal inspectionThreshold,
    Long inspectionPolicyVersion,
    String nextAction
) {
    public PostSubmitResponse(String status, boolean inspectionRequired, BigDecimal inspectionFee,
            BigDecimal shippingFee, String message, BigDecimal creditShortfall) {
        this(status, inspectionRequired, inspectionFee, shippingFee, message, creditShortfall, null, null, null, null);
    }
}
