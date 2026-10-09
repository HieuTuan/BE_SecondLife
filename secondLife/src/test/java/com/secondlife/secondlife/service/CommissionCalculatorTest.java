package com.secondlife.secondlife.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CommissionCalculatorTest {
    @ParameterizedTest
    @CsvSource({
            "1000000, 0.05, 0, null, 50000, 950000",
            "1000000, 0.05, 0, 30000, 30000, 970000",
            "1000000, 0.01, 20000, null, 20000, 980000",
            "10000, 0.05, 20000, null, 10000, 0",
            "333.33, 0.05, 0, null, 16.67, 316.66",
            "1000000, 0, 0, null, 0, 1000000",
            "1000000, 1, 0, null, 1000000, 0"
    })
    void appliesMinimumMaximumAndNeverExceedsProductPrice(String base, String rate, String min, String max, String fee, String payout) {
        var result = new CommissionCalculator().calculate(new BigDecimal(base), new BigDecimal(rate), new BigDecimal(min),
                "null".equals(max) ? null : new BigDecimal(max));
        assertEquals(0, new BigDecimal(fee).compareTo(result.platformCommission()));
        assertEquals(0, new BigDecimal(payout).compareTo(result.sellerPayout()));
    }
}
