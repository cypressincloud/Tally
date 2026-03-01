package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.budgetapp.R;

import java.util.concurrent.Executor;

public class SecuritySettingsActivity extends AppCompatActivity {

    private TextView btnSetPassword;
    private LinearLayout layoutBiometric;
    private SwitchCompat switchBiometric;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ======== 新增：1. 开启沉浸式布局适配 ========
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // ==========================================

        setContentView(R.layout.activity_security_settings);

        // ======== 新增：2. 避让状态栏和底部导航栏(小白条) ========
        View rootView = findViewById(R.id.security_settings_root);
        final int originalPaddingLeft = rootView.getPaddingLeft();
        final int originalPaddingTop = rootView.getPaddingTop();
        final int originalPaddingRight = rootView.getPaddingRight();
        final int originalPaddingBottom = rootView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    originalPaddingLeft + insets.left,
                    originalPaddingTop + insets.top,
                    originalPaddingRight + insets.right,
                    originalPaddingBottom + insets.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });
        // ======================================================

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        btnSetPassword = findViewById(R.id.btn_set_password);
        layoutBiometric = findViewById(R.id.layout_biometric);
        switchBiometric = findViewById(R.id.switch_biometric);

        updateUI();

        // 设置密码按钮点击事件
        btnSetPassword.setOnClickListener(v -> showSetPasswordDialog());

        // 生物识别开关事件
        switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return; // 防止代码修改触发

            if (isChecked) {
                checkAndEnableBiometric();
            } else {
                prefs.edit().putBoolean("biometric_enabled", false).apply();
                Toast.makeText(this, "生物识别已关闭", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI() {
        String currentPassword = prefs.getString("app_password", "");
        boolean hasPassword = !currentPassword.isEmpty();
        boolean biometricEnabled = prefs.getBoolean("biometric_enabled", false);

        if (hasPassword) {
            btnSetPassword.setText("修改应用密码");
            layoutBiometric.setAlpha(1.0f);
            switchBiometric.setEnabled(true);
        } else {
            btnSetPassword.setText("设置应用密码");
            layoutBiometric.setAlpha(0.5f);
            switchBiometric.setEnabled(false);
            switchBiometric.setChecked(false);
            prefs.edit().putBoolean("biometric_enabled", false).apply();
        }

        switchBiometric.setChecked(biometricEnabled);
    }

    private void showSetPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_set_password, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        EditText etInput = view.findViewById(R.id.et_password_input);

        // 直接获取按钮
        View btnClear = view.findViewById(R.id.btn_clear_password);
        View btnConfirm = view.findViewById(R.id.btn_confirm);

        // 判断当前是否有密码，动态改变标题和按钮显示
        boolean hasPassword = !prefs.getString("app_password", "").isEmpty();
        tvTitle.setText(hasPassword ? "修改应用密码" : "设置应用密码");

        // 直接控制按钮本身的显隐即可
        btnClear.setVisibility(hasPassword ? View.VISIBLE : View.GONE);

        // 清除密码按钮点击事件
        btnClear.setOnClickListener(v -> {
            prefs.edit().remove("app_password").putBoolean("biometric_enabled", false).apply();
            Toast.makeText(this, "密码已清除，生物识别已自动关闭", Toast.LENGTH_SHORT).show();
            updateUI();
            dialog.dismiss();
        });

        // 确定保存按钮点击事件
        btnConfirm.setOnClickListener(v -> {
            String password = etInput.getText().toString();
            if (password.length() >= 4) {
                prefs.edit().putString("app_password", password).apply();
                com.example.budgetapp.MyApplication.isUnlocked = true;
                Toast.makeText(this, "密码设置成功", Toast.LENGTH_SHORT).show();
                updateUI();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "密码长度不能少于4位", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    // 验证并开启生物识别
    private void checkAndEnableBiometric() {
        BiometricManager biometricManager = BiometricManager.from(this);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                showBiometricPrompt();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                Toast.makeText(this, "设备不支持或未录入指纹识别", Toast.LENGTH_LONG).show();
                switchBiometric.setChecked(false);
                break;
        }
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(SecuritySettingsActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(), "验证错误: " + errString, Toast.LENGTH_SHORT).show();
                switchBiometric.setChecked(false);
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                prefs.edit().putBoolean("biometric_enabled", true).apply();
                Toast.makeText(getApplicationContext(), "指纹验证成功，已开启", Toast.LENGTH_SHORT).show();
                switchBiometric.setChecked(true);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "验证失败，请重试", Toast.LENGTH_SHORT).show();
                switchBiometric.setChecked(false);
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("验证指纹")
                .setSubtitle("请验证以开启生物识别登录功能")
                .setNegativeButtonText("取消")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}