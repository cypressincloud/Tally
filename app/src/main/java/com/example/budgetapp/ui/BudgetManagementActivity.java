package com.example.budgetapp.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import java.time.YearMonth;

public class BudgetManagementActivity extends AppCompatActivity {

    private SwitchCompat switchBudget;
    private View cardBudgetInput;
    private EditText etMonthlyBudget;
    private TextView tvDailyBudgetHint;
    private Button btnSaveBudget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 设置沉浸式状态栏和导航栏（小白条）
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_budget_management);

        // 2. 处理系统窗口插入 (WindowInsets)，防止内容被状态栏和小白条遮挡
        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            final int originalPaddingLeft = rootLayout.getPaddingLeft();
            final int originalPaddingTop = rootLayout.getPaddingTop();
            final int originalPaddingRight = rootLayout.getPaddingRight();
            final int originalPaddingBottom = rootLayout.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        originalPaddingLeft + insets.left,
                        originalPaddingTop + insets.top,
                        originalPaddingRight + insets.right,
                        originalPaddingBottom + insets.bottom
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        // 初始化视图
        switchBudget = findViewById(R.id.switch_budget);
        cardBudgetInput = findViewById(R.id.card_budget_input);
        etMonthlyBudget = findViewById(R.id.et_monthly_budget);
        tvDailyBudgetHint = findViewById(R.id.tv_daily_budget_hint);
        btnSaveBudget = findViewById(R.id.btn_save_budget);

        SharedPreferences prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isBudgetEnabled = prefs.getBoolean("is_budget_enabled", false);
        float monthlyBudget = prefs.getFloat("monthly_budget", 0f);

        switchBudget.setChecked(isBudgetEnabled);
        cardBudgetInput.setVisibility(isBudgetEnabled ? View.VISIBLE : View.GONE);

        if (monthlyBudget > 0) {
            etMonthlyBudget.setText(String.valueOf(monthlyBudget));
            updateDailyHint(monthlyBudget);
        }

        switchBudget.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cardBudgetInput.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            prefs.edit().putBoolean("is_budget_enabled", isChecked).apply();
        });

        etMonthlyBudget.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    float val = Float.parseFloat(s.toString());
                    updateDailyHint(val);
                } catch (NumberFormatException e) {
                    tvDailyBudgetHint.setText("当月平均每日预算: 0.00");
                }
            }
        });

        btnSaveBudget.setOnClickListener(v -> {
            String valStr = etMonthlyBudget.getText().toString();
            try {
                float val = Float.parseFloat(valStr);
                prefs.edit().putFloat("monthly_budget", val).apply();
                Toast.makeText(this, "预算设置已保存", Toast.LENGTH_SHORT).show();
                finish();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的金额", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDailyHint(float monthlyBudget) {
        // 以当月天数计算平均每日预算
        int days = YearMonth.now().lengthOfMonth();
        float daily = monthlyBudget / days;
        tvDailyBudgetHint.setText(String.format("当月平均每日预算: %.2f", daily));
    }
}