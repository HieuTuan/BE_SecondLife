package com.secondlife.secondlife.service;

import java.math.BigDecimal;

public interface ReputationCalculator {
    BigDecimal score(long good, long bad, BigDecimal penalty);
    BigDecimal score(long good, long bad);
}
