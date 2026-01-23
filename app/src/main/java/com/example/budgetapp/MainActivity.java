package com.example.budgetapp;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
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
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.service.AutoTrackAccessibilityService;
import com.example.budgetapp.ui.SettingsActivity;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.viewmodel.FinanceViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

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
        // 【新增】在界面创建前应用主题设置
        // 确保应用启动时能根据用户之前的选择（日间/夜间/跟随系统）正确显示
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);

        super.onCreate(savedInstanceState);
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
        bottomNav.getMenu().findItem(R.id.nav_assets).setVisible(showAssets);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(bottomNav, navController);

            bottomNav.setOnItemSelectedListener(item -> {
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
                        boolean isMinimalist = prefs.getBoolean("minimalist_mode", false);
                        
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
            });
        }

        checkPermissions();
    }

    private void checkPermissions() {
        AssistantConfig config = new AssistantConfig(this);

        if (config.isEnabled() && !isAccessibilitySettingsOn()) {
            showPermissionDialog("开启屏幕同步助手",
                    "为了提取屏幕上的支付金额，请开启‘记账屏幕同步助手’。",
                    Settings.ACTION_ACCESSIBILITY_SETTINGS);
        }
        else if (config.isRefundEnabled() && !isNotificationListenerEnabled()) {
            showPermissionDialog("开启退款监听",
                    "为了监听微信/支付宝的退款通知，请授予‘通知使用权’。",
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        else if (config.isEnabled() && !Settings.canDrawOverlays(this)) {
            showPermissionDialog("开启悬浮窗权限",
                    "为了在记账时显示确认弹窗，请授予‘显示在其他应用上层’权限。",
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        }
    }

    private void showPermissionDialog(String title, String message, String action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("去开启", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(action);
                        if (Settings.ACTION_MANAGE_OVERLAY_PERMISSION.equals(action)) {
                            intent.setData(Uri.parse("package:" + getPackageName()));
                        }
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "无法打开设置页，请手动前往设置", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean isAccessibilitySettingsOn() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + AutoTrackAccessibilityService.class.getCanonicalName();
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
}