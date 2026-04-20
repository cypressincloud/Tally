package com.example.budgetapp.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.content.ContextCompat;
import com.example.budgetapp.MainActivity;
import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.util.CategoryManager;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TodayBudgetWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final PendingResult pendingResult = goAsync();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
                SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                boolean isDetailedEnabled = prefs.getBoolean("is_detailed_budget_enabled", false);

                Calendar cal = Calendar.getInstance();
                int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                int year = cal.get(Calendar.YEAR);
                int month = cal.get(Calendar.MONTH) + 1;

                float monthlyBudget = 0f;
                // 🌟 核心修复：同步应用内的预算汇总逻辑
                // 不再盲目遍历 Prefs，而是只统计当前有效的分类预算
                if (isDetailedEnabled) {
                    List<String> expenseCategories = CategoryManager.getExpenseCategories(context);
                    for (String cat : expenseCategories) {
                        monthlyBudget += prefs.getFloat("budget_cat_" + cat, 0f);
                    }
                } else {
                    float defaultBudget = prefs.getFloat("monthly_budget", 0f);
                    String monthKey = "budget_" + year + "_" + month;
                    monthlyBudget = prefs.getFloat(monthKey, defaultBudget);
                }

                // 🌟 静态计算：今日预算限额 = 总预算 / 月总天数
                double staticDailyLimit = (daysInMonth > 0) ? (monthlyBudget / daysInMonth) : 0;

                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                long startOfToday = cal.getTimeInMillis();

                // 查询今日支出（数据库已自动排除“资产互转”）
                Double todayExpRaw = db.transactionDao().getTotalAmountByTypeSync(startOfToday, System.currentTimeMillis(), 0);
                double tExp = (todayExpRaw == null) ? 0.0 : todayExpRaw;

                // 判断是否超支并计算进度
                boolean isExceeded = tExp > staticDailyLimit;
                int progress = (staticDailyLimit > 0) ? (int) ((tExp / staticDailyLimit) * 100) : 0;
                if (progress > 100) progress = 100;

                // 颜色资源适配
                int colorGreen = ContextCompat.getColor(context, R.color.expense_green);
                int colorRed = ContextCompat.getColor(context, R.color.income_red);
                int themeColor = isExceeded ? colorRed : colorGreen;

                for (int id : appWidgetIds) { // <--- 修正这一行
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_budget);

                    // 中间大字：显示今日固定额度 (例如 40.00)
                    views.setTextViewText(R.id.tv_widget_budget_amount, String.format("¥%.2f", staticDailyLimit));

                    // 右上角小字：仅显示金额数字，颜色随状态变
                    views.setTextViewText(R.id.tv_widget_budget_status, String.format("¥%.2f", tExp));
                    views.setTextColor(R.id.tv_widget_budget_status, themeColor);

                    // 根据状态切换显示对应的进度条
                    if (isExceeded) {
                        views.setViewVisibility(R.id.pb_widget_budget_safe, View.GONE);
                        views.setViewVisibility(R.id.pb_widget_budget_exceed, View.VISIBLE);
                        views.setProgressBar(R.id.pb_widget_budget_exceed, 100, progress, false);
                    } else {
                        views.setViewVisibility(R.id.pb_widget_budget_safe, View.VISIBLE);
                        views.setViewVisibility(R.id.pb_widget_budget_exceed, View.GONE);
                        views.setProgressBar(R.id.pb_widget_budget_safe, 100, progress, false);
                    }

                    Intent intent = new Intent(context, MainActivity.class);
                    PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    views.setOnClickPendingIntent(R.id.widget_root, pi);
                    appWidgetManager.updateAppWidget(id, views);
                }
            } finally {
                pendingResult.finish();
            }
        });
    }
}