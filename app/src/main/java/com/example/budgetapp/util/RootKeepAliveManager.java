package com.example.budgetapp.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manages the detached root watchdog used for background recovery. */
public final class RootKeepAliveManager {
    private static final String TAG = "RootKeepAlive";
    private static final String PREFS = "root_keep_alive_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_AUTO_ACCESSIBILITY = "auto_accessibility";
    private static final String KEY_AUTO_PERMISSIONS = "auto_permissions";
    private static final String KEY_AUTO_BOOT = "auto_boot";
    private static final String KEY_STARTUP_NOTIFICATION = "startup_notification";
    private static final String KEY_LAST_STATUS = "last_status";
    public static final String STATUS_NOT_RUN = "not_run";
    public static final String STATUS_APPLIED = "applied";
    public static final String STATUS_ROOT_UNAVAILABLE = "root_unavailable";
    public static final String STATUS_PARTIAL_FAILURE = "partial_failure";
    public static final String STATUS_ACCESSIBILITY_REVERTED = "accessibility_reverted";
    private static final String SCRIPT_NAME = "tally_keep_alive.sh";
    private static final String ACCESSIBILITY_SERVICE =
            "com.google.android.accessibility.selecttospeak.SelectToSpeakService";
    private static final String NOTIFICATION_SERVICE =
            "com.example.budgetapp.service.NotificationMonitorService";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "root-keep-alive-manager");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean APPLYING = new AtomicBoolean(false);
    private static final List<Runnable> COMPLETION_CALLBACKS = new ArrayList<>();

    private RootKeepAliveManager() {}

    public static boolean isEnabled(Context context) {
        return prefs(context, KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, enabled).apply();
        if (!enabled) EXECUTOR.execute(() -> stopDaemon(context.getApplicationContext()));
    }

    public static boolean isAutoAccessibilityEnabled(Context context) {
        return prefs(context, KEY_AUTO_ACCESSIBILITY, false);
    }

    public static void setAutoAccessibilityEnabled(Context context, boolean enabled) {
        putBoolean(context, KEY_AUTO_ACCESSIBILITY, enabled);
    }

    public static boolean isAutoPermissionsEnabled(Context context) {
        return prefs(context, KEY_AUTO_PERMISSIONS, false);
    }

    public static void setAutoPermissionsEnabled(Context context, boolean enabled) {
        putBoolean(context, KEY_AUTO_PERMISSIONS, enabled);
    }

    public static boolean isAutoBootEnabled(Context context) {
        return prefs(context, KEY_AUTO_BOOT, true);
    }

    public static void setAutoBootEnabled(Context context, boolean enabled) {
        putBoolean(context, KEY_AUTO_BOOT, enabled);
    }

    public static boolean isStartupNotificationEnabled(Context context) {
        return prefs(context, KEY_STARTUP_NOTIFICATION, true);
    }

    public static void setStartupNotificationEnabled(Context context, boolean enabled) {
        putBoolean(context, KEY_STARTUP_NOTIFICATION, enabled);
    }

    public static String getLastStatus(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_STATUS, STATUS_NOT_RUN);
    }

    public static void applyAsync(Context context) {
        applyAsync(context, null);
    }

    public static void applyAsync(Context context, Runnable completion) {
        if (context == null) return;
        if (completion != null) {
            synchronized (COMPLETION_CALLBACKS) {
                COMPLETION_CALLBACKS.add(completion);
            }
        }
        if (!APPLYING.compareAndSet(false, true)) return;
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                apply(appContext);
            } finally {
                APPLYING.set(false);
                dispatchCompletionCallbacks();
            }
        });
    }

    public static void apply(Context context) {
        if (context == null) return;
        if (!isEnabled(context)) {
            stopDaemon(context);
            return;
        }
        if (!isRootAvailable()) {
            saveStatus(context, STATUS_ROOT_UNAVAILABLE);
            return;
        }
        try {
            stopDaemon(context);
            File workDir = prepareWorkDir(context);
            syncMarkers(context, workDir);
            File script = new File(workDir, SCRIPT_NAME);
            String packageName = context.getPackageName();
            String command = "chmod 700 " + quote(script.getAbsolutePath()) + "; "
                    + "nohup sh " + quote(script.getAbsolutePath()) + " "
                    + quote(packageName) + " "
                    + quote(packageName + "/" + ACCESSIBILITY_SERVICE) + " "
                    + quote(packageName + "/" + NOTIFICATION_SERVICE) + " "
                    + quote(workDir.getAbsolutePath())
                    + " >/dev/null 2>" + quote(new File(workDir, "daemon.log").getAbsolutePath())
                    + " & echo $! >" + quote(new File(workDir, "daemon.pid").getAbsolutePath());
            if (runRoot(command) != 0) {
                saveStatus(context, STATUS_PARTIAL_FAILURE);
                return;
            }
            Thread.sleep(1000);
            if (isAutoAccessibilityEnabled(context) && !isAccessibilityEnabled(context)) {
                saveStatus(context, STATUS_ACCESSIBILITY_REVERTED);
            } else {
                saveStatus(context, STATUS_APPLIED);
            }
        } catch (Exception e) {
            saveStatus(context, STATUS_PARTIAL_FAILURE);
            Log.d(TAG, "Unable to start root watchdog", e);
        }
    }

    public static boolean isRootAvailable() {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }
            return process.waitFor() == 0 && output != null && output.contains("uid=0");
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static File prepareWorkDir(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "root_keep_alive");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create watchdog directory");
        File script = new File(dir, SCRIPT_NAME);
        try (InputStream input = context.getAssets().open(SCRIPT_NAME);
             FileOutputStream output = new FileOutputStream(script, false)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return dir;
    }

    private static void syncMarkers(Context context, File dir) throws IOException {
        setMarker(new File(dir, "enabled"), isEnabled(context));
        setMarker(new File(dir, "auto_accessibility"), isAutoAccessibilityEnabled(context));
        setMarker(new File(dir, "auto_permissions"), isAutoPermissionsEnabled(context));
    }

    private static void setMarker(File file, boolean enabled) throws IOException {
        if (enabled) {
            if (!file.exists() && !file.createNewFile()) throw new IOException("Cannot create marker");
        } else if (file.exists() && !file.delete()) {
            throw new IOException("Cannot remove marker");
        }
    }

    private static void stopDaemon(Context context) {
        File dir = new File(context.getFilesDir(), "root_keep_alive");
        File enabled = new File(dir, "enabled");
        File pid = new File(dir, "daemon.pid");
        File script = new File(dir, SCRIPT_NAME);
        if (!enabled.exists() && !pid.exists() && !script.exists()) return;
        if (enabled.exists()) enabled.delete();
        String scriptPath = script.getAbsolutePath();
        String command = "for daemon_pid in $(pgrep -f " + quote(scriptPath) + "); do "
                + "daemon_cmd=$(tr '\\0' ' ' </proc/$daemon_pid/cmdline 2>/dev/null); "
                + "case \"$daemon_cmd\" in \"sh " + scriptPath + " \"*) kill $daemon_pid;; esac; "
                + "done; sleep 1; rm -f " + quote(pid.getAbsolutePath());
        runRoot(command);
    }

    private static boolean isAccessibilityEnabled(Context context) {
        String services = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return services != null && services.contains(
                context.getPackageName() + "/" + ACCESSIBILITY_SERVICE);
    }

    private static int runRoot(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) { }
            }
            return process.waitFor();
        } catch (Exception e) {
            Log.d(TAG, "Root command failed", e);
            return -1;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static boolean prefs(Context context, String key, boolean defaultValue) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(key, defaultValue);
    }

    private static void putBoolean(Context context, String key, boolean value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(key, value).apply();
    }

    private static void saveStatus(Context context, String status) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_STATUS, status).apply();
    }

    private static void dispatchCompletionCallbacks() {
        List<Runnable> callbacks;
        synchronized (COMPLETION_CALLBACKS) {
            if (COMPLETION_CALLBACKS.isEmpty()) return;
            callbacks = new ArrayList<>(COMPLETION_CALLBACKS);
            COMPLETION_CALLBACKS.clear();
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            for (Runnable callback : callbacks) callback.run();
        });
    }
}
