package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.util.CategoryManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class CategorySettingsActivity extends AppCompatActivity {

    private ChipGroup chipGroupExpense;
    private ChipGroup chipGroupIncome;
    private List<String> expenseList;
    private List<String> incomeList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. 设置全屏沉浸 (Edge-to-Edge)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT); // 小白条沉浸关键

        setContentView(R.layout.activity_category_settings);

        // 2. 处理系统栏边距 (状态栏 + 底部导航栏)
        View rootView = findViewById(R.id.root_view);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 给根布局添加 Padding，避免内容被遮挡
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        initViews();
        loadData();
    }

    private void initViews() {
        // 移除了返回按钮的监听逻辑
        chipGroupExpense = findViewById(R.id.chip_group_expense);
        chipGroupIncome = findViewById(R.id.chip_group_income);

        findViewById(R.id.btn_add_expense).setOnClickListener(v -> showAddDialog(true));
        findViewById(R.id.btn_add_income).setOnClickListener(v -> showAddDialog(false));
    }

    private void loadData() {
        expenseList = CategoryManager.getExpenseCategories(this);
        incomeList = CategoryManager.getIncomeCategories(this);
        refreshChips(chipGroupExpense, expenseList, true);
        refreshChips(chipGroupIncome, incomeList, false);
    }

    private void refreshChips(ChipGroup group, List<String> categories, boolean isExpense) {
        group.removeAllViews();
        for (String cat : categories) {
            Chip chip = new Chip(this);
            chip.setText(cat);
            chip.setCheckable(false);
            chip.setCloseIconVisible(false);
            
            // 【样式统一修改】
            // 使用与记账弹窗一致的背景色 (日间浅灰/夜间深灰)
            chip.setChipBackgroundColorResource(R.color.cat_unselected_bg);
            // 文字颜色 (日间深灰/夜间浅灰)
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            
            // 去除 Chip 默认的边框 (如果不需要描边)
            chip.setChipStrokeWidth(0); 
            
            if ("自定义".equals(cat)) {
                // 自定义分类稍微区分一下，或者保持一致也可，这里设为半透明度
                chip.setAlpha(0.6f);
            } else {
                chip.setOnClickListener(v -> showDeleteDialog(cat, isExpense));
            }
            group.addView(chip);
        }
    }

    private void showAddDialog(boolean isExpense) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setHint("输入分类名称");
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        input.setBackground(null); // 去除下划线，配合弹窗样式
        
        builder.setTitle(isExpense ? "添加支出分类" : "添加收入分类")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        List<String> list = isExpense ? expenseList : incomeList;
                        if (list.contains(text)) {
                            Toast.makeText(this, "分类已存在", Toast.LENGTH_SHORT).show();
                        } else {
                            int insertIdx = list.size() > 0 ? list.size() - 1 : 0;
                            if (list.contains("自定义")) {
                                list.add(list.indexOf("自定义"), text);
                            } else {
                                list.add(text);
                            }
                            saveAndRefresh(isExpense);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteDialog(String category, boolean isExpense) {
        new AlertDialog.Builder(this)
                .setTitle("删除分类")
                .setMessage("确定要删除 \"" + category + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    List<String> list = isExpense ? expenseList : incomeList;
                    list.remove(category);
                    saveAndRefresh(isExpense);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveAndRefresh(boolean isExpense) {
        if (isExpense) {
            CategoryManager.saveExpenseCategories(this, expenseList);
            refreshChips(chipGroupExpense, expenseList, true);
        } else {
            CategoryManager.saveIncomeCategories(this, incomeList);
            refreshChips(chipGroupIncome, incomeList, false);
        }
    }
}