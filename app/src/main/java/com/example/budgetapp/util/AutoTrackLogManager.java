package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class AutoTrackLogManager {
    public static class LogEntry {
        public String time;
        public String packageName;
        public String message;
        
        public LogEntry(String packageName, String message) {
            this.time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
            this.packageName = packageName;
            this.message = message;
        }
        
        public LogEntry(String time, String packageName, String message) {
            this.time = time;
            this.packageName = packageName;
            this.message = message;
        }
    }

    private static final int MAX_LOGS = 2000;
    private static final List<LogEntry> logs = new ArrayList<>();
    private static final LinkedHashSet<String> packages = new LinkedHashSet<>();
    private static Runnable observer;
    private static boolean isLoaded = false;

    public static boolean isLogEnabled(Context context) {
        if (context == null) return false;
        return context.getSharedPreferences("TallySettings", Context.MODE_PRIVATE)
                .getBoolean("enable_auto_track_log", false);
    }

    public static void setLogEnabled(Context context, boolean enabled) {
        if (context == null) return;
        context.getSharedPreferences("TallySettings", Context.MODE_PRIVATE)
                .edit().putBoolean("enable_auto_track_log", enabled).apply();
    }

    // 新增：确保从本地读取过一次历史日志
    public static synchronized void loadLogsIfNeeded(Context context) {
        if (isLoaded || context == null) return;
        isLoaded = true;
        
        SharedPreferences prefs = context.getSharedPreferences("TallySettings", Context.MODE_PRIVATE);
        String savedJson = prefs.getString("saved_auto_track_logs", null);
        if (savedJson != null && !savedJson.isEmpty()) {
            try {
                JSONArray array = new JSONArray(savedJson);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    logs.add(new LogEntry(obj.optString("t", ""), obj.optString("p", ""), obj.optString("m", "")));
                    packages.add(obj.optString("p", ""));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 新增：将当前内存中的日志整体写入本地存储
    public static synchronized void saveLogsToDisk(Context context) {
        if (context == null) return;
        try {
            JSONArray array = new JSONArray();
            // 为了防止保存过慢，最多只保存最近的 1500 条
            int start = Math.max(0, logs.size() - 1500);
            for (int i = start; i < logs.size(); i++) {
                LogEntry e = logs.get(i);
                JSONObject obj = new JSONObject();
                obj.put("t", e.time);
                obj.put("p", e.packageName);
                obj.put("m", e.message);
                array.put(obj);
            }
            context.getSharedPreferences("TallySettings", Context.MODE_PRIVATE)
                    .edit().putString("saved_auto_track_logs", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setObserver(Runnable obs) {
        observer = obs;
    }

    public static synchronized void addLog(String packageName, String message) {
        packages.add(packageName);
        logs.add(new LogEntry(packageName, message));
        if (logs.size() > MAX_LOGS) {
            logs.remove(0);
        }
        if (observer != null) {
            observer.run();
        }
    }

    // 注意：下面的方法都加了 context 参数，用于懒加载本地历史日志
    public static synchronized List<LogEntry> getLogs(Context context, String filterPackage) {
        loadLogsIfNeeded(context);
        if (filterPackage == null || filterPackage.equals("全部") || filterPackage.equals("全部应用")) {
            return new ArrayList<>(logs);
        }
        List<LogEntry> filtered = new ArrayList<>();
        for (LogEntry log : logs) {
            if (filterPackage.equals(log.packageName)) {
                filtered.add(log);
            }
        }
        return filtered;
    }

    public static synchronized List<String> getPackages(Context context) {
        loadLogsIfNeeded(context);
        List<String> list = new ArrayList<>();
        list.add("全部");
        list.addAll(packages);
        return list;
    }
    
    public static synchronized void clearLogs(Context context) {
        logs.clear();
        packages.clear();
        if (context != null) {
            context.getSharedPreferences("TallySettings", Context.MODE_PRIVATE)
                    .edit().remove("saved_auto_track_logs").apply();
        }
        if (observer != null) observer.run();
    }
}