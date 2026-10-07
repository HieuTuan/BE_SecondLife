package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PostSubmitResponse {
    private String status; // ACTIVE, PENDING_INSPECTION hoặc REJECTED
    private boolean inspectionRequired;
    private BigDecimal inspectionFee;  // null nếu không cần kiểm định
    private BigDecimal shippingFee;    // null nếu không cần kiểm định
    private String message;
    private BigDecimal creditShortfall; // Legacy response field; always null in the listing-credit flow.
}
