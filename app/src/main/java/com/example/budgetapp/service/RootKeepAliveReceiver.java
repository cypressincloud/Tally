package com.example.budgetapp.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.budgetapp.util.RootKeepAliveManager;
import com.example.budgetapp.util.AppStatusNotifier;

/** Re-applies root background policies after a device reboot. */
public class RootKeepAliveReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent != null ? intent.getAction() : null)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent != null ? intent.getAction() : null)) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                    && !RootKeepAliveManager.isAutoBootEnabled(context)) return;
            final BroadcastReceiver.PendingResult result = goAsync();
            final Context appContext = context.getApplicationContext();
            boolean bootCompleted = Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction());
            RootKeepAliveManager.applyAsync(appContext, () -> {
                if (bootCompleted && RootKeepAliveManager.isStartupNotificationEnabled(appContext)
                        && RootKeepAliveManager.STATUS_APPLIED.equals(
                        RootKeepAliveManager.getLastStatus(appContext))) {
                    AppStatusNotifier.notifyRootStarted(appContext);
                }
                result.finish();
            });
        }
    }
}
