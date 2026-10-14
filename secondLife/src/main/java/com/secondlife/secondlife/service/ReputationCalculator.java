package com.secondlife.secondlife.service;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
@Component
public class ReputationCalculator {
    public BigDecimal score(long good,long bad) {
        if(good<0 || bad<0) throw new IllegalArgumentException("Reputation transaction counts cannot be negative");
        var total=BigDecimal.valueOf(good).add(BigDecimal.valueOf(bad));
        return total.signum()==0 ? null : BigDecimal.valueOf(good).multiply(BigDecimal.valueOf(100)).divide(total,2,RoundingMode.HALF_UP);
    }
}
