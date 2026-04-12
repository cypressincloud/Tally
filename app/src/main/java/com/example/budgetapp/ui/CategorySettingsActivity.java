package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.util.CategoryManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class CategorySettingsActivity extends AppCompatActivity {

    private ChipGroup chipGroupExpense;
    private ChipGroup chipGroupIncome;
    private SwitchCompat switchSubCategory;
    private List<String> expenseList;
    private List<String> incomeList;
    private SwitchCompat switchDetailedCategory; // 【新增】

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_category_settings);

        View rootView = findViewById(R.id.root_view);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        initViews();
        loadData();
    }

    private void initViews() {
        chipGroupExpense = findViewById(R.id.chip_group_expense);
        chipGroupIncome = findViewById(R.id.chip_group_income);
        switchSubCategory = findViewById(R.id.switch_sub_category);

        // 【新增】初始化详细分类开关
        switchDetailedCategory = findViewById(R.id.switch_detailed_category);

        findViewById(R.id.btn_add_expense).setOnClickListener(v -> showAddDialog(true));
        // 【新增】绑定排序按钮
        Button btnSortExpense = findViewById(R.id.btn_sort_expense);
        if (btnSortExpense != null) {
            btnSortExpense.setOnClickListener(v -> showReorderDialog(true));
        }

        // ==========================================
        // 【新增】绑定收入排序按钮
        Button btnSortIncome = findViewById(R.id.btn_sort_income);
        if (btnSortIncome != null) {
            // 传入 false 代表当前操作的是收入列表
            btnSortIncome.setOnClickListener(v -> showReorderDialog(false));
        }
        // ==========================================

        findViewById(R.id.btn_add_income).setOnClickListener(v -> showAddDialog(false));

        // 二级分类开关绑定（增加判空保护）
        if (switchSubCategory != null) {
            boolean isEnabled = CategoryManager.isSubCategoryEnabled(this);
            switchSubCategory.setChecked(isEnabled);
            switchSubCategory.setOnCheckedChangeListener((buttonView, isChecked) -> {
                CategoryManager.setSubCategoryEnabled(this, isChecked);
                loadData();
            });
        }

        // 【新增】详细分类开关绑定（增加判空保护，防止找不到布局时闪退）
        if (switchDetailedCategory != null) {
            boolean isDetailedEnabled = CategoryManager.isDetailedCategoryEnabled(this);
            switchDetailedCategory.setChecked(isDetailedEnabled);
            switchDetailedCategory.setOnCheckedChangeListener((buttonView, isChecked) -> {
                CategoryManager.setDetailedCategoryEnabled(this, isChecked);
            });
        } else {
            // 如果走到这里，说明 XML 布局文件没有生效（多半是因为深色模式加载了旧布局）
            android.util.Log.e("CategorySettings", "未找到 switch_detailed_category 控件，请检查布局文件！");
        }
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
                chip.setOnClickListener(v -> showEditDialog(cat, isExpense));
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

    // 编辑一级分类
    private void showEditDialog(String oldCategory, boolean isExpense) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_category, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        EditText etInput = view.findViewById(R.id.et_category_name);
        Button btnDelete = view.findViewById(R.id.btn_delete);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        tvTitle.setText(isExpense ? "编辑支出分类" : "编辑收入分类");
        etInput.setText(oldCategory);
        etInput.setSelection(oldCategory.length());
        etInput.requestFocus();

        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteDialog(oldCategory, isExpense);
        });

        btnConfirm.setOnClickListener(v -> {
            String newCategory = etInput.getText().toString().trim();
            if (!newCategory.isEmpty()) {
                if (newCategory.equals(oldCategory)) {
                    dialog.dismiss();
                    return;
                }

                List<String> list = isExpense ? expenseList : incomeList;
                if (list.contains(newCategory)) {
                    Toast.makeText(this, "该分类已存在", Toast.LENGTH_SHORT).show();
                } else {
                    int index = list.indexOf(oldCategory);
                    if (index != -1) {
                        list.set(index, newCategory);

                        // 1. 同步迁移二级分类配置
                        List<String> subCats = CategoryManager.getSubCategories(this, oldCategory);
                        if (!subCats.isEmpty()) {
                            CategoryManager.saveSubCategories(this, newCategory, subCats);
                        }
                        saveAndRefresh(isExpense);

                        // 2. 在后台线程同步更新数据库里的历史账单的一级分类名称
                        new Thread(() -> {
                            // 【修复报错】此处将 getInstance 改为了 getDatabase
                            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                            if (db != null && db.transactionDao() != null) {
                                db.transactionDao().updateCategoryName(oldCategory, newCategory);
                            }
                        }).start();

                        dialog.dismiss();
                    }
                }
            } else {
                Toast.makeText(this, "请输入分类名称", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    // 管理二级分类
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

        ChipGroup chipGroup = view.findViewById(R.id.cg_sub_categories);
        TextView tvEmpty = view.findViewById(R.id.tv_empty);
        EditText etInput = view.findViewById(R.id.et_new_sub_category);
        Button btnAdd = view.findViewById(R.id.btn_add);
        Button btnClose = view.findViewById(R.id.btn_close);

        List<String> subCats = CategoryManager.getSubCategories(this, parentCategory);

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

                    chip.setChipBackgroundColorResource(R.color.cat_unselected_bg);
                    chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    chip.setChipStrokeWidth(0);
                    chip.setCheckedIconVisible(false);

                    chip.setOnClickListener(v -> {
                        AlertDialog.Builder editBuilder = new AlertDialog.Builder(this);
                        View editView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_category, null);
                        editBuilder.setView(editView);
                        AlertDialog editDialog = editBuilder.create();
                        if (editDialog.getWindow() != null) {
                            editDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                        }

                        TextView tvEditTitle = editView.findViewById(R.id.tv_dialog_title);
                        EditText etEditInput = editView.findViewById(R.id.et_category_name);
                        Button btnEditDelete = editView.findViewById(R.id.btn_delete);
                        Button btnEditConfirm = editView.findViewById(R.id.btn_confirm);

                        tvEditTitle.setText("编辑二级分类");
                        etEditInput.setText(subCat);
                        etEditInput.setSelection(subCat.length());

                        btnEditDelete.setOnClickListener(v1 -> {
                            editDialog.dismiss();

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

                            btnCancel.setOnClickListener(v2 -> delDialog.dismiss());
                            btnConfirm.setOnClickListener(v2 -> {
                                subCats.remove(subCat);
                                CategoryManager.saveSubCategories(this, parentCategory, subCats);
                                chipGroup.removeView(chip);
                                if (subCats.isEmpty()) {
                                    tvEmpty.setVisibility(View.VISIBLE);
                                    chipGroup.setVisibility(View.GONE);
                                }
                                delDialog.dismiss();
                            });
                            delDialog.show();
                        });

                        btnEditConfirm.setOnClickListener(v1 -> {
                            String newSubCat = etEditInput.getText().toString().trim();
                            if (!newSubCat.isEmpty()) {
                                if (newSubCat.equals(subCat)) {
                                    editDialog.dismiss();
                                    return;
                                }
                                if (subCats.contains(newSubCat)) {
                                    Toast.makeText(this, "该分类已存在", Toast.LENGTH_SHORT).show();
                                } else {
                                    int index = subCats.indexOf(subCat);
                                    if (index != -1) {
                                        subCats.set(index, newSubCat);
                                        // 1. 保存预设配置
                                        CategoryManager.saveSubCategories(this, parentCategory, subCats);

                                        // 2. 后台同步更新历史账单的二级分类名称
                                        new Thread(() -> {
                                            // 【修复报错】此处将 getInstance 改为了 getDatabase
                                            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                                            if (db != null && db.transactionDao() != null) {
                                                db.transactionDao().updateSubCategoryName(parentCategory, subCat, newSubCat);
                                            }
                                        }).start();

                                        chip.setText(newSubCat);
                                        editDialog.dismiss();
                                    }
                                }
                            } else {
                                Toast.makeText(this, "请输入分类名称", Toast.LENGTH_SHORT).show();
                            }
                        });

                        editDialog.show();
                    });

                    chipGroup.addView(chip);
                }
            }
        };

        refreshList.run();

        btnAdd.setOnClickListener(v -> {
            String newCat = etInput.getText().toString().trim();
            if (!newCat.isEmpty()) {
                if (!subCats.contains(newCat)) {
                    subCats.add(newCat);
                    CategoryManager.saveSubCategories(this, parentCategory, subCats);
                    refreshList.run();
                    etInput.setText("");
                } else {
                    Toast.makeText(this, "该分类已存在", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showAddDialog(boolean isExpense) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        EditText etInput = view.findViewById(R.id.et_category_name);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        tvTitle.setText(isExpense ? "添加支出分类" : "添加收入分类");
        etInput.requestFocus();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                List<String> list = isExpense ? expenseList : incomeList;
                if (list.contains(text)) {
                    Toast.makeText(this, "该分类已存在", Toast.LENGTH_SHORT).show();
                } else {
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

    private void showDeleteDialog(String category, boolean isExpense) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

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
        List<String> list = isExpense ? expenseList : incomeList;

        // 核心逻辑：确保 "自定义" 始终排在列表的最末尾
        if (list.contains("自定义")) {
            list.remove("自定义");
            list.add("自定义"); // 先移除再添加，自动置于末尾
        } else {
            // 如果意外丢失，自动补全到末尾
            list.add("自定义");
        }

        if (isExpense) {
            CategoryManager.saveExpenseCategories(this, expenseList);
            refreshChips(chipGroupExpense, expenseList, true);
        } else {
            CategoryManager.saveIncomeCategories(this, incomeList);
            refreshChips(chipGroupIncome, incomeList, false);
        }
    }

    // ================== 以下为新增的排序功能相关方法 ==================

    // 弹出排序对话框（优化版 UI）
    private void showReorderDialog(boolean isExpense) {
        List<String> list = isExpense ? expenseList : incomeList;

        // 过滤掉“自定义”，保持它永远在最后
        List<String> sortableList = new ArrayList<>();
        for (String cat : list) {
            if (!"自定义".equals(cat)) {
                sortableList.add(cat);
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // 使用新建的统一风格 XML
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_sort_categories, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        // 设置窗口背景透明，才能透出 CardView 的圆角
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        tvTitle.setText(isExpense ? "排序支出分类" : "排序收入分类");

        android.widget.LinearLayout container = view.findViewById(R.id.ll_sort_container);

        // 渲染列表
        buildReorderList(container, sortableList, isExpense);

        // 绑定完成按钮
        Button btnConfirm = view.findViewById(R.id.btn_confirm);
        btnConfirm.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // 动态构建更美观的列表项
    // 动态构建包含已有圆角背景的列表项
    private void buildReorderList(android.widget.LinearLayout container, List<String> sortableList, boolean isExpense) {
        container.removeAllViews();

        int marginV = (int) (6 * getResources().getDisplayMetrics().density); // 项与项之间的间距
        int paddingContent = (int) (14 * getResources().getDisplayMetrics().density); // 稍微加大一点内边距让 16dp 圆角看起来更协调

        for (int i = 0; i < sortableList.size(); i++) {
            final int index = i;
            String cat = sortableList.get(i);

            // 每一个分类的外层容器
            android.widget.LinearLayout itemLayout = new android.widget.LinearLayout(this);
            android.widget.LinearLayout.LayoutParams itemParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            itemParams.setMargins(0, marginV, 0, marginV);
            itemLayout.setLayoutParams(itemParams);

            itemLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
            itemLayout.setPadding(paddingContent, paddingContent, paddingContent, paddingContent);

            // 【关键修改】复用项目原本就有的 bg_input_field 圆角背景！
            itemLayout.setBackgroundResource(R.drawable.bg_input_field);

            // 1. 分类名称
            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText(cat);
            tv.setTextSize(15);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            android.widget.LinearLayout.LayoutParams tvParams = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            itemLayout.addView(tv, tvParams);

            // 2. 上移操作
            android.widget.TextView btnUp = new android.widget.TextView(this);
            btnUp.setText("上移");
            btnUp.setTextSize(13);
            btnUp.setPadding(20, 10, 20, 10);
            if (index > 0) {
                btnUp.setTextColor(ContextCompat.getColor(this, R.color.app_yellow));
                btnUp.setOnClickListener(v -> {
                    java.util.Collections.swap(sortableList, index, index - 1);
                    updateOriginalListAndRefresh(isExpense, sortableList);
                    buildReorderList(container, sortableList, isExpense);
                });
            } else {
                btnUp.setTextColor(android.graphics.Color.parseColor("#CCCCCC"));
            }
            itemLayout.addView(btnUp);

            // 3. 下移操作
            android.widget.TextView btnDown = new android.widget.TextView(this);
            btnDown.setText("下移");
            btnDown.setTextSize(13);
            btnDown.setPadding(20, 10, 10, 10);
            if (index < sortableList.size() - 1) {
                btnDown.setTextColor(ContextCompat.getColor(this, R.color.app_yellow));
                btnDown.setOnClickListener(v -> {
                    java.util.Collections.swap(sortableList, index, index + 1);
                    updateOriginalListAndRefresh(isExpense, sortableList);
                    buildReorderList(container, sortableList, isExpense);
                });
            } else {
                btnDown.setTextColor(android.graphics.Color.parseColor("#CCCCCC"));
            }
            itemLayout.addView(btnDown);

            container.addView(itemLayout);
        }
    }
    // 同步更新主列表并保存
    private void updateOriginalListAndRefresh(boolean isExpense, List<String> sortedSubList) {
        List<String> targetList = isExpense ? expenseList : incomeList;
        targetList.clear();
        targetList.addAll(sortedSubList);

        // 调用我们之前修改过的 saveAndRefresh 方法
        // 它会自动发现“自定义”不见了，然后在最末尾安全地将“自定义”补齐，并保存到数据库和刷新主界面标签
        saveAndRefresh(isExpense);
    }

}