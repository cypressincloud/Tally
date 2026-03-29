package com.example.budgetapp.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class AutoTrackLogManager {
    public static boolean isLogEnabled = false; // 抓取开关，防止后台持续耗电

    public static class LogEntry {
        public String time;
        public String packageName;
        public String message;
        public LogEntry(String packageName, String message) {
            this.time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
            this.packageName = packageName;
            this.message = message;
        }
    }

    private static final int MAX_LOGS = 2000; // 最多保留2000条，防止内存溢出
    private static final List<LogEntry> logs = new ArrayList<>();
    private static final LinkedHashSet<String> packages = new LinkedHashSet<>();
    private static Runnable observer;

    public static void setObserver(Runnable obs) {
        observer = obs;
    }

    public static synchronized void addLog(String packageName, String message) {
        if (!isLogEnabled) return;
        packages.add(packageName);
        logs.add(new LogEntry(packageName, message));
        if (logs.size() > MAX_LOGS) {
            logs.remove(0);
        }
        if (observer != null) {
            observer.run();
        }
    }

    public static synchronized List<LogEntry> getLogs(String filterPackage) {
        if (filterPackage == null || filterPackage.equals("全部")) {
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

    public static synchronized List<String> getPackages() {
        List<String> list = new ArrayList<>();
        list.add("全部");
        list.addAll(packages);
        return list;
    }
    
    public static synchronized void clearLogs() {
        logs.clear();
        packages.clear();
        if (observer != null) observer.run();
    }
}