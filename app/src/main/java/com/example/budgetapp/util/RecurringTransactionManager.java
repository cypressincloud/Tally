package com.example.budgetapp.util;

import android.content.Context;

import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.RecurringRule;
import com.example.budgetapp.database.Transaction;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 周期记账管理器
 * 负责预生成周期账单和激活到期账单
 */
public class RecurringTransactionManager {

    /**
     * 为指定规则生成时间范围内的所有周期账单
     * @param context 上下文
     * @param rule 周期规则
     * @param viewStartDate 视窗开始日期
     * @param viewEndDate 视窗结束日期
     */
    public static void generateRecurringTransactions(Context context, RecurringRule rule, long viewStartDate, long viewEndDate) {
        AppDatabase db = AppDatabase.getDatabase(context);
        
        // 检查规则是否启用
        if (!rule.isEnabled) {
            return;
        }

        // 获取已存在的账单,避免重复生成
        List<Transaction> existingTransactions = db.transactionDao().getUnsettledTransactionsByRuleSync(rule.id);
        
        // 生成所有需要的周期日期
        List<Long> scheduleDates = calculateScheduleDates(rule, viewStartDate, viewEndDate);
        
        for (long scheduleDate : scheduleDates) {
            // 检查是否已存在
            boolean exists = false;
            for (Transaction t : existingTransactions) {
                if (isSameDay(t.date, scheduleDate)) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                // 创建新的预生成账单
                Transaction transaction = new Transaction();
                transaction.date = scheduleDate;
                transaction.type = rule.type;
                transaction.category = rule.category;
                transaction.subCategory = rule.subCategory != null ? rule.subCategory : "";
                transaction.amount = rule.amount;
                transaction.currencySymbol = rule.currencySymbol;
                transaction.assetId = rule.assetId;
                
                // 计算生效时间
                transaction.scheduledExecuteTime = calculateExecuteTime(scheduleDate, rule.triggerTime);
                
                // 【修改】备注：如果规则备注为空,自动生成"生效时间 MM-dd HH:mm"格式
                if (rule.note == null || rule.note.trim().isEmpty()) {
                    SimpleDateFormat sdf = new SimpleDateFormat("生效时间 MM-dd HH:mm", Locale.CHINA);
                    transaction.note = sdf.format(new Date(transaction.scheduledExecuteTime));
                } else {
                    transaction.note = rule.note;
                }
                
                transaction.remark = "";
                transaction.isSettled = false; // 未生效
                transaction.recurringRuleId = rule.id;
                
                db.transactionDao().insert(transaction);
            }
        }
    }

    /**
     * 计算指定规则在时间范围内的所有发生日期
     */
    private static List<Long> calculateScheduleDates(RecurringRule rule, long viewStartDate, long viewEndDate) {
        List<Long> dates = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(rule.startDate);
        clearTime(calendar);
        
        long ruleEndDate = rule.endDate > 0 ? rule.endDate : Long.MAX_VALUE;
        
        while (calendar.getTimeInMillis() <= Math.min(viewEndDate, ruleEndDate)) {
            long currentDate = calendar.getTimeInMillis();
            
            // 只添加在视窗范围内的日期
            if (currentDate >= viewStartDate && currentDate <= viewEndDate) {
                dates.add(currentDate);
            }
            
            // 前进到下一个周期
            advanceToNextPeriod(calendar, rule.periodUnit, rule.periodInterval);
        }
        
        return dates;
    }

    /**
     * 根据周期单位前进到下一个周期
     */
    private static void advanceToNextPeriod(Calendar calendar, String periodUnit, int interval) {
        int originalDay = calendar.get(Calendar.DAY_OF_MONTH);
        
        switch (periodUnit) {
            case "DAY":
                calendar.add(Calendar.DAY_OF_MONTH, interval);
                break;
            case "WEEK":
                calendar.add(Calendar.WEEK_OF_YEAR, interval);
                break;
            case "MONTH":
                calendar.add(Calendar.MONTH, interval);
                // 处理跨月时不存在的日期（如31日在小月）
                int maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
                if (originalDay > maxDay) {
                    calendar.set(Calendar.DAY_OF_MONTH, maxDay);
                }
                break;
            case "YEAR":
                calendar.add(Calendar.YEAR, interval);
                // 处理闰年2月29日的情况
                if (originalDay == 29 && calendar.get(Calendar.MONTH) == Calendar.FEBRUARY) {
                    int yearMaxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
                    if (yearMaxDay < 29) {
                        calendar.set(Calendar.DAY_OF_MONTH, yearMaxDay);
                    }
                }
                break;
        }
    }

    /**
     * 计算账单的生效时间戳
     */
    private static long calculateExecuteTime(long dateMillis, String triggerTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMillis);
        
        // 解析触发时间
        String[] parts = triggerTime.split(":");
        int hour = 0;
        int minute = 0;
        
        if (parts.length >= 2) {
            try {
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                // 使用默认值 00:00
            }
        }
        
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        
        return calendar.getTimeInMillis();
    }

    /**
     * 激活所有已到期的待生效账单
     */
    public static int activatePendingTransactions(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        long currentTime = System.currentTimeMillis();
        
        List<Transaction> pendingTransactions = db.transactionDao().getPendingActivationTransactionsSync(currentTime);
        int activatedCount = 0;
        
        for (Transaction transaction : pendingTransactions) {
            // 标记为已生效
            transaction.isSettled = true;
            
            // 【修改】删除备注中的"生效时间"四个字,保留时间格式 "MM-dd HH:mm"
            if (transaction.note != null && transaction.note.startsWith("生效时间 ")) {
                // 删除前缀"生效时间 ",保留后面的时间
                transaction.note = transaction.note.substring("生效时间 ".length());
            }
            
            db.transactionDao().update(transaction);
            
            // 更新资产账户余额
            updateAssetBalance(context, transaction);
            
            activatedCount++;
        }
        
        return activatedCount;
    }

    /**
     * 更新资产账户余额
     */
    private static void updateAssetBalance(Context context, Transaction transaction) {
        AppDatabase db = AppDatabase.getDatabase(context);
        
        if (transaction.assetId > 0) {
            AssetAccount asset = db.assetAccountDao().getAssetByIdSync(transaction.assetId);
            if (asset != null) {
                if (transaction.type == 0) {
                    // 支出：扣除余额
                    asset.amount -= transaction.amount;
                } else if (transaction.type == 1) {
                    // 收入：增加余额
                    asset.amount += transaction.amount;
                }
                asset.updateTime = System.currentTimeMillis();
                db.assetAccountDao().update(asset);
            }
        }
    }

    /**
     * 清空时间部分,只保留日期
     */
    private static void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    /**
     * 判断两个时间戳是否在同一天
     */
    private static boolean isSameDay(long time1, long time2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTimeInMillis(time1);
        cal2.setTimeInMillis(time2);
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * 批量为所有启用的规则生成指定月份的周期账单
     */
    public static void generateMonthlyRecurringTransactions(Context context, int year, int month) {
        AppDatabase db = AppDatabase.getDatabase(context);
        List<RecurringRule> rules = db.recurringRuleDao().getEnabledRulesSync();
        
        // 计算月份的开始和结束时间
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, 1, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startDate = calendar.getTimeInMillis();
        
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        long endDate = calendar.getTimeInMillis();
        
        // 为每个规则生成账单
        for (RecurringRule rule : rules) {
            generateRecurringTransactions(context, rule, startDate, endDate);
        }
    }
}
