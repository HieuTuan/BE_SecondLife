package com.secondlife.secondlife.service;

import java.math.BigDecimal;

public interface CommissionCalculator {
    record Breakdown(BigDecimal rawCommission, BigDecimal platformCommission, BigDecimal sellerPayout) { }

    Breakdown calculate(BigDecimal base, BigDecimal rate, BigDecimal min, BigDecimal max);
}
