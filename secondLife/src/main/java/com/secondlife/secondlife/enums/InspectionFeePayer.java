package com.secondlife.secondlife.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum InspectionFeePayer {
    BUYER_PAYS(100),
    SELLER_PAYS(0),
    SPLIT_50_50(50),
    CUSTOM(null);

    private final Integer buyerPercent;

    InspectionFeePayer(Integer buyerPercent) {
        this.buyerPercent = buyerPercent;
    }

    public Integer buyerPercent() {
        return buyerPercent;
    }

    public static InspectionFeePayer fromBuyerPercent(int percent) {
        return switch (percent) {
            case 0 -> SELLER_PAYS;
            case 50 -> SPLIT_50_50;
            case 100 -> BUYER_PAYS;
            default -> CUSTOM;
        };
    }

    public BigDecimal buyerShare(BigDecimal amount) {
        if (buyerPercent == null) {
            throw new IllegalStateException("Custom fee splits require a buyer percentage");
        }
        return buyerShare(amount, buyerPercent);
    }

    public static BigDecimal buyerShare(BigDecimal amount, int percent) {
        if (percent == 0) return BigDecimal.ZERO;
        if (percent == 100) return amount;
        return amount.multiply(BigDecimal.valueOf(percent)).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }
}
