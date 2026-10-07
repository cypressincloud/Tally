package com.example.budgetapp.util;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import rikka.shizuku.Shizuku;

public class ShizukuManager {

    private static final String ACCESSIBILITY_SERVICE_COMPONENT =
            "com.example.budgetapp/com.google.android.accessibility.selecttospeak.SelectToSpeakService";

    // 记录 Binder 是否已连接
    private static boolean isBinderReady = false;

    public static void setBinderReady(boolean ready) {
        isBinderReady = ready;
    }

    /**
     * 判断当前系统是否运行了 Shizuku 服务
     */
    public static boolean isShizukuAvailable() {
        if (isBinderReady) return true;
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 判断当前 App 是否获得了 Shizuku 的权限
     */
    public static boolean hasShizukuPermission() {
        if (!isShizukuAvailable()) {
            return false;
        }
        try {
            if (Shizuku.isPreV11()) {
                return false;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        if (isShizukuAvailable() && !hasShizukuPermission()) {
            Shizuku.requestPermission(requestCode);
        }
    }

    public static String execCommand(String command) {
        try {
            Method newProcessMethod = Shizuku.class.getDeclaredMethod(
                    "newProcess",
                    String[].class,
                    String[].class,
                    String.class
            );
            newProcessMethod.setAccessible(true);

            Process process = (Process) newProcessMethod.invoke(
                    null,
                    new Object[]{new String[]{"sh", "-c", command}, null, null}
            );

            if (process == null) return null;

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean isAccessibilityRunning(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (enabledServices != null) {
            for (AccessibilityServiceInfo service : enabledServices) {
                if (service.getId().contains("SelectToSpeakService") ||
                        service.getId().contains("com.example.budgetapp")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean enableAccessibilityService(Context context) {
        if (!hasShizukuPermission()) return false;

        String currentServices = execCommand("settings get secure enabled_accessibility_services");
        Set<String> serviceSet = new LinkedHashSet<>();
        if (currentServices != null && !currentServices.isEmpty() && !"null".equalsIgnoreCase(currentServices)) {
            String[] split = currentServices.split(":");
            serviceSet.addAll(Arrays.asList(split));
        }

        serviceSet.add(ACCESSIBILITY_SERVICE_COMPONENT);
        String updatedServices = String.join(":", serviceSet);

        execCommand("settings put secure enabled_accessibility_services " + updatedServices);
        execCommand("settings put secure accessibility_enabled 1");

        return true;
    }

    public static void grantBackgroundKeepAlive(Context context) {
        if (!hasShizukuPermission()) return;

        String pkgName = context.getPackageName();
        execCommand("dumpsys deviceidle whitelist +" + pkgName);
        execCommand("cmd appops set " + pkgName + " RUN_IN_BACKGROUND allow");
        execCommand("cmd appops set " + pkgName + " RUN_ANY_IN_BACKGROUND allow");
        execCommand("pm grant " + pkgName + " android.permission.SYSTEM_ALERT_WINDOW");
    }
}