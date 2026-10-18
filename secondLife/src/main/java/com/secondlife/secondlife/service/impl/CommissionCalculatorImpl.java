package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.service.CommissionCalculator;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service("commissionCalculator")
public class CommissionCalculatorImpl implements CommissionCalculator {

    @Override
    public Breakdown calculate(BigDecimal base, BigDecimal rate, BigDecimal min, BigDecimal max) {
        if (base == null || base.signum() <= 0 || rate == null || rate.signum() < 0
                || rate.compareTo(BigDecimal.ONE) > 0 || min == null || min.signum() < 0
                || (max != null && max.compareTo(min) < 0))
            throw new IllegalArgumentException("Invalid commission calculation inputs");
        BigDecimal raw = base.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal capped = max == null ? raw : raw.min(max);
        BigDecimal commission = capped.max(min).min(base).setScale(2, RoundingMode.HALF_UP);
        return new Breakdown(raw, commission, base.subtract(commission).setScale(2, RoundingMode.HALF_UP));
    }
}
