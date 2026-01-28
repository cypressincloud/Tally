package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
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

    // ... (保留原有的成员变量和 onCreate 等方法)
    private ChipGroup chipGroupExpense;
    private ChipGroup chipGroupIncome;
    private SwitchCompat switchSubCategory;
    private List<String> expenseList;
    private List<String> incomeList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_category_settings);
        // ... (保留 WindowInsets 设置)
        View rootView = findViewById(R.id.root_view);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        initViews();
        loadData();
    }

    // ... (initViews, loadData, refreshChips, showAddDialog, showDeleteDialog 等保持不变)

    private void initViews() {
        chipGroupExpense = findViewById(R.id.chip_group_expense);
        chipGroupIncome = findViewById(R.id.chip_group_income);
        switchSubCategory = findViewById(R.id.switch_sub_category);

        findViewById(R.id.btn_add_expense).setOnClickListener(v -> showAddDialog(true));
        findViewById(R.id.btn_add_income).setOnClickListener(v -> showAddDialog(false));

        boolean isEnabled = CategoryManager.isSubCategoryEnabled(this);
        switchSubCategory.setChecked(isEnabled);
        switchSubCategory.setOnCheckedChangeListener((buttonView, isChecked) -> {
            CategoryManager.setSubCategoryEnabled(this, isChecked);
            loadData(); // 重新加载以绑定长按事件
        });
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
            chip.setChipBackgroundColorResource(R.color.cat_unselected_bg);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            chip.setChipStrokeWidth(0);

            if ("自定义".equals(cat)) {
                chip.setAlpha(0.6f);
            } else {
                chip.setOnClickListener(v -> showDeleteDialog(cat, isExpense));
                chip.setOnLongClickListener(v -> {
                    if (switchSubCategory.isChecked()) {
                        showSubCategoryManager(cat);
                        return true;
                    }
                    return false;
                });
            }
            group.addView(chip);
        }
    }

    // 【核心修改】showSubCategoryManager
    private void showSubCategoryManager(String parentCategory) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_sub_categories, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_title);
        tvTitle.setText(parentCategory + " - 二级分类");

        // 获取控件
        ChipGroup chipGroup = view.findViewById(R.id.cg_sub_categories);
        TextView tvEmpty = view.findViewById(R.id.tv_empty);
        EditText etInput = view.findViewById(R.id.et_new_sub_category);
        Button btnAdd = view.findViewById(R.id.btn_add);
        Button btnClose = view.findViewById(R.id.btn_close);

        List<String> subCats = CategoryManager.getSubCategories(this, parentCategory);

        // 定义刷新列表的逻辑
        Runnable refreshList = () -> {
            chipGroup.removeAllViews();
            if (subCats.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                chipGroup.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                chipGroup.setVisibility(View.VISIBLE);

                for (String subCat : subCats) {
                    Chip chip = new Chip(this);
                    chip.setText(subCat);
                    chip.setCheckable(false);
                    chip.setClickable(true);

                    // 样式统一：使用与一级分类相同的灰色胶囊样式
                    chip.setChipBackgroundColorResource(R.color.cat_unselected_bg);
                    chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    chip.setChipStrokeWidth(0);
                    chip.setCheckedIconVisible(false);
                    // 保持默认圆角，与主界面一致

                    // 点击删除
                    chip.setOnClickListener(v -> {
                        // 构建自定义删除确认弹窗
                        AlertDialog.Builder delBuilder = new AlertDialog.Builder(this);
                        View delView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
                        delBuilder.setView(delView);
                        AlertDialog delDialog = delBuilder.create();
                        if (delDialog.getWindow() != null) {
                            delDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                        }

                        TextView tvDelTitle = delView.findViewById(R.id.tv_dialog_title);
                        TextView tvDelMsg = delView.findViewById(R.id.tv_dialog_message);
                        Button btnCancel = delView.findViewById(R.id.btn_dialog_cancel);
                        Button btnConfirm = delView.findViewById(R.id.btn_dialog_confirm);

                        tvDelTitle.setText("删除二级分类");
                        tvDelMsg.setText("确定要删除 \"" + subCat + "\" 吗？");

                        btnCancel.setOnClickListener(v1 -> delDialog.dismiss());
                        btnConfirm.setOnClickListener(v1 -> {
                            subCats.remove(subCat);
                            CategoryManager.saveSubCategories(this, parentCategory, subCats);
                            // 递归调用自身刷新界面（这里是Runnable内的逻辑）
                            // 注意：在Lambda中调用自身需要一点技巧，或者直接提取为方法。
                            // 由于这里是界面刷新，简单的方式是直接重新执行这一段逻辑
                            // 但为了代码清晰，我们其实是在删除后手动更新UI
                            chipGroup.removeView(chip);
                            if (subCats.isEmpty()) {
                                tvEmpty.setVisibility(View.VISIBLE);
                                chipGroup.setVisibility(View.GONE);
                            }
                            delDialog.dismiss();
                        });

                        delDialog.show();
                    });

                    chipGroup.addView(chip);
                }
            }
        };

        // 初始加载
        refreshList.run();

        // 添加按钮逻辑
        btnAdd.setOnClickListener(v -> {
            String newCat = etInput.getText().toString().trim();
            if (!newCat.isEmpty()) {
                if (!subCats.contains(newCat)) {
                    subCats.add(newCat);
                    CategoryManager.saveSubCategories(this, parentCategory, subCats);
                    refreshList.run(); // 重新加载列表
                    etInput.setText("");
                } else {
                    Toast.makeText(this, "该分类已存在", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // 【修改】重写 showAddDialog 方法
    private void showAddDialog(boolean isExpense) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // 1. 加载新布局
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        // 2. 设置背景透明，这一步对于圆角效果至关重要
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 3. 获取控件
        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        EditText etInput = view.findViewById(R.id.et_category_name);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        // 4. 设置标题
        tvTitle.setText(isExpense ? "添加支出分类" : "添加收入分类");

        // 自动弹出键盘 (可选优化)
        etInput.requestFocus();

        // 5. 按钮事件
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                List<String> list = isExpense ? expenseList : incomeList;
                if (list.contains(text)) {
                    Toast.makeText(this, "该分类已存在", Toast.LENGTH_SHORT).show();
                } else {
                    // 插入逻辑：如果有"自定义"，插在它前面，否则插在最后
                    if (list.contains("自定义")) {
                        list.add(list.indexOf("自定义"), text);
                    } else {
                        list.add(text);
                    }
                    saveAndRefresh(isExpense);
                    dialog.dismiss();
                }
            } else {
                Toast.makeText(this, "请输入分类名称", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    // 替换原有的 showDeleteDialog 方法
    private void showDeleteDialog(String category, boolean isExpense) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        // 设置背景透明
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = view.findViewById(R.id.tv_dialog_message);

        tvTitle.setText("删除分类");
        tvMsg.setText("确定要删除 \"" + category + "\" 吗？");

        view.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
            List<String> list = isExpense ? expenseList : incomeList;
            list.remove(category);
            saveAndRefresh(isExpense);
            dialog.dismiss();
        });

        dialog.show();
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