package com.example.budgetapp.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.budgetapp.util.RecurringTransactionManager;

/**
 * 周期记账后台任务
 * 每天定时检查并激活到期的周期账单
 */

import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class RecurringTransactionWorker extends Worker {
    private static final String TAG = "RecurringTransactionWorker";

    public RecurringTransactionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d(TAG, "开始检查并激活周期账单");

            // 激活所有已到期的待生效账单
            int activatedCount = RecurringTransactionManager.activatePendingTransactions(getApplicationContext());

            Log.d(TAG, "成功激活 " + activatedCount + " 条周期账单");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "激活周期账单失败", e);
            return Result.retry();
        }
    }
}