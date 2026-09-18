package com.example.budgetapp.util;

/** Shared interest calculations for investment accounts. */
public final class InterestCalculator {
    private InterestCalculator() {}

    /**
     * Returns interest accrued over the supplied number of days.
     * Annual rates are percentages, for example 3.5 means 3.5%.
     */
    public static double interest(double principal, double annualRatePercent,
                                  long days, boolean compound) {
        if (!Double.isFinite(principal) || !Double.isFinite(annualRatePercent)
                || principal <= 0 || annualRatePercent <= 0 || days <= 0) {
            return 0;
        }
        double dailyRate = annualRatePercent / 100.0 / 365.0;
        if (compound) {
            return principal * (Math.pow(1.0 + dailyRate, days) - 1.0);
        }
        return principal * dailyRate * days;
    }

    /** Expected principal plus interest for a fixed-term product measured in months. */
    public static double expectedReturn(double principal, double annualRatePercent,
                                        int months, boolean compound) {
        if (!Double.isFinite(principal) || !Double.isFinite(annualRatePercent)
                || principal <= 0 || annualRatePercent < 0 || months < 0) {
            return 0;
        }
        if (compound) {
            return principal * Math.pow(1.0 + annualRatePercent / 100.0 / 12.0, months);
        }
        return principal + principal * (annualRatePercent / 100.0) * (months / 12.0);
    }
}
