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

public class CombinedSummaryWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final PendingResult pendingResult = goAsync();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());

                // 1. 获取今日时间范围
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                long startToday = cal.getTimeInMillis();
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999);
                long endToday = cal.getTimeInMillis();

                // 2. 获取本月时间范围
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                long startMonth = cal.getTimeInMillis();
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999);
                long endMonth = cal.getTimeInMillis();

                // 3. 查询数据
                Double tInc = db.transactionDao().getTotalAmountByTypeSync(startToday, endToday, 1);
                Double tExp = db.transactionDao().getTotalAmountByTypeSync(startToday, endToday, 0);
                Double mInc = db.transactionDao().getTotalAmountByTypeSync(startMonth, endMonth, 1);
                Double mExp = db.transactionDao().getTotalAmountByTypeSync(startMonth, endMonth, 0);

                // 4. 更新所有小组件
                for (int appWidgetId : appWidgetIds) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_combined_summary);
                    
                    // 设置文字
                    views.setTextViewText(R.id.tv_widget_today_income, format(tInc));
                    views.setTextViewText(R.id.tv_widget_today_expense, format(tExp));
                    views.setTextViewText(R.id.tv_widget_today_balance, format(nvl(tInc) - nvl(tExp)));
                    
                    views.setTextViewText(R.id.tv_widget_month_income, format(mInc));
                    views.setTextViewText(R.id.tv_widget_month_expense, format(mExp));
                    views.setTextViewText(R.id.tv_widget_month_balance, format(nvl(mInc) - nvl(mExp)));

                    // 点击跳转
                    Intent intent = new Intent(context, MainActivity.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }
            } catch (Exception e) {
                Log.e("CombinedWidget", "Update failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private String format(Double val) {
        return "¥" + String.format("%.2f", nvl(val));
    }

    private double nvl(Double val) {
        return val == null ? 0.0 : val;
    }
}