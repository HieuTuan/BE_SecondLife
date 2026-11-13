package com.secondlife.secondlife.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum InspectionFeePayer {
    BUYER_PAYS,
    SELLER_PAYS,
    SPLIT_50_50;

    public BigDecimal buyerShare(BigDecimal amount) {
        return switch (this) {
            case BUYER_PAYS -> amount;
            case SELLER_PAYS -> BigDecimal.ZERO;
            case SPLIT_50_50 -> amount.divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
        };
    }
}
