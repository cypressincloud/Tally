package com.example.budgetapp.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.util.CategoryManager;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class BudgetManagementActivity extends AppCompatActivity {

    private SwitchCompat switchBudget, switchDetailedBudget;
    private View cardBudgetInput;
    private LinearLayout llNormalBudgetInput, llDetailedBudgetContainer;
    private EditText etMonthlyBudget;
    private TextView tvDailyBudgetHint;
    private Button btnSaveBudget;

    // 用于保存动态生成的详细分类输入框
    private List<CategoryInputHolder> categoryInputs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_budget_management);

        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            final int originalPaddingLeft = rootLayout.getPaddingLeft();
            final int originalPaddingTop = rootLayout.getPaddingTop();
            final int originalPaddingRight = rootLayout.getPaddingRight();
            final int originalPaddingBottom = rootLayout.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(originalPaddingLeft + insets.left, originalPaddingTop + insets.top,
                        originalPaddingRight + insets.right, originalPaddingBottom + insets.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        switchBudget = findViewById(R.id.switch_budget);
        switchDetailedBudget = findViewById(R.id.switch_detailed_budget);
        cardBudgetInput = findViewById(R.id.card_budget_input);
        llNormalBudgetInput = findViewById(R.id.ll_normal_budget_input);
        llDetailedBudgetContainer = findViewById(R.id.ll_detailed_budget_container);
        etMonthlyBudget = findViewById(R.id.et_monthly_budget);
        tvDailyBudgetHint = findViewById(R.id.tv_daily_budget_hint);
        btnSaveBudget = findViewById(R.id.btn_save_budget);

        SharedPreferences prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isBudgetEnabled = prefs.getBoolean("is_budget_enabled", false);
        boolean isDetailedEnabled = prefs.getBoolean("is_detailed_budget_enabled", false);
        float monthlyBudget = prefs.getFloat("monthly_budget", 0f);

        switchBudget.setChecked(isBudgetEnabled);
        switchDetailedBudget.setChecked(isDetailedEnabled);

        // 构建动态视图
        buildDetailedInputs(prefs);
        updateUI(isBudgetEnabled, isDetailedEnabled);

        if (monthlyBudget > 0) {
            etMonthlyBudget.setText(String.valueOf(monthlyBudget));
            updateDailyHint(monthlyBudget);
        }

        switchBudget.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("is_budget_enabled", isChecked).apply();
            updateUI(isChecked, switchDetailedBudget.isChecked());
        });

        switchDetailedBudget.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("is_detailed_budget_enabled", isChecked).apply();
            updateUI(switchBudget.isChecked(), isChecked);
            calculateDynamicTotal(); // 切换时立刻重算提示金额
        });

        etMonthlyBudget.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!switchDetailedBudget.isChecked()) {
                    try {
                        updateDailyHint(Float.parseFloat(s.toString()));
                    } catch (NumberFormatException e) {
                        tvDailyBudgetHint.setText("当月平均每日预算: 0.00");
                    }
                }
            }
        });

        btnSaveBudget.setOnClickListener(v -> saveSettings(prefs));
    }

    private void updateUI(boolean isBudgetEnabled, boolean isDetailedEnabled) {
        cardBudgetInput.setVisibility(isBudgetEnabled ? View.VISIBLE : View.GONE);
        llNormalBudgetInput.setVisibility(isDetailedEnabled ? View.GONE : View.VISIBLE);
        llDetailedBudgetContainer.setVisibility(isDetailedEnabled ? View.VISIBLE : View.GONE);
    }

    /**
     * 动态生成支出一级分类的预算输入框
     */
    private void buildDetailedInputs(SharedPreferences prefs) {
        List<String> expenseCategories = CategoryManager.getExpenseCategories(this);
        for (String cat : expenseCategories) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_budget_category_input, llDetailedBudgetContainer, false);
            TextView tvName = row.findViewById(R.id.tv_cat_name);
            EditText etAmount = row.findViewById(R.id.et_cat_budget);

            tvName.setText(cat);
            float catBudget = prefs.getFloat("budget_cat_" + cat, 0f);
            if (catBudget > 0) etAmount.setText(String.valueOf(catBudget));

            etAmount.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (switchDetailedBudget.isChecked()) calculateDynamicTotal();
                }
            });

            llDetailedBudgetContainer.addView(row);
            categoryInputs.add(new CategoryInputHolder(cat, etAmount));
        }
    }

    private void calculateDynamicTotal() {
        float total = 0;
        for (CategoryInputHolder holder : categoryInputs) {
            try {
                total += Float.parseFloat(holder.editText.getText().toString());
            } catch (Exception ignored) {}
        }
        updateDailyHint(total);
    }

    private void updateDailyHint(float monthlyBudget) {
        int days = YearMonth.now().lengthOfMonth();
        float daily = monthlyBudget / days;
        tvDailyBudgetHint.setText(String.format("当月平均每日预算: %.2f", daily));
    }

    private void saveSettings(SharedPreferences prefs) {
        float totalBudget = 0;
        if (switchDetailedBudget.isChecked()) {
            // 开启详细模式：遍历保存各个分类，并加总
            for (CategoryInputHolder holder : categoryInputs) {
                float val = 0;
                try {
                    val = Float.parseFloat(holder.editText.getText().toString());
                } catch (Exception ignored) {}
                prefs.edit().putFloat("budget_cat_" + holder.categoryName, val).apply();
                totalBudget += val;
            }
            prefs.edit().putFloat("monthly_budget", totalBudget).apply();
            Toast.makeText(this, "详细分类预算已保存\n月总额更新为: " + totalBudget, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            // 普通模式：直接保存总额
            try {
                totalBudget = Float.parseFloat(etMonthlyBudget.getText().toString());
                prefs.edit().putFloat("monthly_budget", totalBudget).apply();
                Toast.makeText(this, "全局预算设置已保存", Toast.LENGTH_SHORT).show();
                finish();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的金额", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class CategoryInputHolder {
        String categoryName;
        EditText editText;
        CategoryInputHolder(String name, EditText et) {
            this.categoryName = name;
            this.editText = et;
        }
    }
}