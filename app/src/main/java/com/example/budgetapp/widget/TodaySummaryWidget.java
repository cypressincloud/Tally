package com.example.budgetapp.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.util.Log;

import com.example.budgetapp.MainActivity;
import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TodaySummaryWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 【关键修复】调用 goAsync() 保持 BroadcastReceiver 的存活，防止子线程被中断
        final PendingResult pendingResult = goAsync();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // 推荐使用 getApplicationContext() 防止内存泄漏
                AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfDay = cal.getTimeInMillis();

                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                long endOfDay = cal.getTimeInMillis();

                Double income = db.transactionDao().getTotalAmountByTypeSync(startOfDay, endOfDay, 1);
                Double expense = db.transactionDao().getTotalAmountByTypeSync(startOfDay, endOfDay, 0);

                double incomeVal = (income == null) ? 0.0 : income;
                double expenseVal = (expense == null) ? 0.0 : expense;
                double balanceVal = incomeVal - expenseVal;

                String incomeStr = "¥" + String.format("%.2f", incomeVal);
                String expenseStr = "¥" + String.format("%.2f", expenseVal);
                String balanceStr = "¥" + String.format("%.2f", balanceVal);

                for (int appWidgetId : appWidgetIds) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_summary);
                    views.setTextViewText(R.id.tv_widget_income, incomeStr);
                    views.setTextViewText(R.id.tv_widget_expense, expenseStr);
                    views.setTextViewText(R.id.tv_widget_balance, balanceStr);

                    Intent intent = new Intent(context, MainActivity.class);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
                    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }
            } catch (Exception e) {
                Log.e("WidgetError", "Widget update failed", e);
            } finally {
                // 【关键修复】任务完成后必须调用 finish()，通知系统释放资源
                pendingResult.finish();
            }
        });
    }
}