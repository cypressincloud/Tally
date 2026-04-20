package com.example.budgetapp;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.widget.TodaySummaryWidget;
import com.google.android.accessibility.selecttospeak.SelectToSpeakService;
import com.example.budgetapp.ui.SettingsActivity;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.viewmodel.FinanceViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

import androidx.activity.OnBackPressedCallback;

public class MainActivity extends AppCompatActivity {

    private FinanceViewModel financeViewModel;
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<AssetAccount> allAssets = new ArrayList<>();


    // 导出功能保留在此处作为备份逻辑
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            uri -> {
                if (uri != null) {
                    try {
                        BackupManager.exportToZip(this, uri, allTransactions, allAssets);
                        Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    // 导入功能保留在此处作为备份逻辑
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        BackupData data = BackupManager.importFromZip(this, uri);
                        
                        int recordCount = 0;
                        int assetCount = 0;

                        if (data.records != null && !data.records.isEmpty()) {
                            for (Transaction t : data.records) {
                                Transaction newT = t; 
                                newT.id = 0; 
                                financeViewModel.addTransaction(newT);
                            }
                            recordCount = data.records.size();
                        }

                        if (data.assets != null && !data.assets.isEmpty()) {
                            for (AssetAccount a : data.assets) {
                                AssetAccount newA = a;
                                newA.id = 0; 
                                financeViewModel.addAsset(newA);
                            }
                            assetCount = data.assets.size();
                        }

                        if (recordCount > 0 || assetCount > 0) {
                            Toast.makeText(this, 
                                String.format("成功导入: %d条账单, %d个资产", recordCount, assetCount), 
                                Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "备份文件中未发现数据", Toast.LENGTH_SHORT).show();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 【修改】适配双背景组合逻辑
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        // 【修复】使用安全的读取方法，防止老版本导入导致 ClassCastException
        int themeMode = getSafeInt(prefs, "theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int delegateMode = themeMode;
        if (themeMode == 3) {
            String dayUri = prefs.getString("custom_bg_day_uri", null);
            String nightUri = prefs.getString("custom_bg_night_uri", null);
            if (dayUri != null && nightUri == null) {
                delegateMode = AppCompatDelegate.MODE_NIGHT_NO; // 只有日间图片，全局锁死日间模式
            } else if (nightUri != null && dayUri == null) {
                delegateMode = AppCompatDelegate.MODE_NIGHT_YES; // 只有夜间图片，全局锁死夜间模式
            } else {
                delegateMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; // 都有，跟随系统自动切换
            }
        }
        AppCompatDelegate.setDefaultNightMode(delegateMode);

        super.onCreate(savedInstanceState);

        // 【关键新增】允许内容延伸到状态栏和导航栏区域
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // 【关键新增】：强制状态栏颜色为透明，确保自定义图片能完美沉浸到状态栏区域
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_main);

        financeViewModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        
        financeViewModel.getAllTransactions().observe(this, transactions -> {
            this.allTransactions = transactions;
        });

        financeViewModel.getAllAssets().observe(this, assets -> {
            this.allAssets = assets;
        });

        LinearLayout rootLayout = findViewById(R.id.root_layout);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        // 初始化时应用背景
        applyCustomBackground();

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        AssistantConfig config = new AssistantConfig(this);
        boolean showAssets = config.isAssetsEnabled();
        boolean showDetails = config.isDetailsEnabled(); // 新增

        bottomNav.getMenu().findItem(R.id.nav_assets).setVisible(showAssets);
        bottomNav.getMenu().findItem(R.id.nav_details).setVisible(showDetails); // 新增

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(bottomNav, navController);

            bottomNav.setOnItemSelectedListener(item -> {
                // 添加触摸振动反馈
                bottomNav.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);

                if (item.getItemId() != navController.getCurrentDestination().getId()) {
                    navController.navigate(item.getItemId());
                    return true;
                }
                return false;
            });

            bottomNav.post(() -> {
                // 1. 记账: 屏蔽默认长按提示
                View recordTab = bottomNav.findViewById(R.id.nav_record);
                if (recordTab != null) {
                    recordTab.setOnLongClickListener(v -> true);
                }

                // 2. 统计: 极简模式跳转设置，否则仅屏蔽默认提示
                View statsTab = bottomNav.findViewById(R.id.nav_stats);
                if (statsTab != null) {
                    statsTab.setOnLongClickListener(v -> {
                        boolean isMinimalist = getSafeBoolean(prefs, "minimalist_mode", false);
                        
                        if (isMinimalist) {
                            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                            startActivity(intent);
                        }
                        return true; 
                    });
                }
                
                // 3. 资产: 屏蔽默认长按提示
                View assetsTab = bottomNav.findViewById(R.id.nav_assets);
                if (assetsTab != null) {
                    assetsTab.setOnLongClickListener(v -> true);
                }

                // 4. 明细: 屏蔽默认长按提示
                View detailsTab = bottomNav.findViewById(R.id.nav_details);
                if (detailsTab != null) {
                    detailsTab.setOnLongClickListener(v -> true);
                }
            });

            // 【新增】拦截返回手势逻辑
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (navController.getCurrentDestination() != null) {
                        int currentId = navController.getCurrentDestination().getId();
                        // 新增 R.id.nav_details
                        if (currentId == R.id.nav_record || currentId == R.id.nav_assets ||
                                currentId == R.id.nav_stats || currentId == R.id.nav_details) {
                            finish();
                        } else {
                            if (!navController.popBackStack()) {
                                finish();
                            }
                        }
                    } else {
                        finish();
                    }
                }
            });
        }

        checkPermissions();
    }

    // 【修改】应用自定义背景（完美保留原有透明度适配逻辑，仅新增日/夜双图片判断）
    private void applyCustomBackground() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int themeMode = getSafeInt(prefs, "theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        View rootLayout = findViewById(R.id.root_layout);
        View navHostFragment = findViewById(R.id.nav_host_fragment); // 获取碎片容器

        // 🌟 获取底栏
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        if (rootLayout == null) return;

        if (themeMode == 3) { // 3 代表开启了自定义背景
            // 🌟 【修改部分开始】：智能获取应该加载日间还是夜间的图片
            String dayUriStr = prefs.getString("custom_bg_day_uri", null);
            String nightUriStr = prefs.getString("custom_bg_night_uri", null);
            String targetUriStr = null;

            if (dayUriStr != null && nightUriStr == null) {
                targetUriStr = dayUriStr; // 只有日间
            } else if (nightUriStr != null && dayUriStr == null) {
                targetUriStr = nightUriStr; // 只有夜间
            } else if (dayUriStr != null && nightUriStr != null) {
                // 两个都有，判断当前系统是否为暗黑模式
                int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    targetUriStr = nightUriStr;
                } else {
                    targetUriStr = dayUriStr;
                }
            }
            // 🌟 【修改部分结束】

            if (targetUriStr != null) {
                try {
                    android.net.Uri uri = android.net.Uri.parse(targetUriStr);
                    java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                    android.graphics.drawable.Drawable drawable =
                            android.graphics.drawable.Drawable.createFromStream(inputStream, uri.toString());

                    // 设置为根布局背景
                    rootLayout.setBackground(drawable);

                    // 【动态透明】：保留你原本的逻辑，把顶层容器的透明度调成 100% (完全透明)
                    if (navHostFragment != null) {
                        navHostFragment.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    }

                    // 🌟 【保留原有】：底栏设置透明度 (Alpha: 230)
                    if (bottomNav != null && bottomNav.getBackground() != null) {
                        bottomNav.getBackground().mutate().setAlpha(230);
                    }

                    if (inputStream != null) inputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    rootLayout.setBackgroundResource(R.color.bar_background);
                    if (navHostFragment != null) navHostFragment.setBackgroundResource(R.color.white);
                    // 异常时恢复底栏透明度
                    if (bottomNav != null && bottomNav.getBackground() != null) bottomNav.getBackground().mutate().setAlpha(255);
                }
            } else {
                // 如果开启了自定义背景，但用户把两张图都"清除"了，则恢复系统默认
                rootLayout.setBackgroundResource(R.color.bar_background);
                if (navHostFragment != null) navHostFragment.setBackgroundResource(R.color.white);
                if (bottomNav != null && bottomNav.getBackground() != null) bottomNav.getBackground().mutate().setAlpha(255);
            }
        } else {
            // 如果不是自定义背景模式，恢复系统默认颜色
            rootLayout.setBackgroundResource(R.color.bar_background);
            if (navHostFragment != null) {
                navHostFragment.setBackgroundResource(R.color.white);
            }

            // 🌟 【保留原有】：恢复底栏 100% 不透明度
            if (bottomNav != null && bottomNav.getBackground() != null) {
                bottomNav.getBackground().mutate().setAlpha(255);
            }
        }
    }

    private void checkPermissions() {
        AssistantConfig config = new AssistantConfig(this);

        if (config.isEnabled() && !isAccessibilitySettingsOn()) {
            showPermissionDialog("开启屏幕同步助手",
                    "为了提取屏幕上的支付金额，请开启‘记账屏幕同步助手’。",
                    Settings.ACTION_ACCESSIBILITY_SETTINGS);
        }
//        else if (config.isRefundEnabled() && !isNotificationListenerEnabled()) {
//            showPermissionDialog("开启退款监听",
//                    "为了监听微信/支付宝的退款通知，请授予‘通知使用权’。",
//                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
//        }
        else if (config.isEnabled() && !Settings.canDrawOverlays(this)) {
            showPermissionDialog("开启悬浮窗权限",
                    "为了在记账时显示确认弹窗，请授予‘显示在其他应用上层’权限。",
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        }
    }

    private void showPermissionDialog(String title, String message, String action) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_permission_request, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            // 背景透明以显示圆角
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 绑定数据
        TextView tvTitle = view.findViewById(R.id.tv_permission_title);
        TextView tvMessage = view.findViewById(R.id.tv_permission_message);
        tvTitle.setText(title);
        tvMessage.setText(message);

        // 取消按钮
        view.findViewById(R.id.btn_cancel_permission).setOnClickListener(v -> dialog.dismiss());

        // 去授权按钮
        view.findViewById(R.id.btn_grant_permission).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(action);
                if (Settings.ACTION_MANAGE_OVERLAY_PERMISSION.equals(action)) {
                    intent.setData(Uri.parse("package:" + getPackageName()));
                }
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开设置页，请手动前往设置", Toast.LENGTH_LONG).show();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private boolean isAccessibilitySettingsOn() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + SelectToSpeakService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) { }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                return settingValue.contains(service);
            }
        }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // 已移除旧的 toggleNightMode 方法，因为现在由 SettingsActivity 统一管理

    private void showBackupOptions() {
        String[] options = {"导出数据", "导入数据"};
        new AlertDialog.Builder(this)
                .setTitle("数据备份与恢复")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        String timeStr = sdf.format(new Date()).replace(":", "-"); 
                        String fileName = "Tally " + timeStr + ".zip";
                        exportLauncher.launch(fileName);
                    }
                    else {
                        importLauncher.launch(new String[]{"application/zip"});
                    }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 【新增】每次回到主界面时，检查并刷新背景
        applyCustomBackground();

        // 动态控制"预算"菜单栏是否显示
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean isBudgetEnabled = getSafeBoolean(prefs, "is_budget_enabled", false);

            // 如果菜单里找到了 nav_budget，就根据开关状态决定它的隐藏/显示
            if (bottomNav.getMenu().findItem(R.id.nav_budget) != null) {
                bottomNav.getMenu().findItem(R.id.nav_budget).setVisible(isBudgetEnabled);
            }
        }
    }

    // ================== 新增：安全读取 SharedPreferences 的兼容方法 ==================
    private int getSafeInt(SharedPreferences prefs, String key, int defValue) {
        try {
            return prefs.getInt(key, defValue);
        } catch (ClassCastException e) {
            // 如果遇到了 String 类型的老数据，尝试强转并自动修复为 Int
            try {
                int val = Integer.parseInt(prefs.getString(key, String.valueOf(defValue)));
                prefs.edit().putInt(key, val).apply(); // 自动修复本地数据
                return val;
            } catch (Exception ex) {
                return defValue;
            }
        }
    }

    private boolean getSafeBoolean(SharedPreferences prefs, String key, boolean defValue) {
        try {
            return prefs.getBoolean(key, defValue);
        } catch (ClassCastException e) {
            // 如果遇到了 String 类型的老数据，尝试强转并自动修复为 Boolean
            try {
                boolean val = Boolean.parseBoolean(prefs.getString(key, String.valueOf(defValue)));
                prefs.edit().putBoolean(key, val).apply(); // 自动修复本地数据
                return val;
            } catch (Exception ex) {
                return defValue;
            }
        }
    }
    // ==============================================================================

}