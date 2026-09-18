package com.example.budgetapp.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.example.budgetapp.BackupManager;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.AssetAccountDao;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;
import com.example.budgetapp.util.InterestCalculator;

import java.util.Calendar;
import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 活期理财每日计息服务
 * 每天凌晨自动计算活期理财的利息，更新资产余额并生成交易记录。
 */
public class DailyInterestService extends BroadcastReceiver {
    private static final String ACTION_DAILY_INTEREST = "com.example.budgetapp.action.DAILY_INTEREST";
    private static final String PREFS_NAME = "daily_interest_prefs";
    private static final String KEY_LAST_DATE_PREFIX = "last_interest_date_";
    private static final String KEY_ALARM_SCHEDULED = "daily_interest_alarm_scheduled";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_DAILY_INTEREST.equals(intent.getAction())) {
            return;
        }

        AppDatabase database = AppDatabase.getDatabase(context);
        AssetAccountDao assetDao = database.assetAccountDao();
        TransactionDao transactionDao = database.transactionDao();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        List<AssetAccount> currentDeposits = assetDao.getCurrentDepositAssetsSync();
        if (currentDeposits == null || currentDeposits.isEmpty()) {
            return;
        }

        long todayStart = getTodayStartMillis();
        boolean anyUpdated = false;

        for (AssetAccount asset : currentDeposits) {
            if (asset.interestRate <= 0) continue;

            long lastDate = prefs.getLong(KEY_LAST_DATE_PREFIX + asset.id, 0L);
            if (lastDate >= todayStart) {
                continue;
            }

            long daysToCalculate;
            if (lastDate == 0) {
                long depositStart = asset.depositDate > 0 ? asset.depositDate : asset.updateTime;
                daysToCalculate = daysBetween(depositStart, todayStart);
            } else {
                daysToCalculate = daysBetween(lastDate, todayStart);
            }
            if (daysToCalculate <= 0) continue;

            double totalInterest = 0;

            if (asset.isCompoundInterest) {
                totalInterest = InterestCalculator.interest(
                        asset.amount, asset.interestRate, daysToCalculate, true);
            } else {
                Double accruedInterestRaw = transactionDao.getInvestmentInterestTotalSync(asset.id);
                double accruedInterest = accruedInterestRaw == null ? 0 : accruedInterestRaw;
                double principal = accruedInterest > 0 && accruedInterest < asset.amount
                        ? asset.amount - accruedInterest : asset.amount;
                totalInterest = InterestCalculator.interest(
                        principal, asset.interestRate, daysToCalculate, false);
            }

            double roundedInterest = Math.round(totalInterest * 100.0) / 100.0;
            if (roundedInterest <= 0) continue;
            asset.amount = Math.round((asset.amount + roundedInterest) * 100.0) / 100.0;
            assetDao.update(asset);

            Transaction transaction = new Transaction(
                    System.currentTimeMillis(),
                    1,
                    "理财收益",
                    roundedInterest,
                    asset.name + " 活期利息"
            );
            transaction.assetId = asset.id;
            transaction.excludeFromBudget = true;
            transaction.subCategory = "";
            transactionDao.insert(transaction);

            prefs.edit().putLong(KEY_LAST_DATE_PREFIX + asset.id, todayStart).apply();
            anyUpdated = true;
        }

        if (anyUpdated) {
            BackupManager.triggerAutoUploadIfEnabled(context);
        }
    }

    private long getTodayStartMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long daysBetween(long startMillis, long endMillis) {
        LocalDate start = Instant.ofEpochMilli(startMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * 安排每日计息闹钟（凌晨 1 点执行）
     */
    public static void scheduleAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, DailyInterestService.class);
        intent.setAction(ACTION_DAILY_INTEREST);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ALARM_SCHEDULED, true)
                .apply();
    }

    /**
     * 检查计息闹钟是否已安排
     */
    public static boolean isAlarmScheduled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ALARM_SCHEDULED, false);
    }
}
