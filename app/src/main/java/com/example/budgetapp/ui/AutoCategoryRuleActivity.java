package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.util.AutoCategoryRule;
import com.example.budgetapp.util.AutoCategoryRuleManager;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.util.KeywordManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AutoCategoryRuleActivity extends AppCompatActivity {
    private LinearLayout ruleContainer;
    private List<AutoCategoryRule> rules;
    private final List<String> appLabels = new ArrayList<>();
    private final List<String> appPackages = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_auto_category_rule);
        View root = findViewById(R.id.root_layout);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top + 24, v.getPaddingRight(), bars.bottom + 24);
            return WindowInsetsCompat.CONSUMED;
        });
        ruleContainer = findViewById(R.id.ll_rules);
        findViewById(R.id.btn_add_rule).setOnClickListener(v -> showRuleDialog(-1));
        buildAppOptions();
        refreshRules();
    }

    private void buildAppOptions() {
        appLabels.add("所有软件");
        appPackages.add("");
        List<Map.Entry<String, String>> entries = new ArrayList<>(KeywordManager.getSupportedApps().entrySet());
        entries.sort(Map.Entry.comparingByValue());
        for (Map.Entry<String, String> entry : entries) {
            appPackages.add(entry.getKey());
            appLabels.add(entry.getValue());
        }
    }

    private void refreshRules() {
        rules = AutoCategoryRuleManager.getRules(this);
        ruleContainer.removeAllViews();
        if (rules.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有自动分类规则");
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setPadding(8, 32, 8, 32);
            ruleContainer.addView(empty);
            return;
        }
        for (int i = 0; i < rules.size(); i++) addRuleView(i, rules.get(i));
    }

    private void addRuleView(int index, AutoCategoryRule rule) {
        View row = getLayoutInflater().inflate(R.layout.item_auto_category_rule, ruleContainer, false);
        TextView title = row.findViewById(R.id.tv_rule_title);
        title.setText((index + 1) + ". " + appName(rule.getPackageName()) + "  ·  "
                + matchName(rule.getMatchType()) + "  ·  " + rule.getExpression());
        TextView target = row.findViewById(R.id.tv_rule_target);
        target.setText((rule.getTransactionType() == 0 ? "支出" : "收入") + " → "
                + rule.getCategory() + (rule.getSubCategory().isEmpty() ? "" : " > " + rule.getSubCategory()));
        row.findViewById(R.id.btn_edit).setOnClickListener(v -> showRuleDialog(index));
        row.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("删除自动分类规则")
                    .setMessage("确定删除这条规则吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> {
            AutoCategoryRuleManager.deleteRule(this, index);
            refreshRules();
                    }).show();
        });
        ruleContainer.addView(row);
    }

    private void showRuleDialog(int editIndex) {
        AutoCategoryRule existing = editIndex >= 0 ? rules.get(editIndex) : null;
        View box = getLayoutInflater().inflate(R.layout.dialog_auto_category_rule, null);
        TextView dialogTitle = box.findViewById(R.id.tv_dialog_title);
        dialogTitle.setText(existing == null ? "添加自动分类规则" : "编辑自动分类规则");
        EditText expression = box.findViewById(R.id.et_expression);
        Spinner app = box.findViewById(R.id.spinner_app);
        Spinner match = box.findViewById(R.id.spinner_match);
        Spinner sourceType = box.findViewById(R.id.spinner_source_type);
        Spinner targetType = box.findViewById(R.id.spinner_target_type);
        Spinner category = box.findViewById(R.id.spinner_category);
        Spinner sub = box.findViewById(R.id.spinner_sub_category);
        app.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, appLabels));
        match.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                java.util.Arrays.asList("包含", "相等", "正则表达式")));
        sourceType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                java.util.Arrays.asList("支出", "收入", "不限")));
        targetType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                java.util.Arrays.asList("支出", "收入")));

        if (existing != null) {
            expression.setText(existing.getExpression());
            app.setSelection(Math.max(0, appPackages.indexOf(existing.getPackageName())));
            match.setSelection(existing.getMatchType());
            sourceType.setSelection(existing.getSourceType() == 2 ? 2 : existing.getSourceType());
            targetType.setSelection(existing.getTargetType());
        }
        Runnable updateCategories = () -> {
            boolean income = targetType.getSelectedItemPosition() == 1;
            List<String> cats = income ? CategoryManager.getIncomeCategories(this) : CategoryManager.getExpenseCategories(this);
            category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
            int pos = existing == null ? 0 : cats.indexOf(existing.getCategory()); category.setSelection(Math.max(0, pos));
            updateSubcategories(sub, cats.isEmpty() ? "" : cats.get(category.getSelectedItemPosition()), existing == null ? "" : existing.getSubCategory());
        };
        targetType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) { updateCategories.run(); }
        });
        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) { updateSubcategories(sub, String.valueOf(category.getSelectedItem()), existing == null ? "" : existing.getSubCategory()); }
        });
        updateCategories.run();

        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        dialog.setOnShowListener(v -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setGravity(Gravity.BOTTOM);
                dialog.getWindow().setLayout(-1, -2);
            }
            box.findViewById(R.id.btn_cancel).setOnClickListener(x -> dialog.dismiss());
            box.findViewById(R.id.btn_save).setOnClickListener(x -> {
            String text = expression.getText().toString().trim();
            String validationError = AutoCategoryRuleManager.validateExpression(text, match.getSelectedItemPosition());
            if (validationError != null || category.getSelectedItem() == null) { expression.setError(validationError); return; }
            String subValue = "不设置".equals(sub.getSelectedItem()) ? "" : String.valueOf(sub.getSelectedItem());
            AutoCategoryRule rule = new AutoCategoryRule(appPackages.get(app.getSelectedItemPosition()), text,
                    match.getSelectedItemPosition(), sourceType.getSelectedItemPosition() == 2 ? 2 : sourceType.getSelectedItemPosition(),
                    targetType.getSelectedItemPosition(), String.valueOf(category.getSelectedItem()), subValue);
            if (existing == null) AutoCategoryRuleManager.addRule(this, rule); else AutoCategoryRuleManager.updateRule(this, editIndex, rule);
            dialog.dismiss(); refreshRules();
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -2);
    }
    private void updateSubcategories(Spinner spinner, String category, String selected) {
        List<String> values = new ArrayList<>(); values.add("不设置"); values.addAll(CategoryManager.getSubCategories(this, category));
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        int pos = values.indexOf(selected); spinner.setSelection(pos < 0 ? 0 : pos);
    }
    private String appName(String pkg) { int i = appPackages.indexOf(pkg); return i >= 0 ? appLabels.get(i) : "所有软件"; }
    private String matchName(int type) { return type == AutoCategoryRule.MATCH_EQUALS ? "相等" : type == AutoCategoryRule.MATCH_REGEX ? "正则" : "包含"; }
}
