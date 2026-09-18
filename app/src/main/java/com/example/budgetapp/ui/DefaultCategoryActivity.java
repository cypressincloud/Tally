package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.budgetapp.R;
import com.example.budgetapp.util.AutoCategoryRuleManager;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.util.KeywordManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultCategoryActivity extends AppCompatActivity {
    private final List<String> labels = new ArrayList<>();
    private final List<String> packages = new ArrayList<>();
    private LinearLayout container;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_auto_category_rule);
        ((TextView) findViewById(R.id.tv_page_title)).setText("应用默认分类");
        ((TextView) findViewById(R.id.tv_page_description)).setText("未命中自动分类规则时使用的软件默认分类");
        ((com.google.android.material.button.MaterialButton) findViewById(R.id.btn_add_rule)).setText("添加应用默认分类");
        container = findViewById(R.id.ll_rules);
        buildApps();
        findViewById(R.id.btn_add_rule).setOnClickListener(v -> showEditor(null));
        refresh();
    }

    private void buildApps() {
        labels.add("所有软件"); packages.add("");
        List<Map.Entry<String, String>> entries = new ArrayList<>(KeywordManager.getSupportedApps().entrySet());
        entries.sort(Map.Entry.comparingByValue());
        for (Map.Entry<String, String> entry : entries) { packages.add(entry.getKey()); labels.add(entry.getValue()); }
    }

    private void refresh() {
        container.removeAllViews();
        for (AutoCategoryRuleManager.DefaultCategory item : AutoCategoryRuleManager.getDefaults(this)) {
            View row = getLayoutInflater().inflate(R.layout.item_auto_category_rule, container, false);
            ((TextView) row.findViewById(R.id.tv_rule_title)).setText(appName(item.packageName) + " · " + (item.type == 0 ? "支出" : "收入"));
            ((TextView) row.findViewById(R.id.tv_rule_target)).setText(item.category + (item.subCategory.isEmpty() ? "" : " > " + item.subCategory));
            row.findViewById(R.id.btn_edit).setOnClickListener(v -> showEditor(item));
            row.findViewById(R.id.btn_delete).setOnClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("删除应用默认分类").setMessage("确定删除这条默认分类吗？")
                        .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
                            List<AutoCategoryRuleManager.DefaultCategory> all = AutoCategoryRuleManager.getDefaults(this);
                            all.remove(item); AutoCategoryRuleManager.saveDefaults(this, all); refresh();
                        }).show();
            });
            container.addView(row);
        }
    }

    private void showEditor(AutoCategoryRuleManager.DefaultCategory existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_auto_category_rule, null);
        ((TextView) view.findViewById(R.id.tv_dialog_title)).setText(existing == null ? "添加应用默认分类" : "编辑应用默认分类");
        Spinner app = view.findViewById(R.id.spinner_app), type = view.findViewById(R.id.spinner_target_type), category = view.findViewById(R.id.spinner_category), sub = view.findViewById(R.id.spinner_sub_category);
        view.findViewById(R.id.et_expression).setVisibility(View.GONE);
        view.findViewById(R.id.spinner_match).setVisibility(View.GONE);
        view.findViewById(R.id.spinner_source_type).setVisibility(View.GONE);
        app.setAdapter(adapter(labels)); type.setAdapter(adapter(java.util.Arrays.asList("支出", "收入")));
        Runnable update = () -> {
            List<String> cats = type.getSelectedItemPosition() == 1 ? CategoryManager.getIncomeCategories(this) : CategoryManager.getExpenseCategories(this);
            category.setAdapter(adapter(cats));
            if (existing != null) category.setSelection(Math.max(0, cats.indexOf(existing.category)));
            List<String> subs = new ArrayList<>(); subs.add("不设置"); if (!cats.isEmpty()) subs.addAll(CategoryManager.getSubCategories(this, String.valueOf(category.getSelectedItem())));
            sub.setAdapter(adapter(subs)); if (existing != null) sub.setSelection(Math.max(0, subs.indexOf(existing.subCategory)));
        };
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() { public void onNothingSelected(android.widget.AdapterView<?> p) {} public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { update.run(); } });
        if (existing != null) { app.setSelection(Math.max(0, packages.indexOf(existing.packageName))); type.setSelection(existing.type); }
        update.run();
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        dialog.setOnShowListener(x -> { if (dialog.getWindow() != null) { dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); dialog.getWindow().setGravity(android.view.Gravity.BOTTOM); dialog.getWindow().setLayout(-1, -2); }
            view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
            view.findViewById(R.id.btn_save).setOnClickListener(v -> { List<AutoCategoryRuleManager.DefaultCategory> all = AutoCategoryRuleManager.getDefaults(this); if (existing != null) all.remove(existing); String subValue = "不设置".equals(sub.getSelectedItem()) ? "" : String.valueOf(sub.getSelectedItem()); all.add(new AutoCategoryRuleManager.DefaultCategory(packages.get(app.getSelectedItemPosition()), type.getSelectedItemPosition(), String.valueOf(category.getSelectedItem()), subValue)); AutoCategoryRuleManager.saveDefaults(this, all); dialog.dismiss(); refresh(); }); });
        dialog.show(); if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -2);
    }

    private ArrayAdapter<String> adapter(List<String> values) { return new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values); }
    private String appName(String pkg) { int index = packages.indexOf(pkg); return index < 0 ? "所有软件" : labels.get(index); }
}
