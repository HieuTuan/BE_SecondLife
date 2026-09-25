package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PostSubmitResponse {
    private String status; // ACTIVE hoặc PENDING_INSPECTION
    private boolean inspectionRequired;
    private BigDecimal inspectionFee;  // null nếu không cần kiểm định
    private BigDecimal shippingFee;    // null nếu không cần kiểm định
    private String message;
    private BigDecimal creditShortfall; // Số tiền còn thiếu nếu không đủ credit (null nếu đủ)
}
