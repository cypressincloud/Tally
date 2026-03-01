package com.example.budgetapp.ui;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.MyApplication;
import com.example.budgetapp.R;

import java.util.concurrent.Executor;

public class AuthActivity extends AppCompatActivity {

    private EditText etPassword;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 【新增 1】允许内容延伸到系统栏
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_auth);

        // 【新增 2】处理内边距，防止密码输入框/按钮被状态栏或小白条遮挡
        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        etPassword = findViewById(R.id.et_password);
        Button btnUnlock = findViewById(R.id.btn_unlock);
//        ImageView ivBiometric = findViewById(R.id.iv_biometric);

        // 密码解锁按钮
        btnUnlock.setOnClickListener(v -> verifyPassword());
        View layoutBiometricContainer = findViewById(R.id.layout_biometric_container);
        View btnBiometric = findViewById(R.id.btn_biometric);

        // 检查是否开启了指纹
        boolean biometricEnabled = prefs.getBoolean("biometric_enabled", false);
        if (biometricEnabled) {
            layoutBiometricContainer.setVisibility(View.VISIBLE); // 显示整个区域（包含文字）
            btnBiometric.setOnClickListener(v -> showBiometricPrompt()); // 仅将点击事件绑定在圆形按钮上
            // 自动拉起指纹验证
            showBiometricPrompt();
        } else {
            layoutBiometricContainer.setVisibility(View.GONE);
        }
    }

    private void verifyPassword() {
        String input = etPassword.getText().toString();
        String correctPwd = prefs.getString("app_password", "");

        if (input.equals(correctPwd)) {
            unlockSuccess();
        } else {
            Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
            etPassword.setText(""); // 清空输入框
        }
    }

    private void showBiometricPrompt() {
        BiometricManager biometricManager = BiometricManager.from(this);
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                != BiometricManager.BIOMETRIC_SUCCESS) {
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                unlockSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "验证失败", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁应用")
                .setSubtitle("请验证指纹以进入应用")
                .setNegativeButtonText("使用密码解锁")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void unlockSuccess() {
        // 更新全局状态为已解锁
        MyApplication.isUnlocked = true;
        finish();
        overridePendingTransition(0, 0); // 取消关闭动画，体验更好
    }

    @Override
    public void onBackPressed() {
        // 拦截返回键，按返回键直接退到手机桌面，防止绕过密码页
        moveTaskToBack(true);
    }
}