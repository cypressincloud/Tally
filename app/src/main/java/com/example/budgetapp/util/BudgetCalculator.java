package com.example.budgetapp.util;

import com.example.budgetapp.database.BudgetPlan;
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.Transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Shared, deterministic budget calculations used by budget and record screens. */
public final class BudgetCalculator {
    public static final int PROGRESS_MAX = 10_000;

    private BudgetCalculator() {}

    /** Keeps sub-percent spending visible without changing the represented ratio. */
    public static int progress(double spent, double total) {
        if (spent <= 0 || total <= 0) return 0;
        return (int) Math.min(PROGRESS_MAX, Math.round(spent * PROGRESS_MAX / total));
    }

    public static double expenseBetween(List<Transaction> transactions, long start, long end) {
        double total = 0;
        if (transactions == null) return total;
        for (Transaction t : transactions) {
            if (t.type == 0 && !t.excludeFromBudget && !"资产互转".equals(t.category)) {
                total += amountBetween(t, start, end);
            }
        }
        return total;
    }

    /** Amount of one transaction attributable to the half-open range [start, end). */
    public static double amountBetween(Transaction t, long start, long end) {
        if (t == null || end <= start) return 0;
        if (t.spreadStartDate <= 0 || t.spreadEndDate < t.spreadStartDate) {
            return t.date >= start && t.date < end ? t.amount : 0;
        }

        LocalDate spreadStart = toDate(t.spreadStartDate);
        LocalDate spreadEnd = toDate(t.spreadEndDate);
        if (spreadEnd.isBefore(spreadStart)) return 0;

        LocalDate queryStart = toDate(start);
        LocalDate queryEnd = toDate(end - 1);
        LocalDate overlapStart = spreadStart.isAfter(queryStart) ? spreadStart : queryStart;
        LocalDate overlapEnd = spreadEnd.isBefore(queryEnd) ? spreadEnd : queryEnd;
        if (overlapEnd.isBefore(overlapStart)) return 0;

        long days = ChronoUnit.DAYS.between(spreadStart, spreadEnd) + 1;
        long first = ChronoUnit.DAYS.between(spreadStart, overlapStart);
        long count = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
        List<Double> slices = distributeEvenly(t.amount, (int) days);
        double total = 0;
        for (long i = 0; i < count; i++) total += slices.get((int) (first + i));
        return total;
    }

    /** Amount of one transaction attributable to a single local calendar day. */
    public static double amountForDay(Transaction t, LocalDate day) {
        if (t == null || day == null) return 0;
        if (t.spreadStartDate <= 0 || t.spreadEndDate < t.spreadStartDate) {
            LocalDate txDay = toDate(t.date);
            return txDay.equals(day) ? t.amount : 0;
        }
        LocalDate start = toDate(t.spreadStartDate);
        LocalDate end = toDate(t.spreadEndDate);
        if (day.isBefore(start) || day.isAfter(end)) return 0;
        long index = day.toEpochDay() - start.toEpochDay();
        return distributeEvenly(t.amount, (int) (end.toEpochDay() - start.toEpochDay() + 1)).get((int) index);
    }

    public static double dailySurplus(BudgetPlan plan, LocalDate day, List<Transaction> transactions) {
        LocalDate start = toDate(plan.startDate);
        LocalDate end = toDate(plan.endDate);
        if (day.isBefore(start) || day.isAfter(end)) return 0;
        long startMillis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        double dailyBudget = dailyBudget(plan, day);
        return Math.max(0, dailyBudget - expenseBetween(transactions, startMillis, endMillis));
    }

    /**
     * Budget still available for the selected day, calculated before that day's
     * spending and spread across the remaining days in the plan. This prevents
     * spending today from shrinking today's own target after the fact.
     */
    public static double remainingDailyBudget(BudgetPlan plan, LocalDate day,
                                               List<Transaction> transactions) {
        if (plan == null || day == null) return 0;
        LocalDate start = toDate(plan.startDate);
        LocalDate end = toDate(plan.endDate);
        if (day.isBefore(start) || day.isAfter(end)) return 0;

        long startOfDay = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        double spentBeforeDay = expenseBetween(transactions, plan.startDate, startOfDay);
        long remainingDays = end.toEpochDay() - day.toEpochDay() + 1;
        if (remainingDays <= 0) return 0;
        return Math.max(0, plan.totalAmount - spentBeforeDay) / remainingDays;
    }

    /**
     * Returns the exact budget slice for a day in the plan period. Amounts are
     * allocated in cents, so the slices add up to the plan total after normal
     * currency rounding instead of accumulating floating-point division error.
     */
    public static double dailyBudget(BudgetPlan plan, LocalDate day) {
        if (plan == null || day == null) return 0;
        LocalDate start = toDate(plan.startDate);
        LocalDate end = toDate(plan.endDate);
        if (day.isBefore(start) || day.isAfter(end)) return 0;
        long days = end.toEpochDay() - start.toEpochDay() + 1;
        if (days <= 0) return 0;
        long cents = Math.max(0, Math.round(plan.totalAmount * 100));
        long each = cents / days;
        long remainder = cents % days;
        long index = day.toEpochDay() - start.toEpochDay();
        return (each + (index < remainder ? 1 : 0)) / 100.0;
    }

    /** Splits a currency amount into equal cent-accurate parts. */
    public static List<Double> distributeEvenly(double amount, int parts) {
        if (parts <= 0 || amount <= 0) return Collections.emptyList();
        long cents = Math.max(0, Math.round(amount * 100));
        long each = cents / parts;
        long remainder = cents % parts;
        List<Double> result = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            result.add((each + (i < remainder ? 1 : 0)) / 100.0);
        }
        return result;
    }

    public static double remainingAmount(BudgetPlan plan, List<Transaction> transactions) {
        long endExclusive = Instant.ofEpochMilli(plan.endDate).atZone(ZoneId.systemDefault())
                .toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return Math.max(0, plan.totalAmount - expenseBetween(transactions, plan.startDate, endExclusive));
    }

    /** Distributes the amount in cents, assigning any remainder deterministically. */
    public static Map<Integer, Double> distributeEvenly(double amount, List<Goal> goals) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        if (goals == null || goals.isEmpty() || amount <= 0) return result;
        List<Double> parts = distributeEvenly(amount, goals.size());
        for (int i = 0; i < goals.size(); i++) {
            result.put(goals.get(i).id, parts.get(i));
        }
        return result;
    }

    private static LocalDate toDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
