package com.example.budgetapp.util;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

import com.example.budgetapp.MainActivity;
import com.example.budgetapp.R;

import java.util.Locale;

/** Posts optional status notifications for background-only actions. */
public final class AppStatusNotifier {
    private static final String CHANNEL_ACCOUNTING = "automatic_accounting";
    private static final String CHANNEL_ROOT = "root_keep_alive";
    private static final int ROOT_NOTIFICATION_ID = 4100;

    private AppStatusNotifier() {}

    public static void notifyDirectAccounting(Context context, String appName, int type,
                                               double amount, String assetName) {
        if (!canNotify(context)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        createChannel(manager, CHANNEL_ACCOUNTING, "自动记账", NotificationManager.IMPORTANCE_HIGH);
        String direction = type == 1 ? "收入" : "支出";
        String content = direction + " ¥" + String.format(Locale.ROOT, "%.2f", amount);
        if (assetName != null && !assetName.isEmpty()) content += " · " + assetName;
        Notification notification = new Notification.Builder(context, CHANNEL_ACCOUNTING)
                .setSmallIcon(R.drawable.ic_qs_record)
                .setColor(Color.rgb(52, 120, 246))
                .setContentTitle((appName == null || appName.isEmpty() ? "通知" : appName) + " 已自动记账")
                .setContentText(content)
                .setContentIntent(mainPendingIntent(context))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
        manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), notification);
    }

    public static void notifyRootStarted(Context context) {
        if (!canNotify(context)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        createChannel(manager, CHANNEL_ROOT, "Root 保活", NotificationManager.IMPORTANCE_DEFAULT);
        Notification notification = new Notification.Builder(context, CHANNEL_ROOT)
                .setSmallIcon(R.drawable.ic_qs_record)
                .setColor(Color.rgb(52, 120, 246))
                .setContentTitle("Root 保活已启动")
                .setContentText("后台守护与自动恢复策略正在运行")
                .setContentIntent(mainPendingIntent(context))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
        manager.notify(ROOT_NOTIFICATION_ID, notification);
    }

    private static void createChannel(NotificationManager manager, String id, String name, int importance) {
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        manager.createNotificationChannel(channel);
    }

    private static PendingIntent mainPendingIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
