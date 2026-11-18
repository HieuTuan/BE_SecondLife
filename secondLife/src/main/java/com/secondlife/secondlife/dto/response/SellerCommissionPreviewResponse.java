package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;

public record SellerCommissionPreviewResponse(
        BigDecimal rate,
        BigDecimal platformCommission,
        BigDecimal sellerPayout
) {
}
