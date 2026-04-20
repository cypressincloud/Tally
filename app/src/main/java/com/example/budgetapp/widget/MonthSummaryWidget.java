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

public class MonthSummaryWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final PendingResult pendingResult = goAsync();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());

                // 获取本月第一天和最后一天的时间戳
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfMonth = cal.getTimeInMillis();

                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                long endOfMonth = cal.getTimeInMillis();

                Double income = db.transactionDao().getTotalAmountByTypeSync(startOfMonth, endOfMonth, 1);
                Double expense = db.transactionDao().getTotalAmountByTypeSync(startOfMonth, endOfMonth, 0);

                double incomeVal = (income == null) ? 0.0 : income;
                double expenseVal = (expense == null) ? 0.0 : expense;
                double balanceVal = incomeVal - expenseVal;

                String incomeStr = "¥" + String.format("%.2f", incomeVal);
                String expenseStr = "¥" + String.format("%.2f", expenseVal);
                String balanceStr = "¥" + String.format("%.2f", balanceVal);

                for (int appWidgetId : appWidgetIds) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_month_summary);
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
                Log.e("MonthWidgetError", "Widget update failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}