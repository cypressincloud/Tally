package com.example.budgetapp.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class WidgetUtils {
    // 一键刷新全家桶（今日、本月、双拼组合组件）
    public static void updateAllWidgets(Context context) {
        try {
            Class<?>[] widgetClasses = {
                    TodaySummaryWidget.class,
                    MonthSummaryWidget.class,
                    CombinedSummaryWidget.class,
                    OvertimeSummaryWidget.class,
                    TodayBudgetWidget.class
            };
            
            for (Class<?> cls : widgetClasses) {
                Intent intent = new Intent(context, cls);
                intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                int[] ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                        new ComponentName(context, cls));
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                context.sendBroadcast(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}