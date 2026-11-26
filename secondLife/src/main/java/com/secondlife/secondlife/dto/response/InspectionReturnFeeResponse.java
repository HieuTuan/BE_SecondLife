package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;

public record InspectionReturnFeeResponse(
        BigDecimal reserveFee,
        BigDecimal quotedFee,
        BigDecimal actualFee,
        Share buyer,
        Share seller
) {
    public record Share(int percent, BigDecimal collected, BigDecimal outstanding) {}
}