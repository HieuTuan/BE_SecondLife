package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.service.ReputationCalculator;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
@Service("reputationCalculator")
public class ReputationCalculatorImpl implements ReputationCalculator {
    @Override
    public BigDecimal score(long good,long bad,BigDecimal penalty) {
        if(penalty==null || penalty.signum()<0) throw new IllegalArgumentException("Penalty points cannot be negative");
        var base=score(good,bad);
        return base==null?null:base.subtract(penalty).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);
    }
    @Override
    public BigDecimal score(long good,long bad) {
        if(good<0 || bad<0) throw new IllegalArgumentException("Reputation transaction counts cannot be negative");
        var total=BigDecimal.valueOf(good).add(BigDecimal.valueOf(bad));
        return total.signum()==0 ? null : BigDecimal.valueOf(good).multiply(BigDecimal.valueOf(100)).divide(total,2,RoundingMode.HALF_UP);
    }
}
