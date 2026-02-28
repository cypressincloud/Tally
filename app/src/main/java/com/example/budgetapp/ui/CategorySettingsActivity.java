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

import java.util.List;

public class CategorySettingsActivity extends AppCompatActivity {

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

        findViewById(R.id.btn_add_expense).setOnClickListener(v -> showAddDialog(true));
        findViewById(R.id.btn_add_income).setOnClickListener(v -> showAddDialog(false));

        boolean isEnabled = CategoryManager.isSubCategoryEnabled(this);
        switchSubCategory.setChecked(isEnabled);
        switchSubCategory.setOnCheckedChangeListener((buttonView, isChecked) -> {
            CategoryManager.setSubCategoryEnabled(this, isChecked);
            loadData();
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
        if (isExpense) {
            CategoryManager.saveExpenseCategories(this, expenseList);
            refreshChips(chipGroupExpense, expenseList, true);
        } else {
            CategoryManager.saveIncomeCategories(this, incomeList);
            refreshChips(chipGroupIncome, incomeList, false);
        }
    }
}