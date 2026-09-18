package com.example.budgetapp.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InterestCalculatorTest {
    @Test
    public void simpleInterest_usesFixedPrincipalForAllDays() {
        assertEquals(2.0, InterestCalculator.interest(1000, 36.5, 2, false), 0.000001);
    }

    @Test
    public void compoundInterest_includesInterestOnInterest() {
        assertEquals(2.001, InterestCalculator.interest(1000, 36.5, 2, true), 0.000001);
    }

    @Test
    public void invalidValuesDoNotCreateMoney() {
        assertEquals(0, InterestCalculator.interest(-1, 10, 1, true), 0.000001);
        assertEquals(0, InterestCalculator.interest(100, 10, 0, false), 0.000001);
        assertEquals(0, InterestCalculator.interest(100, Double.NaN, 1, true), 0.000001);
    }

    @Test
    public void expectedReturn_supportsSimpleAndCompoundModes() {
        assertEquals(1010.0, InterestCalculator.expectedReturn(1000, 12, 1, false), 0.000001);
        assertEquals(1010.0, InterestCalculator.expectedReturn(1000, 12, 1, true), 0.000001);
    }
}
