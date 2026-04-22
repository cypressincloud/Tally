package com.example.budgetapp;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.example.budgetapp.ui.AuthActivity;

public class MyApplication extends Application {

    // 全局静态变量，记录当前是否已经解锁过
    public static boolean isUnlocked = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. 监听系统锁屏广播（一旦屏幕熄灭，就将状态改为未解锁）
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    isUnlocked = false;
                }
            }
        }, filter);

        // 2. 全局监听所有页面的生命周期，自动拦截未解锁状态
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                // 如果当前进入的页面不是验证页本身，检查是否需要拦截
                if (!(activity instanceof AuthActivity)) {
                    SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                    String pwd = prefs.getString("app_password", "");
                    
                    // 如果用户设置了密码，且当前状态是“未解锁”
                    if (!pwd.isEmpty() && !isUnlocked) {
                        Intent intent = new Intent(activity, AuthActivity.class);
                        // 去除跳转动画，让遮挡更自然
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION); 
                        activity.startActivity(intent);
                    }
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {}
            @Override
            public void onActivityStopped(Activity activity) {}
            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }
}