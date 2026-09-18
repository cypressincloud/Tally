package com.example.budgetapp.util;

import static org.junit.Assert.assertEquals;

import com.example.budgetapp.database.BudgetPlan;
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.Transaction;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BudgetCalculatorTest {
    @Test
    public void progress_preservesSubPercentSpending() {
        assertEquals(93, BudgetCalculator.progress(18.51, 2000));
        assertEquals(BudgetCalculator.PROGRESS_MAX, BudgetCalculator.progress(2100, 2000));
        assertEquals(0, BudgetCalculator.progress(0, 2000));
    }

    @Test
    public void remainingAmount_offsetsOverspendingAcrossDays() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 2);
        BudgetPlan plan = new BudgetPlan("test", millis(start), millis(end), 200);
        Transaction firstDay = new Transaction(millis(start) + 1000, 0, "餐饮", 150);
        Transaction secondDay = new Transaction(millis(end) + 1000, 0, "餐饮", 10);

        assertEquals(40, BudgetCalculator.remainingAmount(plan, Arrays.asList(firstDay, secondDay)), 0.001);
    }

    @Test
    public void distributeEvenly_preservesCents() {
        Goal first = new Goal("a", 100, 0, false, 0);
        first.id = 1;
        Goal second = new Goal("b", 100, 0, false, 0);
        second.id = 2;
        Goal third = new Goal("c", 100, 0, false, 0);
        third.id = 3;

        Map<Integer, Double> result = BudgetCalculator.distributeEvenly(10, Arrays.asList(first, second, third));

        assertEquals(3.34, result.get(1), 0.001);
        assertEquals(3.33, result.get(2), 0.001);
        assertEquals(3.33, result.get(3), 0.001);
    }

    @Test
    public void distributeEvenlyByParts_preservesTotalWhenNotDivisible() {
        List<Double> values = BudgetCalculator.distributeEvenly(10.01, 3);

        assertEquals(Arrays.asList(3.34, 3.34, 3.33), values);
        assertEquals(10.01, values.stream().mapToDouble(Double::doubleValue).sum(), 0.000001);
    }

    @Test
    public void dailyBudget_allocatesWholePeriodInCents() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 3);
        BudgetPlan plan = new BudgetPlan("test", millis(start), millis(end), 10.01);

        assertEquals(3.34, BudgetCalculator.dailyBudget(plan, start), 0.000001);
        assertEquals(3.34, BudgetCalculator.dailyBudget(plan, start.plusDays(1)), 0.000001);
        assertEquals(3.33, BudgetCalculator.dailyBudget(plan, end), 0.000001);
    }

    @Test
    public void remainingDailyBudget_doesNotShrinkTodayFromTodaySpending() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);
        BudgetPlan plan = new BudgetPlan("test", millis(start), millis(end), 100);
        Transaction firstDay = new Transaction(millis(start) + 1000, 0, "餐饮", 40);
        List<Transaction> transactions = Arrays.asList(firstDay);

        assertEquals(50, BudgetCalculator.remainingDailyBudget(plan, start, transactions), 0.000001);
        assertEquals(60, BudgetCalculator.remainingDailyBudget(plan, end, transactions), 0.000001);
    }

    @Test
    public void expenseBetween_amortizesTransactionAcrossRange() {
        LocalDate purchase = LocalDate.of(2026, 1, 1);
        Transaction transaction = new Transaction(millis(purchase), 0, "购物", 10.01);
        transaction.spreadStartDate = millis(purchase);
        transaction.spreadEndDate = millis(purchase.plusDays(2));

        assertEquals(3.34, BudgetCalculator.expenseBetween(
                Arrays.asList(transaction), millis(purchase), millis(purchase.plusDays(1))), 0.000001);
        assertEquals(10.01, BudgetCalculator.expenseBetween(
                Arrays.asList(transaction), millis(purchase), millis(purchase.plusDays(3))), 0.000001);
    }

    @Test
    public void amountBetween_returnsOnlySlicesInsideRequestedRange() {
        LocalDate purchase = LocalDate.of(2026, 1, 31);
        Transaction transaction = new Transaction(millis(purchase) + 12 * 60 * 60 * 1000L,
                0, "购物", 10.01);
        transaction.spreadStartDate = millis(purchase);
        transaction.spreadEndDate = millis(purchase.plusDays(2));

        assertEquals(3.34, BudgetCalculator.amountBetween(transaction,
                millis(purchase), millis(purchase.plusDays(1))), 0.000001);
        assertEquals(6.67, BudgetCalculator.amountBetween(transaction,
                millis(purchase.plusDays(1)), millis(purchase.plusDays(3))), 0.000001);
        assertEquals(0, BudgetCalculator.amountBetween(transaction,
                millis(purchase.plusDays(3)), millis(purchase.plusDays(4))), 0.000001);
    }

    @Test
    public void amountBetween_keepsOriginalTimestampForOneDayBill() {
        LocalDate day = LocalDate.of(2026, 2, 1);
        Transaction transaction = new Transaction(millis(day) + 12 * 60 * 60 * 1000L,
                0, "餐饮", 12);

        assertEquals(0, BudgetCalculator.amountBetween(transaction,
                millis(day), millis(day) + 12 * 60 * 60 * 1000L), 0.000001);
        assertEquals(12, BudgetCalculator.amountBetween(transaction,
                millis(day), millis(day.plusDays(1))), 0.000001);
    }

    private static long millis(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
