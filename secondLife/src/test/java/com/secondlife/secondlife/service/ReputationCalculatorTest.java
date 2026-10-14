package com.secondlife.secondlife.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReputationCalculatorTest {
    private final ReputationCalculator calculator=new ReputationCalculator();
    @Test void noTransactionsHaveNoScore() { assertNull(calculator.score(0,0)); }
    @Test void scoreEqualsPercentageOfGoodEvaluatedTransactions() {
        assertEquals("100.00",calculator.score(1,0).toPlainString());
        assertEquals("85.00",calculator.score(17,3).toPlainString());
        assertEquals("0.00",calculator.score(0,1).toPlainString());
        assertEquals("66.67",calculator.score(2,1).toPlainString());
        assertThrows(IllegalArgumentException.class,()->calculator.score(-1,0));
    }
}
