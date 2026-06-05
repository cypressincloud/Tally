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

import java.util.Calendar;
import java.util.List;

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
                if (daysToCalculate <= 0) daysToCalculate = 1;
            } else {
                daysToCalculate = daysBetween(lastDate, todayStart);
                if (daysToCalculate <= 0) daysToCalculate = 1;
            }

            double dailyRate = asset.interestRate / 100.0 / 365.0;
            double totalInterest = 0;
            double currentPrincipal = asset.amount;

            for (long day = 0; day < daysToCalculate; day++) {
                double dayInterest = currentPrincipal * dailyRate;
                totalInterest += dayInterest;
                currentPrincipal += dayInterest;
            }

            asset.amount = Math.round(currentPrincipal * 100.0) / 100.0;
            assetDao.update(asset);

            Transaction transaction = new Transaction(
                    System.currentTimeMillis(),
                    1,
                    "理财收益",
                    Math.round(totalInterest * 100.0) / 100.0,
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
        long startDay = startMillis / (24 * 60 * 60 * 1000L);
        long endDay = endMillis / (24 * 60 * 60 * 1000L);
        return Math.max(1, endDay - startDay);
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
