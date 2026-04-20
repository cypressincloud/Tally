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
import com.example.budgetapp.database.Transaction;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OvertimeSummaryWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final PendingResult pendingResult = goAsync();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());

                // 1. 获取本月第一天和最后一天的时间戳
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                long startOfMonth = cal.getTimeInMillis();

                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999);
                long endOfMonth = cal.getTimeInMillis();

                // 2. 查询数据库
                // 查询本月加班总金额
                Double totalSalaryRaw = db.transactionDao().getOvertimeTotalAmountSync(startOfMonth, endOfMonth);
                double totalSalary = (totalSalaryRaw == null) ? 0.0 : totalSalaryRaw;

                // 查询本月所有加班账单列表，用于手动累加时长
                List<Transaction> overtimeList = db.transactionDao().getOvertimeTransactionsSync(startOfMonth, endOfMonth);
                
                double totalHours = 0.0;
                
                if (overtimeList != null && !overtimeList.isEmpty()) {
                    for (Transaction tx : overtimeList) {
                        try {
                            // 假设在弹窗中，时长是通过 et_duration 输入并保存在了 note（或 remark）中
                            // 实际解析取决于你在 SaveOvertime 时是如何封装 note 字符串的
                            // 假设 note 里存的就是单纯的数字字符串 "2.5" 或者 "时长: 2.5"
                            String noteStr = tx.note; 
                            if (noteStr != null && !noteStr.isEmpty()) {
                                // 使用正则提取数字部分
                                String numericPart = noteStr.replaceAll("[^0-9.]", ""); 
                                if (!numericPart.isEmpty()) {
                                    totalHours += Double.parseDouble(numericPart);
                                }
                            }
                        } catch (NumberFormatException e) {
                            Log.e("OvertimeWidget", "Failed to parse hours from note: " + tx.note);
                        }
                    }
                }

                // 3. 格式化输出
                String salaryStr = "¥" + String.format("%.2f", totalSalary);
                // 使用 %.1f 保留一位小数，如果是 ".0" 结尾可以考虑替换掉以保持整洁
                String hoursStr = String.format("%.1f", totalHours).replace(".0", "") + " 小时";

                // 4. 更新UI
                for (int appWidgetId : appWidgetIds) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_overtime_summary);
                    views.setTextViewText(R.id.tv_widget_overtime_salary, salaryStr);
                    views.setTextViewText(R.id.tv_widget_overtime_hours, hoursStr);

                    // 点击跳转
                    Intent intent = new Intent(context, MainActivity.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }
            } catch (Exception e) {
                Log.e("OvertimeWidgetError", "Widget update failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}