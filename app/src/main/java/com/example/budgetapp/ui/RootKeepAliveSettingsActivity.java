package com.example.budgetapp.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.util.RootKeepAliveManager;

public class RootKeepAliveSettingsActivity extends AppCompatActivity {
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_root_keep_alive_settings);
        View root = findViewById(R.id.root_layout);
        final int top = root.getPaddingTop();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), top + bars.top, view.getPaddingRight(), bottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        SwitchCompat enabled = findViewById(R.id.switch_root_keep_alive);
        enabled.setChecked(RootKeepAliveManager.isEnabled(this));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            RootKeepAliveManager.setEnabled(this, checked);
            if (checked) RootKeepAliveManager.applyAsync(this);
            updateStatus();
        });
        SwitchCompat autoAccessibility = findViewById(R.id.switch_root_auto_accessibility);
        autoAccessibility.setChecked(RootKeepAliveManager.isAutoAccessibilityEnabled(this));
        autoAccessibility.setOnCheckedChangeListener((button, checked) -> {
            RootKeepAliveManager.setAutoAccessibilityEnabled(this, checked);
            RootKeepAliveManager.applyAsync(this, this::updateStatus);
        });
        SwitchCompat autoPermissions = findViewById(R.id.switch_root_auto_permissions);
        autoPermissions.setChecked(RootKeepAliveManager.isAutoPermissionsEnabled(this));
        autoPermissions.setOnCheckedChangeListener((button, checked) -> {
            RootKeepAliveManager.setAutoPermissionsEnabled(this, checked);
            RootKeepAliveManager.applyAsync(this, this::updateStatus);
        });
        SwitchCompat autoBoot = findViewById(R.id.switch_root_auto_boot);
        autoBoot.setChecked(RootKeepAliveManager.isAutoBootEnabled(this));
        autoBoot.setOnCheckedChangeListener((button, checked) -> {
            RootKeepAliveManager.setAutoBootEnabled(this, checked);
            if (checked) RootKeepAliveManager.applyAsync(this);
        });
        SwitchCompat startupNotification = findViewById(R.id.switch_root_startup_notification);
        startupNotification.setChecked(RootKeepAliveManager.isStartupNotificationEnabled(this));
        startupNotification.setOnCheckedChangeListener((button, checked) ->
                RootKeepAliveManager.setStartupNotificationEnabled(this, checked));
        status = findViewById(R.id.tv_root_status);
        updateStatus();
    }

    private void updateStatus() {
        status.setText(RootKeepAliveManager.isEnabled(this) ? "正在检查 root 权限…" : "已关闭");
        if (!RootKeepAliveManager.isEnabled(this)) return;
        new Thread(() -> {
            boolean available = RootKeepAliveManager.isRootAvailable();
            runOnUiThread(() -> status.setText(getStatusText(available)));
        }, "root-status-check").start();
    }

    private String getStatusText(boolean rootAvailable) {
        if (!rootAvailable) return "未检测到 root，功能将自动跳过";
        switch (RootKeepAliveManager.getLastStatus(this)) {
            case RootKeepAliveManager.STATUS_APPLIED:
                return "Root 策略已应用";
            case RootKeepAliveManager.STATUS_PARTIAL_FAILURE:
                return "部分策略不受当前系统支持";
            case RootKeepAliveManager.STATUS_ACCESSIBILITY_REVERTED:
                return "系统已撤销无障碍配置，请检查系统限制";
            case RootKeepAliveManager.STATUS_ROOT_UNAVAILABLE:
                return "Root 授权失败";
            default:
                return "已检测到 root，等待应用策略";
        }
    }
}
